package com.arms;

import com.arms.config.AppConfig;
import com.arms.db.DataAccessException;
import com.arms.db.Database;
import com.arms.payment.SimulatedPaymentGateway;
import com.arms.service.AdminService;
import com.arms.service.AuthService;
import com.arms.service.BookingService;
import com.arms.service.DataSeeder;
import com.arms.service.FlightService;
import com.arms.ui.ConsoleApp;
import com.arms.ui.ConsoleIO;
import com.arms.util.PasswordHasher;

import java.time.Clock;
import java.time.Duration;

/** Composition root: builds every object once and wires them together (manual dependency injection). */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        AppConfig config = AppConfig.load();
        Database db = new Database(config);
        Clock clock = Clock.systemDefaultZone();

        try {
            db.migrate();
        } catch (DataAccessException e) {
            System.err.println("Could not connect to MySQL at " + config.dbUrl());
            System.err.println("Start the database with:  docker compose up -d mysql   (details: " + e.getMessage() + ")");
            System.exit(1);
        }

        AuthService auth = new AuthService(db, new PasswordHasher(config.hashIterations()), clock);
        auth.ensureAdmin(config.adminPassword().toCharArray());
        int created = new DataSeeder(db, clock).ensureUpcomingFlights(14);
        if (created > 0) System.out.println("Added " + created + " demo flights for the next 14 days.");

        FlightService flights = new FlightService(db, clock);
        BookingService bookings = new BookingService(db, new SimulatedPaymentGateway(), clock,
                Duration.ofMinutes(config.holdMinutes()));
        AdminService admin = new AdminService(db, clock);

        ConsoleIO io = new ConsoleIO(System.in, System.out, System.console());
        new ConsoleApp(io, clock, auth, flights, bookings, admin).run();
    }
}
