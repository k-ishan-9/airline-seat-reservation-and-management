package com.arms.dao;

import com.arms.model.FareClass;
import com.arms.model.FareOption;
import com.arms.model.Flight;
import com.arms.model.FlightOffer;
import com.arms.util.SeatLayout;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FlightDao {

    // Seats left per class = all seats minus seats with an active booking row (LEFT JOIN + GROUP BY)
    private static final String OFFERS_SQL = """
            SELECT f.flight_id, f.flight_no, f.origin, f.destination, f.departure_time, f.arrival_time, f.status,
                   fa.fare_class, fa.price,
                   COUNT(s.seat_id) - COUNT(bs.seat_id) AS seats_left
            FROM flights f
            JOIN fares fa ON fa.flight_id = f.flight_id
            JOIN seats s ON s.flight_id = fa.flight_id AND s.fare_class = fa.fare_class
            LEFT JOIN booking_seats bs ON bs.seat_id = s.seat_id AND bs.active = 1
            WHERE %s
            GROUP BY f.flight_id, fa.fare_class, fa.price
            ORDER BY %s, FIELD(fa.fare_class, 'ECONOMY', 'BUSINESS', 'FIRST')
            """;

    /** Scheduled flights on a route departing between from and to (and after now). */
    public List<FlightOffer> search(Connection c, String origin, String destination,
                                    LocalDateTime from, LocalDateTime to, LocalDateTime now) throws SQLException {
        String sql = OFFERS_SQL.formatted(
                "f.origin = ? AND f.destination = ? AND f.departure_time >= ? AND f.departure_time < ? "
                        + "AND f.departure_time > ? AND f.status = 'SCHEDULED'",
                "f.departure_time");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, origin);
            ps.setString(2, destination);
            ps.setTimestamp(3, Timestamp.valueOf(from));
            ps.setTimestamp(4, Timestamp.valueOf(to));
            ps.setTimestamp(5, Timestamp.valueOf(now));
            return toOffers(ps);
        }
    }

    /** Cheapest upcoming flights in a class within a budget, from any route (or from one origin). */
    public List<FlightOffer> withinBudget(Connection c, FareClass fareClass, BigDecimal maxPrice, String origin,
                                          LocalDateTime now, LocalDateTime until) throws SQLException {
        String where = "fa.fare_class = ? AND fa.price <= ? AND f.status = 'SCHEDULED' "
                + "AND f.departure_time > ? AND f.departure_time < ?" + (origin == null ? "" : " AND f.origin = ?");
        try (PreparedStatement ps = c.prepareStatement(OFFERS_SQL.formatted(where, "fa.price, f.departure_time"))) {
            ps.setString(1, fareClass.name());
            ps.setBigDecimal(2, maxPrice);
            ps.setTimestamp(3, Timestamp.valueOf(now));
            ps.setTimestamp(4, Timestamp.valueOf(until));
            if (origin != null) ps.setString(5, origin);
            return toOffers(ps);
        }
    }

    public Flight find(Connection c, long flightId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM flights WHERE flight_id = ?")) {
            ps.setLong(1, flightId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapFlight(rs) : null;
            }
        }
    }

    public Flight findByNumberAndDeparture(Connection c, String flightNo, LocalDateTime departure) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM flights WHERE flight_no = ? AND departure_time = ?")) {
            ps.setString(1, flightNo);
            ps.setTimestamp(2, Timestamp.valueOf(departure));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapFlight(rs) : null;
            }
        }
    }

    public BigDecimal price(Connection c, long flightId, FareClass fareClass) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT price FROM fares WHERE flight_id = ? AND fare_class = ?")) {
            ps.setLong(1, flightId);
            ps.setString(2, fareClass.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : null;
            }
        }
    }

    public boolean airportExists(Connection c, String code) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM airports WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Map<String, String> airports(Connection c) throws SQLException {
        Map<String, String> airports = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT code, city FROM airports ORDER BY city");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) airports.put(rs.getString("code"), rs.getString("city"));
        }
        return airports;
    }

    public void insertAirport(Connection c, String code, String city, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO airports (code, city, name) VALUES (?, ?, ?)")) {
            ps.setString(1, code);
            ps.setString(2, city);
            ps.setString(3, name);
            ps.executeUpdate();
        }
    }

    /** Inserts a flight with its three fares and all 144 seats, in the caller's transaction. */
    public long insertFlight(Connection c, String flightNo, String origin, String destination,
                             LocalDateTime departure, LocalDateTime arrival,
                             Map<FareClass, BigDecimal> prices) throws SQLException {
        long flightId;
        String sql = "INSERT INTO flights (flight_no, origin, destination, departure_time, arrival_time) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, flightNo);
            ps.setString(2, origin);
            ps.setString(3, destination);
            ps.setTimestamp(4, Timestamp.valueOf(departure));
            ps.setTimestamp(5, Timestamp.valueOf(arrival));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                flightId = keys.getLong(1);
            }
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO fares (flight_id, fare_class, price) VALUES (?, ?, ?)")) {
            for (Map.Entry<FareClass, BigDecimal> fare : prices.entrySet()) {
                ps.setLong(1, flightId);
                ps.setString(2, fare.getKey().name());
                ps.setBigDecimal(3, fare.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
        // Batch insert: 144 seats in one round trip instead of 144
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO seats (flight_id, seat_no, fare_class) VALUES (?, ?, ?)")) {
            for (SeatLayout.SeatSpec seat : SeatLayout.standard()) {
                ps.setLong(1, flightId);
                ps.setString(2, seat.seatNo());
                ps.setString(3, seat.fareClass().name());
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return flightId;
    }

    /** Marks a flight cancelled only if it is still scheduled. Returns false if it wasn't. */
    public boolean cancel(Connection c, long flightId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE flights SET status = 'CANCELLED' WHERE flight_id = ? AND status = 'SCHEDULED'")) {
            ps.setLong(1, flightId);
            return ps.executeUpdate() == 1;
        }
    }

    public int countFlights(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM flights"); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    static Flight mapFlight(ResultSet rs) throws SQLException {
        return new Flight(rs.getLong("flight_id"), rs.getString("flight_no"), rs.getString("origin"),
                rs.getString("destination"), rs.getTimestamp("departure_time").toLocalDateTime(),
                rs.getTimestamp("arrival_time").toLocalDateTime(), rs.getString("status"));
    }

    private static List<FlightOffer> toOffers(PreparedStatement ps) throws SQLException {
        Map<Long, Flight> flights = new LinkedHashMap<>();
        Map<Long, List<FareOption>> fares = new LinkedHashMap<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong("flight_id");
                flights.putIfAbsent(id, mapFlight(rs));
                fares.computeIfAbsent(id, k -> new ArrayList<>()).add(new FareOption(
                        FareClass.valueOf(rs.getString("fare_class")), rs.getBigDecimal("price"), rs.getInt("seats_left")));
            }
        }
        List<FlightOffer> offers = new ArrayList<>();
        flights.forEach((id, flight) -> offers.add(new FlightOffer(flight, fares.get(id))));
        return offers;
    }
}
