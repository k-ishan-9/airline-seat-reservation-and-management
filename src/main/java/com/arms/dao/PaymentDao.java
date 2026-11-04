package com.arms.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class PaymentDao {

    public enum Type { CHARGE, REFUND }

    public enum Status { SUCCESS, DECLINED }

    public void insert(Connection c, long bookingId, Type type, String maskedMethod, BigDecimal amount,
                       Status status, String reference, LocalDateTime at) throws SQLException {
        String sql = "INSERT INTO payments (booking_id, type, method, amount, status, reference, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, bookingId);
            ps.setString(2, type.name());
            ps.setString(3, maskedMethod);
            ps.setBigDecimal(4, amount);
            ps.setString(5, status.name());
            ps.setString(6, reference);
            ps.setTimestamp(7, Timestamp.valueOf(at));
            ps.executeUpdate();
        }
    }

    /** The masked method of the successful charge, so a refund goes back the same way it came. */
    public String chargeMethod(Connection c, long bookingId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT method FROM payments WHERE booking_id = ? AND type = 'CHARGE' AND status = 'SUCCESS'")) {
            ps.setLong(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "ORIGINAL METHOD";
            }
        }
    }
}
