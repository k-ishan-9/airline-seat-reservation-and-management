package com.arms.service;

import com.arms.dao.BookingDao;
import com.arms.dao.FlightDao;
import com.arms.dao.SeatDao;
import com.arms.db.Database;
import com.arms.model.FareClass;
import com.arms.model.Flight;
import com.arms.model.FlightOffer;
import com.arms.model.Seat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Read-side features: airports, flight search, budget search and seat maps. */
public class FlightService {

    private final Database db;
    private final Clock clock;
    private final FlightDao flights = new FlightDao();
    private final SeatDao seats = new SeatDao();
    private final BookingDao bookings = new BookingDao();

    public FlightService(Database db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public Map<String, String> airports() {
        return db.read(flights::airports);
    }

    public List<FlightOffer> search(String origin, String destination, LocalDate date) {
        String from = normaliseAirport(origin);
        String to = normaliseAirport(destination);
        if (from.equals(to)) throw new ValidationException("Origin and destination must be different");
        if (date.isBefore(LocalDate.now(clock))) throw new ValidationException("That date is in the past");
        LocalDateTime now = LocalDateTime.now(clock);
        return db.inTransaction(c -> {
            if (!flights.airportExists(c, from) || !flights.airportExists(c, to)) {
                throw new ValidationException("Unknown airport code. Choose one from the list.");
            }
            bookings.releaseExpiredHolds(c, null, now); // so seat counts don't include abandoned holds
            return flights.search(c, from, to, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), now);
        });
    }

    /** Cheapest flights in the next 30 days at or under a price, optionally from one airport. */
    public List<FlightOffer> withinBudget(FareClass fareClass, BigDecimal maxPrice, String origin) {
        if (maxPrice == null || maxPrice.signum() <= 0) throw new ValidationException("Enter a budget above zero");
        String from = origin == null || origin.isBlank() ? null : normaliseAirport(origin);
        LocalDateTime now = LocalDateTime.now(clock);
        return db.read(c -> flights.withinBudget(c, fareClass, maxPrice, from, now, now.plusDays(30)));
    }

    public Flight flight(long flightId) {
        Flight flight = db.read(c -> flights.find(c, flightId));
        if (flight == null) throw new NotFoundException("Flight not found");
        return flight;
    }

    public List<Seat> seatMap(long flightId, FareClass fareClass) {
        return db.read(c -> seats.seatMap(c, flightId, fareClass));
    }

    static String normaliseAirport(String code) {
        String c = code == null ? "" : code.trim().toUpperCase();
        if (!c.matches("[A-Z]{3}")) throw new ValidationException("Airport codes are 3 letters, e.g. DEL");
        return c;
    }
}
