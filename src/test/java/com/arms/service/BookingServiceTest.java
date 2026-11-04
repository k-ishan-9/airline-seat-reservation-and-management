package com.arms.service;

import com.arms.model.Booking;
import com.arms.model.CancellationResult;
import com.arms.model.FareClass;
import com.arms.model.Flight;
import com.arms.model.User;
import com.arms.payment.PaymentGateway;
import com.arms.payment.PaymentResult;
import com.arms.support.IntegrationTest;
import com.arms.support.TestDb;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BookingServiceTest extends IntegrationTest {

    @Test
    public void booksChosenSeatsAndRecordsThePayment() {
        User alice = customer("alice");
        Flight flight = flight("AR101", 2);
        Booking booking = bookings.book(alice, request(flight, FareClass.ECONOMY,
                List.of("Alice Rao", "Ravi Rao"), List.of("12A", "12B"), GOOD_CARD));

        assertEquals("CONFIRMED", booking.status().name());
        assertTrue(booking.pnr().matches("[A-Z2-9]{6}"));
        BigDecimal price = new BigDecimal("5700.00"); // day 2: 5200 + 2 x 250
        assertEquals(price.multiply(BigDecimal.valueOf(2)), booking.total());
        assertEquals(List.of("12A", "12B"), booking.passengers().stream().map(Booking.Passenger::seatNo).toList());
        assertEquals(1, activeHolders(flight, "12A"));
        assertEquals("CARD ****1111", TestDb.string(db,
                "SELECT method FROM payments WHERE booking_id = ? AND type = 'CHARGE' AND status = 'SUCCESS'", booking.id()));
    }

    @Test
    public void aTakenSeatCantBeBookedAgain() {
        Flight flight = flight("AR101", 2);
        bookings.book(customer("alice"), request(flight, FareClass.ECONOMY, List.of("Alice Rao"), List.of("12A"), GOOD_CARD));
        try {
            bookings.book(customer("bob"), request(flight, FareClass.ECONOMY, List.of("Bob Das"), List.of("12a"), GOOD_CARD));
            fail("seat 12A is already taken");
        } catch (SeatUnavailableException expected) {
            assertTrue(expected.getMessage().contains("12A"));
        }
        assertEquals(1, activeHolders(flight, "12A"));
    }

    @Test
    public void autoAssignTakesTheFirstFreeSeats() {
        Flight flight = flight("AR101", 2);
        bookings.book(customer("alice"), request(flight, FareClass.ECONOMY, List.of("Alice Rao"), List.of("10A"), GOOD_CARD));
        Booking auto = bookings.book(customer("bob"), request(flight, FareClass.ECONOMY,
                List.of("Bob Das", "Bina Das"), List.of(), GOOD_CARD));
        assertEquals(List.of("10B", "10C"), auto.passengers().stream().map(Booking.Passenger::seatNo).toList());
    }

    @Test
    public void rejectsInvalidRequests() {
        User alice = customer("alice");
        Flight flight = flight("AR101", 2);
        expect(ValidationException.class, () -> bookings.book(alice,
                request(flight, FareClass.ECONOMY, List.of("Alice Rao"), List.of("1A"), GOOD_CARD))); // a First seat
        expect(ValidationException.class, () -> bookings.book(alice,
                request(flight, FareClass.ECONOMY, List.of("Alice Rao"), List.of("12A", "12B"), GOOD_CARD))); // 2 seats, 1 name
        expect(ValidationException.class, () -> bookings.book(alice,
                request(flight, FareClass.ECONOMY, List.of("Alice Rao"), List.of("99A"), GOOD_CARD))); // no such seat
        expect(ValidationException.class, () -> bookings.book(alice,
                request(flight, FareClass.ECONOMY, List.of("A1!"), List.of(), GOOD_CARD))); // bad name
        assertEquals(0, TestDb.count(db, "SELECT COUNT(*) FROM bookings"));
    }

    @Test
    public void aDeclinedPaymentReleasesTheSeats() {
        Flight flight = flight("AR101", 2);
        expect(PaymentDeclinedException.class, () -> bookings.book(customer("alice"),
                request(flight, FareClass.ECONOMY, List.of("Alice Rao"), List.of("14C"), DECLINED_CARD)));
        assertEquals("PAYMENT_FAILED", TestDb.string(db, "SELECT status FROM bookings"));
        assertEquals(0, activeHolders(flight, "14C"));
        // The seat is immediately bookable by someone else
        bookings.book(customer("bob"), request(flight, FareClass.ECONOMY, List.of("Bob Das"), List.of("14C"), GOOD_CARD));
        assertEquals(1, activeHolders(flight, "14C"));
    }

    @Test
    public void twentySimultaneousBookingsOfOneSeatProduceExactlyOneWinner() throws Exception {
        User alice = customer("alice");
        Flight flight = flight("AR101", 2);
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger confirmed = new AtomicInteger();
        AtomicInteger seatTaken = new AtomicInteger();
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            int n = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await(); // all threads fire at the same moment
                    bookings.book(alice, request(flight, FareClass.ECONOMY, List.of("Passenger " + (char) ('A' + n)),
                            List.of("15C"), GOOD_CARD));
                    confirmed.incrementAndGet();
                } catch (SeatUnavailableException e) {
                    seatTaken.incrementAndGet();
                } catch (Throwable t) {
                    unexpected.add(t);
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));

        assertEquals("unexpected errors: " + unexpected, 0, unexpected.size());
        assertEquals(1, confirmed.get());
        assertEquals(threads - 1, seatTaken.get());
        assertEquals(1, activeHolders(flight, "15C"));
    }

    @Test
    public void autoAssignUnderLoadNeverOversellsACabin() throws Exception {
        Flight flight = flight("AR201", 2); // First class has 8 seats
        User alice = customer("alice");
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<String> seatsSold = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger soldOut = new AtomicInteger();
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            int n = i;
            pool.submit(() -> {
                try {
                    go.await();
                    Booking b = bookings.book(alice, request(flight, FareClass.FIRST,
                            List.of("Passenger " + (char) ('A' + n)), List.of(), GOOD_CARD));
                    seatsSold.add(b.passengers().get(0).seatNo());
                } catch (SeatUnavailableException e) {
                    soldOut.incrementAndGet();
                } catch (Throwable t) {
                    unexpected.add(t);
                }
            });
        }
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));

        assertEquals("unexpected errors: " + unexpected, 0, unexpected.size());
        assertEquals(8, seatsSold.size());
        assertEquals(8, Set.copyOf(seatsSold).size()); // no seat sold twice
        assertEquals(4, soldOut.get());
    }

    @Test
    public void theDatabaseItselfRefusesASecondActiveHolder() throws Exception {
        User alice = customer("alice");
        Flight flight = flight("AR101", 2);
        Booking booking = bookings.book(alice, request(flight, FareClass.ECONOMY, List.of("Alice Rao"), List.of("20A"), GOOD_CARD));
        long seatId = TestDb.count(db, "SELECT seat_id FROM seats WHERE flight_id = ? AND seat_no = '20A'", flight.id());

        // Bypass the service completely, as a buggy program or a manual SQL session would
        try (Connection c = db.connect()) {
            long otherBooking;
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO bookings (pnr, user_id, flight_id, status, "
                    + "hold_expires_at, booked_at) VALUES ('TEST22', ?, ?, 'HELD', ?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, alice.id());
                ps.setLong(2, flight.id());
                ps.setTimestamp(3, Timestamp.valueOf(START.plusMinutes(10)));
                ps.setTimestamp(4, Timestamp.valueOf(START));
                ps.executeUpdate();
                var keys = ps.getGeneratedKeys();
                keys.next();
                otherBooking = keys.getLong(1);
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO booking_seats (booking_id, seat_id, passenger_name, "
                    + "price, active) VALUES (?, ?, 'Intruder', 1.00, 1)")) {
                ps.setLong(1, otherBooking);
                ps.setLong(2, seatId);
                ps.executeUpdate();
                fail("the unique key must reject a second active holder of seat 20A");
            } catch (SQLIntegrityConstraintViolationException expected) {
                assertTrue(expected.getMessage().contains("uq_active_seat"));
            }
        }
        assertEquals("CONFIRMED", bookingStatus(booking.pnr()));
    }

    @Test
    public void paymentSlowerThanTheHoldIsRefundedAndTheSeatReleased() {
        PaymentGateway slow = (amount, method, ref) -> {
            clock.advance(Duration.ofMinutes(11)); // the customer took 11 minutes; holds last 10
            return new PaymentResult(true, "TXNSLOW", "Approved");
        };
        Flight flight = flight("AR101", 2);
        try {
            bookingsWith(slow).book(customer("alice"), request(flight, FareClass.ECONOMY, List.of("Alice Rao"), List.of("21A"), GOOD_CARD));
            fail("the hold should have expired");
        } catch (ValidationException expected) {
            assertTrue(expected.getMessage().contains("expired"));
        }
        assertEquals("EXPIRED", TestDb.string(db, "SELECT status FROM bookings"));
        assertEquals(1, TestDb.count(db, "SELECT COUNT(*) FROM payments WHERE type = 'REFUND' AND status = 'SUCCESS'"));
        assertEquals(0, activeHolders(flight, "21A"));
    }

    @Test
    public void anAbandonedHoldIsReclaimedByTheNextCustomer() {
        Flight flight = flight("AR101", 2);
        User bob = customer("bob");
        AtomicReference<Booking> bobsBooking = new AtomicReference<>();
        PaymentGateway aliceWandersOff = (amount, method, ref) -> {
            clock.advance(Duration.ofMinutes(11));
            // Meanwhile Bob wants the same seat: the expired hold is released and he gets it
            bobsBooking.set(bookings.book(bob, request(flight, FareClass.ECONOMY, List.of("Bob Das"), List.of("22A"), GOOD_CARD)));
            return new PaymentResult(true, "TXNLATE", "Approved");
        };
        expect(ValidationException.class, () -> bookingsWith(aliceWandersOff).book(customer("alice"),
                request(flight, FareClass.ECONOMY, List.of("Alice Rao"), List.of("22A"), GOOD_CARD)));

        assertEquals("CONFIRMED", bobsBooking.get().status().name());
        assertEquals(1, activeHolders(flight, "22A"));
    }

    @Test
    public void bookingClosesAnHourBeforeDeparture() {
        Flight flight = flight("AR401", 0); // departs 11:10 today
        User alice = customer("alice");
        clock.advance(Duration.ofMinutes(15)); // now 10:15: 55 minutes to go
        expect(ValidationException.class, () -> bookings.book(alice,
                request(flight, FareClass.ECONOMY, List.of("Alice Rao"), List.of(), GOOD_CARD)));
    }

    @Test
    public void refundsFollowThePolicy() {
        User alice = customer("alice");
        assertEquals(90, cancelOn(alice, flight("AR101", 4)).refundPercent()); // ~92 hours ahead
        assertEquals(50, cancelOn(alice, flight("AR101", 2)).refundPercent()); // ~44 hours ahead
        CancellationResult late = cancelOn(alice, flight("AR101", 1));         // ~20 hours ahead
        assertEquals(0, late.refundPercent());
        assertEquals(0, late.refund().signum());
    }

    @Test
    public void cancellationFreesTheSeatAndRecordsTheRefund() {
        User alice = customer("alice");
        Flight flight = flight("AR101", 4);
        Booking booking = bookings.book(alice, request(flight, FareClass.BUSINESS, List.of("Alice Rao"), List.of("3A"), GOOD_CARD));
        CancellationResult result = bookings.cancel(alice, booking.pnr().toLowerCase());

        assertEquals(booking.total(), result.paid());
        assertEquals(booking.total().multiply(new BigDecimal("0.90")).setScale(2), result.refund());
        assertEquals("CANCELLED", bookingStatus(booking.pnr()));
        assertEquals(0, activeHolders(flight, "3A"));
        assertEquals(result.refund(), new BigDecimal(TestDb.string(db,
                "SELECT amount FROM payments WHERE booking_id = ? AND type = 'REFUND'", booking.id())));
        expect(ValidationException.class, () -> bookings.cancel(alice, booking.pnr())); // not twice
    }

    @Test
    public void customersCantSeeOrCancelEachOthersBookings() {
        User alice = customer("alice");
        User bob = customer("bob");
        Booking booking = bookings.book(alice, request(flight("AR101", 4), FareClass.ECONOMY, List.of("Alice Rao"), List.of(), GOOD_CARD));

        expect(NotFoundException.class, () -> bookings.cancel(bob, booking.pnr()));
        assertEquals(0, bookings.myBookings(bob).size());
        assertEquals(List.of(booking.pnr()), bookings.myBookings(alice).stream().map(Booking::pnr).collect(Collectors.toList()));
    }

    private CancellationResult cancelOn(User user, Flight flight) {
        Booking booking = bookings.book(user, request(flight, FareClass.ECONOMY, List.of("Alice Rao"), List.of(), GOOD_CARD));
        return bookings.cancel(user, booking.pnr());
    }

    private static void expect(Class<? extends Exception> type, Runnable action) {
        try {
            action.run();
            fail("expected " + type.getSimpleName());
        } catch (Exception e) {
            if (!type.isInstance(e)) throw new AssertionError("expected " + type.getSimpleName() + " but got " + e, e);
        }
    }
}
