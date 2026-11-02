package com.arms.dao;

import com.arms.model.FareClass;
import com.arms.model.Seat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SeatDao {

    /** Every seat of a class on a flight, marked taken if an active booking row holds it. */
    public List<Seat> seatMap(Connection c, long flightId, FareClass fareClass) throws SQLException {
        String sql = """
                SELECT s.seat_id, s.seat_no, s.fare_class, bs.seat_id IS NOT NULL AS taken
                FROM seats s
                LEFT JOIN booking_seats bs ON bs.seat_id = s.seat_id AND bs.active = 1
                WHERE s.flight_id = ? AND s.fare_class = ?
                ORDER BY s.seat_id
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, flightId);
            ps.setString(2, fareClass.name());
            return mapSeats(ps);
        }
    }

    /**
     * Locks the chosen seats (SELECT ... FOR UPDATE) so no other transaction can book them until we
     * commit. Rows are locked in seat_id order, so two bookings of overlapping seats always take locks
     * in the same order, which avoids deadlocks.
     */
    public List<Seat> lockSeats(Connection c, long flightId, Collection<String> seatNos) throws SQLException {
        String placeholders = String.join(",", seatNos.stream().map(s -> "?").toList());
        String sql = "SELECT s.seat_id, s.seat_no, s.fare_class, "
                + "EXISTS (SELECT 1 FROM booking_seats bs WHERE bs.seat_id = s.seat_id AND bs.active = 1) AS taken "
                + "FROM seats s WHERE s.flight_id = ? AND s.seat_no IN (" + placeholders + ") "
                + "ORDER BY s.seat_id FOR UPDATE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, flightId);
            int i = 2;
            for (String seatNo : seatNos) ps.setString(i++, seatNo);
            return mapSeats(ps);
        }
    }

    /**
     * Auto-assign: lock the first N free seats of a class. SKIP LOCKED makes concurrent bookings
     * take different seats instead of queueing behind each other on the same rows.
     */
    public List<Seat> lockFreeSeats(Connection c, long flightId, FareClass fareClass, int count) throws SQLException {
        String sql = """
                SELECT s.seat_id, s.seat_no, s.fare_class, FALSE AS taken
                FROM seats s
                WHERE s.flight_id = ? AND s.fare_class = ?
                  AND NOT EXISTS (SELECT 1 FROM booking_seats bs WHERE bs.seat_id = s.seat_id AND bs.active = 1)
                ORDER BY s.seat_id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, flightId);
            ps.setString(2, fareClass.name());
            ps.setInt(3, count);
            return mapSeats(ps);
        }
    }

    private static List<Seat> mapSeats(PreparedStatement ps) throws SQLException {
        List<Seat> seats = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                seats.add(new Seat(rs.getLong("seat_id"), rs.getString("seat_no"),
                        FareClass.valueOf(rs.getString("fare_class")), rs.getBoolean("taken")));
            }
        }
        return seats;
    }
}
