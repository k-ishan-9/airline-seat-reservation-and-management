package com.arms.support;

import com.arms.db.Database;
import com.arms.model.FareClass;
import com.arms.model.Flight;
import com.arms.model.User;
import com.arms.payment.PaymentGateway;
import com.arms.payment.PaymentMethod;
import com.arms.payment.SimulatedPaymentGateway;
import com.arms.service.AdminService;
import com.arms.service.AuthService;
import com.arms.service.BookingService;
import com.arms.service.DataSeeder;
import com.arms.service.FlightService;
import com.arms.util.PasswordHasher;
import org.junit.Before;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Base for tests that run against a real MySQL database. Every test starts from an empty schema,
 * with demo flights for 6 days and a clock fixed at 3 Nov 2026, 10:00.
 */
public abstract class IntegrationTest {

    protected static final LocalDateTime START = LocalDateTime.of(2026, 11, 3, 10, 0);
    protected static final PaymentMethod GOOD_CARD = new PaymentMethod.Card("4111 1111 1111 1111", "Test User");
    protected static final PaymentMethod DECLINED_CARD = new PaymentMethod.Card("4000 0000 0000 0002", "Test User");

    protected Database db;
    protected MutableClock clock;
    protected AuthService auth;
    protected FlightService flights;
    protected BookingService bookings;
    protected AdminService admin;

    @Before
    public void setUpDatabase() {
        db = TestDb.fresh();
        clock = new MutableClock(START, ZoneOffset.UTC);
        auth = new AuthService(db, new PasswordHasher(1_000), clock); // few iterations keep tests fast
        new DataSeeder(db, clock).ensureUpcomingFlights(6);
        flights = new FlightService(db, clock);
        bookings = bookingsWith(new SimulatedPaymentGateway());
        admin = new AdminService(db, clock);
    }

    protected BookingService bookingsWith(PaymentGateway gateway) {
        return new BookingService(db, gateway, clock, Duration.ofMinutes(10));
    }

    protected User customer(String username) {
        return auth.register(username, "Test " + username, "password1".toCharArray());
    }

    protected User adminUser() {
        auth.ensureAdmin("admin12345".toCharArray());
        return auth.login("admin", "admin12345".toCharArray());
    }

    /** A seeded flight by number, N days after the test's "today". */
    protected Flight flight(String flightNo, int daysFromToday) {
        LocalDate date = START.toLocalDate().plusDays(daysFromToday);
        long id = db.read(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT flight_id FROM flights WHERE flight_no = ? AND DATE(departure_time) = ?")) {
                ps.setString(1, flightNo);
                ps.setObject(2, date);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        });
        return flights.flight(id);
    }

    protected static BookingService.BookingRequest request(Flight flight, FareClass fareClass, List<String> names,
                                                           List<String> seats, PaymentMethod payment) {
        return new BookingService.BookingRequest(flight.id(), fareClass, names, seats, payment);
    }

    protected int activeHolders(Flight flight, String seatNo) {
        return TestDb.count(db, "SELECT COUNT(*) FROM booking_seats bs JOIN seats s ON s.seat_id = bs.seat_id "
                + "WHERE s.flight_id = ? AND s.seat_no = ? AND bs.active = 1", flight.id(), seatNo);
    }

    protected String bookingStatus(String pnr) {
        return TestDb.string(db, "SELECT status FROM bookings WHERE pnr = ?", pnr);
    }
}
