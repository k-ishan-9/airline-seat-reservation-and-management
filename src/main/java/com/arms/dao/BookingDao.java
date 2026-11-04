package com.arms.dao;

import com.arms.model.Booking;
import com.arms.model.BookingStatus;
import com.arms.model.FareClass;
import com.arms.model.Flight;

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

public class BookingDao {

    /** One passenger's seat in a booking, with the fare they paid. */
    public record SeatAssignment(long seatId, String passengerName, BigDecimal price) {
    }

    /** The minimum the cancellation logic needs, read with a row lock. */
    public record BookingRef(long id, long userId, long flightId, BookingStatus status) {
    }

    private static final String DETAILS_SQL = """
            SELECT b.booking_id, b.pnr, b.user_id, b.status AS booking_status, b.booked_at, b.refund_amount,
                   f.flight_id, f.flight_no, f.origin, f.destination, f.departure_time, f.arrival_time,
                   f.status AS flight_status, s.seat_no, s.fare_class, bs.passenger_name, bs.price
            FROM bookings b
            JOIN flights f ON f.flight_id = b.flight_id
            JOIN booking_seats bs ON bs.booking_id = b.booking_id
            JOIN seats s ON s.seat_id = bs.seat_id
            WHERE %s
            ORDER BY b.booked_at DESC, b.booking_id DESC, s.seat_id
            """;

    public long insertHeld(Connection c, String pnr, long userId, long flightId,
                           LocalDateTime holdExpiresAt, LocalDateTime bookedAt) throws SQLException {
        String sql = "INSERT INTO bookings (pnr, user_id, flight_id, status, hold_expires_at, booked_at) "
                + "VALUES (?, ?, ?, 'HELD', ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, pnr);
            ps.setLong(2, userId);
            ps.setLong(3, flightId);
            ps.setTimestamp(4, Timestamp.valueOf(holdExpiresAt));
            ps.setTimestamp(5, Timestamp.valueOf(bookedAt));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** Batch insert of the passenger seats. A seat already held elsewhere violates uq_active_seat. */
    public void insertSeats(Connection c, long bookingId, List<SeatAssignment> seats) throws SQLException {
        String sql = "INSERT INTO booking_seats (booking_id, seat_id, passenger_name, price, active) VALUES (?, ?, ?, ?, 1)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (SeatAssignment seat : seats) {
                ps.setLong(1, bookingId);
                ps.setLong(2, seat.seatId());
                ps.setString(3, seat.passengerName());
                ps.setBigDecimal(4, seat.price());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * HELD -> CONFIRMED, only if the hold is still valid at {@code now}. Returns false if the hold
     * expired (or was already released), in which case the seats may belong to someone else now.
     */
    public boolean confirm(Connection c, long bookingId, LocalDateTime now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE bookings SET status = 'CONFIRMED', hold_expires_at = NULL "
                + "WHERE booking_id = ? AND status = 'HELD' AND hold_expires_at >= ?")) {
            ps.setLong(1, bookingId);
            ps.setTimestamp(2, Timestamp.valueOf(now));
            return ps.executeUpdate() == 1;
        }
    }

    public void setStatus(Connection c, long bookingId, BookingStatus status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE bookings SET status = ? WHERE booking_id = ?")) {
            ps.setString(1, status.name());
            ps.setLong(2, bookingId);
            ps.executeUpdate();
        }
    }

    /** Frees a booking's seats. The rows stay as history, with active = NULL. */
    public void releaseSeats(Connection c, long bookingId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE booking_seats SET active = NULL WHERE booking_id = ?")) {
            ps.setLong(1, bookingId);
            ps.executeUpdate();
        }
    }

    /**
     * Releases holds whose payment window has passed (lazy clean-up: runs before searches and bookings,
     * so no background job is needed). Pass flightId = null for all flights.
     *
     * <p>Step 1 is a plain SELECT, which takes no locks, so in the usual case (nothing expired) this
     * costs one indexed read. Only expired bookings are then updated, by primary key, so the clean-up
     * never scans and locks rows that concurrent bookings are inserting (that caused deadlocks).
     */
    public int releaseExpiredHolds(Connection c, Long flightId, LocalDateTime now) throws SQLException {
        String find = "SELECT booking_id FROM bookings WHERE status = 'HELD' AND hold_expires_at < ?"
                + (flightId == null ? "" : " AND flight_id = ?");
        List<Long> expired = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(find)) {
            ps.setTimestamp(1, Timestamp.valueOf(now));
            if (flightId != null) ps.setLong(2, flightId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) expired.add(rs.getLong(1));
            }
        }
        int released = 0;
        for (long bookingId : expired) {
            // Re-check the status in the UPDATE itself: another session may have released it already
            try (PreparedStatement ps = c.prepareStatement("UPDATE bookings SET status = 'EXPIRED' "
                    + "WHERE booking_id = ? AND status = 'HELD' AND hold_expires_at < ?")) {
                ps.setLong(1, bookingId);
                ps.setTimestamp(2, Timestamp.valueOf(now));
                if (ps.executeUpdate() == 0) continue;
            }
            releaseSeats(c, bookingId);
            released++;
        }
        return released;
    }

    /** Finds a booking by PNR and locks its row, so it can't be cancelled twice at the same moment. */
    public BookingRef lockByPnr(Connection c, String pnr) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT booking_id, user_id, flight_id, status FROM bookings WHERE pnr = ? FOR UPDATE")) {
            ps.setString(1, pnr);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new BookingRef(rs.getLong("booking_id"), rs.getLong("user_id"), rs.getLong("flight_id"),
                        BookingStatus.valueOf(rs.getString("status")));
            }
        }
    }

    /** Locks every booking on a flight that still holds seats (for a flight cancellation). */
    public List<BookingRef> lockActiveForFlight(Connection c, long flightId) throws SQLException {
        List<BookingRef> refs = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT booking_id, user_id, flight_id, status FROM bookings "
                + "WHERE flight_id = ? AND status IN ('HELD', 'CONFIRMED') ORDER BY booking_id FOR UPDATE")) {
            ps.setLong(1, flightId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    refs.add(new BookingRef(rs.getLong("booking_id"), rs.getLong("user_id"), rs.getLong("flight_id"),
                            BookingStatus.valueOf(rs.getString("status"))));
                }
            }
        }
        return refs;
    }

    public BigDecimal total(Connection c, long bookingId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(SUM(price), 0) FROM booking_seats WHERE booking_id = ?")) {
            ps.setLong(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    public void markCancelled(Connection c, long bookingId, BigDecimal refund, LocalDateTime when) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE bookings SET status = 'CANCELLED', cancelled_at = ?, refund_amount = ? WHERE booking_id = ?")) {
            ps.setTimestamp(1, Timestamp.valueOf(when));
            ps.setBigDecimal(2, refund);
            ps.setLong(3, bookingId);
            ps.executeUpdate();
        }
    }

    public Booking find(Connection c, long bookingId) throws SQLException {
        List<Booking> found = query(c, "b.booking_id = ?", bookingId);
        return found.isEmpty() ? null : found.get(0);
    }

    public List<Booking> listForUser(Connection c, long userId) throws SQLException {
        return query(c, "b.user_id = ? AND b.status IN ('CONFIRMED', 'CANCELLED')", userId);
    }

    private List<Booking> query(Connection c, String where, long id) throws SQLException {
        Map<Long, Booking> bookings = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(DETAILS_SQL.formatted(where))) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long bookingId = rs.getLong("booking_id");
                    Booking booking = bookings.get(bookingId);
                    if (booking == null) {
                        Flight flight = new Flight(rs.getLong("flight_id"), rs.getString("flight_no"),
                                rs.getString("origin"), rs.getString("destination"),
                                rs.getTimestamp("departure_time").toLocalDateTime(),
                                rs.getTimestamp("arrival_time").toLocalDateTime(), rs.getString("flight_status"));
                        booking = new Booking(bookingId, rs.getString("pnr"), rs.getLong("user_id"), flight,
                                FareClass.valueOf(rs.getString("fare_class")),
                                BookingStatus.valueOf(rs.getString("booking_status")), new ArrayList<>(),
                                BigDecimal.ZERO, rs.getBigDecimal("refund_amount"),
                                rs.getTimestamp("booked_at").toLocalDateTime());
                    }
                    booking.passengers().add(new Booking.Passenger(rs.getString("passenger_name"),
                            rs.getString("seat_no"), rs.getBigDecimal("price")));
                    // The total is derived from the seats: records are immutable, so rebuild with the new sum
                    BigDecimal total = booking.total().add(rs.getBigDecimal("price"));
                    bookings.put(bookingId, new Booking(booking.id(), booking.pnr(), booking.userId(), booking.flight(),
                            booking.fareClass(), booking.status(), booking.passengers(), total,
                            booking.refundAmount(), booking.bookedAt()));
                }
            }
        }
        return new ArrayList<>(bookings.values());
    }
}
