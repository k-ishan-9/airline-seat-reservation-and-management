package com.arms.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Management reports, computed in SQL with joins, GROUP BY and conditional aggregation. */
public class ReportDao {

    public record Occupancy(String flightNo, String origin, String destination, LocalDateTime departure,
                            int totalSeats, int bookedSeats, BigDecimal loadFactorPercent) {
    }

    public record RouteRevenue(String origin, String destination, int paidBookings,
                               BigDecimal charged, BigDecimal refunded, BigDecimal net) {
    }

    /** Load factor (share of seats sold) for upcoming flights, fullest first. */
    public List<Occupancy> occupancy(Connection c, LocalDateTime from, LocalDateTime to, int limit) throws SQLException {
        String sql = """
                SELECT f.flight_no, f.origin, f.destination, f.departure_time,
                       COUNT(s.seat_id) AS total_seats,
                       COUNT(bs.seat_id) AS booked_seats,
                       ROUND(100 * COUNT(bs.seat_id) / COUNT(s.seat_id), 1) AS load_factor
                FROM flights f
                JOIN seats s ON s.flight_id = f.flight_id
                LEFT JOIN booking_seats bs ON bs.seat_id = s.seat_id AND bs.active = 1
                WHERE f.status = 'SCHEDULED' AND f.departure_time BETWEEN ? AND ?
                GROUP BY f.flight_id
                ORDER BY load_factor DESC, f.departure_time
                LIMIT ?
                """;
        List<Occupancy> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(from));
            ps.setTimestamp(2, Timestamp.valueOf(to));
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Occupancy(rs.getString("flight_no"), rs.getString("origin"), rs.getString("destination"),
                            rs.getTimestamp("departure_time").toLocalDateTime(), rs.getInt("total_seats"),
                            rs.getInt("booked_seats"), rs.getBigDecimal("load_factor")));
                }
            }
        }
        return rows;
    }

    /** Money in and out per route, from the payments ledger (charges minus refunds). */
    public List<RouteRevenue> revenueByRoute(Connection c) throws SQLException {
        String sql = """
                SELECT f.origin, f.destination,
                       COUNT(DISTINCT CASE WHEN p.type = 'CHARGE' THEN p.booking_id END) AS paid_bookings,
                       SUM(CASE WHEN p.type = 'CHARGE' THEN p.amount ELSE 0 END) AS charged,
                       SUM(CASE WHEN p.type = 'REFUND' THEN p.amount ELSE 0 END) AS refunded,
                       SUM(CASE WHEN p.type = 'CHARGE' THEN p.amount ELSE -p.amount END) AS net
                FROM payments p
                JOIN bookings b ON b.booking_id = p.booking_id
                JOIN flights f ON f.flight_id = b.flight_id
                WHERE p.status = 'SUCCESS'
                GROUP BY f.origin, f.destination
                ORDER BY net DESC
                """;
        List<RouteRevenue> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new RouteRevenue(rs.getString("origin"), rs.getString("destination"), rs.getInt("paid_bookings"),
                        rs.getBigDecimal("charged"), rs.getBigDecimal("refunded"), rs.getBigDecimal("net")));
            }
        }
        return rows;
    }
}
