package com.arms.service;

import com.arms.dao.BookingDao;
import com.arms.dao.FlightDao;
import com.arms.dao.PaymentDao;
import com.arms.dao.ReportDao;
import com.arms.db.Database;
import com.arms.model.BookingStatus;
import com.arms.model.FareClass;
import com.arms.model.User;

import java.math.BigDecimal;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Operations only an admin may run. Every method checks the role itself, not just the menu. */
public class AdminService {

    public record FlightCancellation(int bookingsRefunded, BigDecimal totalRefunded) {
    }

    private final Database db;
    private final Clock clock;
    private final FlightDao flights = new FlightDao();
    private final BookingDao bookings = new BookingDao();
    private final PaymentDao payments = new PaymentDao();
    private final ReportDao reports = new ReportDao();

    public AdminService(Database db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public long addFlight(User admin, String flightNo, String origin, String destination,
                          LocalDateTime departure, LocalDateTime arrival, Map<FareClass, BigDecimal> prices) {
        requireAdmin(admin);
        String number = flightNo == null ? "" : flightNo.trim().toUpperCase();
        if (!number.matches("[A-Z0-9]{2}\\d{1,4}")) throw new ValidationException("Flight numbers look like AI202 or 6E512");
        String from = FlightService.normaliseAirport(origin);
        String to = FlightService.normaliseAirport(destination);
        if (from.equals(to)) throw new ValidationException("Origin and destination must be different");
        if (!departure.isAfter(LocalDateTime.now(clock))) throw new ValidationException("Departure must be in the future");
        if (!arrival.isAfter(departure)) throw new ValidationException("Arrival must be after departure");
        Map<FareClass, BigDecimal> fares = new EnumMap<>(FareClass.class);
        for (FareClass fareClass : FareClass.values()) {
            BigDecimal price = prices.get(fareClass);
            if (price == null || price.signum() <= 0) throw new ValidationException(fareClass.label() + " price must be above zero");
            fares.put(fareClass, price);
        }

        return db.inTransaction(c -> {
            if (!flights.airportExists(c, from) || !flights.airportExists(c, to)) {
                throw new ValidationException("Unknown airport code");
            }
            try {
                return flights.insertFlight(c, number, from, to, departure, arrival, fares);
            } catch (SQLIntegrityConstraintViolationException e) {
                throw new ValidationException("Flight " + number + " already departs at that time");
            }
        });
    }

    /**
     * Cancels a flight and refunds every passenger 100%, in ONE transaction: either the flight is
     * cancelled and everyone is refunded, or (on any error) nothing changes at all.
     */
    public FlightCancellation cancelFlight(User admin, long flightId) {
        requireAdmin(admin);
        return db.inTransaction(c -> {
            if (flights.find(c, flightId) == null) throw new NotFoundException("Flight not found");
            if (!flights.cancel(c, flightId)) throw new ValidationException("This flight is already cancelled");
            LocalDateTime now = LocalDateTime.now(clock);
            int refunded = 0;
            BigDecimal totalRefunded = BigDecimal.ZERO;
            for (BookingDao.BookingRef booking : bookings.lockActiveForFlight(c, flightId)) {
                if (booking.status() == BookingStatus.HELD) {
                    bookings.setStatus(c, booking.id(), BookingStatus.EXPIRED);
                } else {
                    BigDecimal amount = bookings.total(c, booking.id());
                    bookings.markCancelled(c, booking.id(), amount, now);
                    payments.insert(c, booking.id(), PaymentDao.Type.REFUND, payments.chargeMethod(c, booking.id()),
                            amount, PaymentDao.Status.SUCCESS, null, now);
                    refunded++;
                    totalRefunded = totalRefunded.add(amount);
                }
                bookings.releaseSeats(c, booking.id());
            }
            return new FlightCancellation(refunded, totalRefunded);
        });
    }

    public List<ReportDao.Occupancy> occupancyReport(User admin, int days) {
        requireAdmin(admin);
        LocalDateTime now = LocalDateTime.now(clock);
        return db.read(c -> reports.occupancy(c, now, now.plusDays(days), 15));
    }

    public List<ReportDao.RouteRevenue> revenueByRoute(User admin) {
        requireAdmin(admin);
        return db.read(reports::revenueByRoute);
    }

    private static void requireAdmin(User user) {
        if (user == null || !user.isAdmin()) throw new AuthException("Only administrators can do this");
    }
}
