package com.arms.service;

import com.arms.dao.BookingDao;
import com.arms.dao.BookingDao.SeatAssignment;
import com.arms.dao.FlightDao;
import com.arms.dao.PaymentDao;
import com.arms.dao.SeatDao;
import com.arms.db.Database;
import com.arms.model.Booking;
import com.arms.model.BookingStatus;
import com.arms.model.CancellationResult;
import com.arms.model.FareClass;
import com.arms.model.Flight;
import com.arms.model.Seat;
import com.arms.model.User;
import com.arms.payment.PaymentGateway;
import com.arms.payment.PaymentMethod;
import com.arms.payment.PaymentResult;
import com.arms.util.PnrGenerator;
import com.arms.util.RefundPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Booking and cancellation. A booking runs in three steps so that no database lock is held
 * while the (slow, external) payment provider is called:
 *
 * <ol>
 *   <li><b>Hold</b> (transaction 1): lock the seats, check they're free, insert a HELD booking
 *       that expires in a few minutes.</li>
 *   <li><b>Pay</b> (no transaction): charge the payment method.</li>
 *   <li><b>Confirm or release</b> (transaction 2): CONFIRMED if paid, otherwise the seats are freed.</li>
 * </ol>
 *
 * Double booking is prevented twice: row locks (SELECT ... FOR UPDATE) make concurrent bookings
 * of the same seat wait their turn, and the uq_active_seat unique key makes it impossible in the
 * database even if the application had a bug.
 */
public class BookingService {

    public static final int MAX_PASSENGERS = 6;
    private static final Duration BOOKING_CLOSES_BEFORE_DEPARTURE = Duration.ofMinutes(60);

    private final Database db;
    private final PaymentGateway gateway;
    private final Clock clock;
    private final Duration holdTime;
    private final FlightDao flights = new FlightDao();
    private final SeatDao seats = new SeatDao();
    private final BookingDao bookings = new BookingDao();
    private final PaymentDao payments = new PaymentDao();

    public BookingService(Database db, PaymentGateway gateway, Clock clock, Duration holdTime) {
        this.db = db;
        this.gateway = gateway;
        this.clock = clock;
        this.holdTime = holdTime;
    }

    /** seatNos empty = auto-assign the first free seats in the class. */
    public record BookingRequest(long flightId, FareClass fareClass, List<String> passengerNames,
                                 List<String> seatNos, PaymentMethod payment) {
    }

    private record Hold(long bookingId, String pnr, BigDecimal total) {
    }

    private enum ConfirmOutcome { CONFIRMED, DECLINED, HOLD_EXPIRED }

    public Booking book(User user, BookingRequest request) {
        List<String> names = validatePassengers(request.passengerNames());
        List<String> chosenSeats = normaliseSeats(request.seatNos(), names.size());

        // 1. Hold the seats. READ COMMITTED: every statement sees the latest committed bookings.
        Hold hold = db.inTransaction(Connection.TRANSACTION_READ_COMMITTED,
                c -> holdSeats(c, user, request, names, chosenSeats));

        // 2. Pay, outside any transaction, so seat rows aren't locked while the provider responds.
        PaymentResult payment = gateway.charge(hold.total(), request.payment(), hold.pnr());

        // 3. Confirm or release.
        ConfirmOutcome outcome = db.inTransaction(c -> confirmOrRelease(c, hold, payment, request.payment()));
        return switch (outcome) {
            case CONFIRMED -> db.read(c -> bookings.find(c, hold.bookingId()));
            case DECLINED -> throw new PaymentDeclinedException(
                    "Payment declined: " + payment.message() + ". Your seats have been released.");
            case HOLD_EXPIRED -> throw new ValidationException(
                    "Payment took too long and the seat hold expired. The charge has been refunded.");
        };
    }

    private Hold holdSeats(Connection c, User user, BookingRequest request, List<String> names,
                           List<String> chosenSeats) throws SQLException {
        LocalDateTime now = LocalDateTime.now(clock);
        Flight flight = flights.find(c, request.flightId());
        if (flight == null) throw new NotFoundException("Flight not found");
        if (!flight.isScheduled()) throw new ValidationException("This flight has been cancelled");
        if (!now.plus(BOOKING_CLOSES_BEFORE_DEPARTURE).isBefore(flight.departure())) {
            throw new ValidationException("Booking closes 60 minutes before departure");
        }
        BigDecimal price = flights.price(c, flight.id(), request.fareClass());
        if (price == null) throw new ValidationException("No " + request.fareClass().label() + " class on this flight");

        bookings.releaseExpiredHolds(c, flight.id(), now);

        List<Seat> locked;
        if (chosenSeats.isEmpty()) {
            locked = seats.lockFreeSeats(c, flight.id(), request.fareClass(), names.size());
            if (locked.size() < names.size()) {
                throw new SeatUnavailableException("Only " + locked.size() + " "
                        + request.fareClass().label() + " seat(s) left on this flight");
            }
        } else {
            locked = seats.lockSeats(c, flight.id(), chosenSeats);
            Set<String> found = locked.stream().map(Seat::seatNo).collect(Collectors.toSet());
            for (String seatNo : chosenSeats) {
                if (!found.contains(seatNo)) throw new ValidationException("Seat " + seatNo + " doesn't exist on this aircraft");
            }
            for (Seat seat : locked) {
                if (seat.fareClass() != request.fareClass()) {
                    throw new ValidationException("Seat " + seat.seatNo() + " is in " + seat.fareClass().label()
                            + ", not " + request.fareClass().label());
                }
                if (seat.taken()) throw new SeatUnavailableException("Seat " + seat.seatNo() + " was just taken. Please pick another.");
            }
            // Keep the passenger order the customer typed
            List<Seat> lockedSeats = locked;
            locked = chosenSeats.stream()
                    .map(no -> lockedSeats.stream().filter(s -> s.seatNo().equals(no)).findFirst().orElseThrow())
                    .toList();
        }

        Hold inserted = insertWithUniquePnr(c, user.id(), flight.id(), now);
        List<SeatAssignment> assignments = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            assignments.add(new SeatAssignment(locked.get(i).id(), names.get(i), price));
        }
        try {
            bookings.insertSeats(c, inserted.bookingId(), assignments);
        } catch (SQLException e) {
            // The unique key on (seat_id, active) is the last line of defence against double booking
            if (isUniqueViolation(e)) throw new SeatUnavailableException("One of those seats was just taken. Please try again.");
            throw e;
        }
        BigDecimal total = price.multiply(BigDecimal.valueOf(names.size()));
        return new Hold(inserted.bookingId(), inserted.pnr(), total);
    }

    private ConfirmOutcome confirmOrRelease(Connection c, Hold hold, PaymentResult payment,
                                            PaymentMethod method) throws SQLException {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!payment.approved()) {
            bookings.setStatus(c, hold.bookingId(), BookingStatus.PAYMENT_FAILED);
            bookings.releaseSeats(c, hold.bookingId());
            payments.insert(c, hold.bookingId(), PaymentDao.Type.CHARGE, method.masked(), hold.total(),
                    PaymentDao.Status.DECLINED, null, now);
            return ConfirmOutcome.DECLINED;
        }
        payments.insert(c, hold.bookingId(), PaymentDao.Type.CHARGE, method.masked(), hold.total(),
                PaymentDao.Status.SUCCESS, payment.reference(), now);
        if (bookings.confirm(c, hold.bookingId(), now)) return ConfirmOutcome.CONFIRMED;

        // The hold expired while paying and its seats may already be someone else's: release and refund in full
        bookings.setStatus(c, hold.bookingId(), BookingStatus.EXPIRED);
        bookings.releaseSeats(c, hold.bookingId());
        payments.insert(c, hold.bookingId(), PaymentDao.Type.REFUND, method.masked(), hold.total(),
                PaymentDao.Status.SUCCESS, payment.reference(), now);
        return ConfirmOutcome.HOLD_EXPIRED;
    }

    /** Cancels a confirmed booking: refund by policy, seats released, refund recorded. All or nothing. */
    public CancellationResult cancel(User user, String pnr) {
        String ref = pnr == null ? "" : pnr.trim().toUpperCase(Locale.ROOT);
        return db.inTransaction(c -> {
            BookingDao.BookingRef booking = bookings.lockByPnr(c, ref);
            // Someone else's PNR gets the same answer as a made-up one, so PNRs can't be probed
            if (booking == null || (booking.userId() != user.id() && !user.isAdmin())) {
                throw new NotFoundException("No booking found with PNR " + ref);
            }
            if (booking.status() != BookingStatus.CONFIRMED) {
                throw new ValidationException("This booking is already " + booking.status().name().toLowerCase());
            }
            Flight flight = flights.find(c, booking.flightId());
            LocalDateTime now = LocalDateTime.now(clock);
            int percent = RefundPolicy.refundPercent(Duration.between(now, flight.departure()));
            BigDecimal paid = bookings.total(c, booking.id());
            BigDecimal refund = paid.multiply(BigDecimal.valueOf(percent))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            bookings.markCancelled(c, booking.id(), refund, now);
            bookings.releaseSeats(c, booking.id());
            if (refund.signum() > 0) {
                payments.insert(c, booking.id(), PaymentDao.Type.REFUND, payments.chargeMethod(c, booking.id()),
                        refund, PaymentDao.Status.SUCCESS, null, now);
            }
            return new CancellationResult(ref, paid, percent, refund);
        });
    }

    public List<Booking> myBookings(User user) {
        return db.read(c -> bookings.listForUser(c, user.id()));
    }

    // ---------- helpers ----------

    private Hold insertWithUniquePnr(Connection c, long userId, long flightId, LocalDateTime now) throws SQLException {
        for (int attempt = 1; ; attempt++) {
            String pnr = PnrGenerator.next();
            try {
                long id = bookings.insertHeld(c, pnr, userId, flightId, now.plus(holdTime), now);
                return new Hold(id, pnr, BigDecimal.ZERO);
            } catch (SQLException e) {
                // 31^6 (about 887 million) PNRs make a clash rare; if one happens, draw again
                if (!isUniqueViolation(e) || attempt == 5) throw e;
            }
        }
    }


    private static boolean isUniqueViolation(SQLException e) {
        return e.getErrorCode() == 1062 || (e.getSQLState() != null && e.getSQLState().startsWith("23"));
    }

    private static List<String> validatePassengers(List<String> passengerNames) {
        if (passengerNames == null || passengerNames.isEmpty()) throw new ValidationException("Add at least one passenger");
        if (passengerNames.size() > MAX_PASSENGERS) {
            throw new ValidationException("At most " + MAX_PASSENGERS + " passengers per booking");
        }
        List<String> names = new ArrayList<>();
        for (String raw : passengerNames) {
            String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
            if (!name.matches("[\\p{L} .'-]{2,80}")) {
                throw new ValidationException("Passenger names must be 2-80 letters (spaces, dots, hyphens and apostrophes allowed)");
            }
            names.add(name);
        }
        return names;
    }

    private static List<String> normaliseSeats(List<String> seatNos, int passengers) {
        if (seatNos == null || seatNos.isEmpty()) return List.of();
        Set<String> unique = new LinkedHashSet<>();
        for (String s : seatNos) {
            String seat = s.trim().toUpperCase(Locale.ROOT);
            if (!seat.matches("\\d{1,2}[A-F]")) throw new ValidationException("Seat numbers look like 12A");
            if (!unique.add(seat)) throw new ValidationException("Seat " + seat + " was chosen twice");
        }
        if (unique.size() != passengers) {
            throw new ValidationException("Choose exactly " + passengers + " seat(s), one per passenger");
        }
        return new ArrayList<>(unique);
    }
}
