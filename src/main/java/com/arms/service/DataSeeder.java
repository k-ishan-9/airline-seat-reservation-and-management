package com.arms.service;

import com.arms.dao.FlightDao;
import com.arms.db.Database;
import com.arms.model.FareClass;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Demo data: 6 Indian airports and daily flights on 8 routes for the next 14 days, dated from
 * TODAY, so the app always has bookable flights.
 */
public class DataSeeder {

    private record Route(String flightNo, String from, String to, LocalTime departs, int minutes, int economy) {
    }

    private static final List<String[]> AIRPORTS = List.of(
            new String[]{"DEL", "Delhi", "Indira Gandhi International"},
            new String[]{"BOM", "Mumbai", "Chhatrapati Shivaji Maharaj International"},
            new String[]{"BLR", "Bengaluru", "Kempegowda International"},
            new String[]{"HYD", "Hyderabad", "Rajiv Gandhi International"},
            new String[]{"MAA", "Chennai", "Chennai International"},
            new String[]{"CCU", "Kolkata", "Netaji Subhas Chandra Bose International"});

    private static final List<Route> ROUTES = List.of(
            new Route("AR101", "DEL", "BOM", LocalTime.of(6, 30), 130, 5200),
            new Route("AR102", "BOM", "DEL", LocalTime.of(18, 15), 135, 5400),
            new Route("AR201", "DEL", "BLR", LocalTime.of(8, 0), 165, 6100),
            new Route("AR202", "BLR", "DEL", LocalTime.of(20, 30), 170, 6300),
            new Route("AR301", "HYD", "BOM", LocalTime.of(9, 45), 90, 3900),
            new Route("AR302", "BOM", "HYD", LocalTime.of(14, 20), 85, 3700),
            new Route("AR401", "MAA", "CCU", LocalTime.of(11, 10), 140, 4800),
            new Route("AR402", "BLR", "HYD", LocalTime.of(7, 5), 75, 3200));

    private final Database db;
    private final Clock clock;

    public DataSeeder(Database db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    /**
     * Makes sure every demo route has a flight on each of the next {@code days} days.
     * Idempotent: existing flights are skipped, so running it on every start keeps the demo alive.
     * Returns the number of flights created.
     */
    public int ensureUpcomingFlights(int days) {
        FlightDao flights = new FlightDao();
        return db.inTransaction(c -> {
            for (String[] airport : AIRPORTS) flights.insertAirport(c, airport[0], airport[1], airport[2]);
            int created = 0;
            LocalDate today = LocalDate.now(clock);
            for (int day = 0; day < days; day++) {
                for (Route r : ROUTES) {
                    var departure = today.plusDays(day).atTime(r.departs());
                    if (flights.findByNumberAndDeparture(c, r.flightNo(), departure) != null) continue;
                    BigDecimal economy = BigDecimal.valueOf(r.economy() + (day % 3) * 250L); // a little price variety
                    flights.insertFlight(c, r.flightNo(), r.from(), r.to(), departure, departure.plusMinutes(r.minutes()),
                            Map.of(FareClass.ECONOMY, economy,
                                    FareClass.BUSINESS, economy.multiply(BigDecimal.valueOf(3)),
                                    FareClass.FIRST, economy.multiply(BigDecimal.valueOf(5))));
                    created++;
                }
            }
            return created;
        });
    }
}
