package com.arms.support;

import com.arms.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * A fresh, empty test database for each test class. It refuses to run unless the database
 * name ends in "_test", so the tests can never wipe real data.
 * Settings: ARMS_TEST_DB_URL, ARMS_TEST_DB_USER, ARMS_TEST_DB_PASSWORD.
 */
public final class TestDb {

    private static final List<String> TABLES =
            List.of("payments", "booking_seats", "bookings", "seats", "fares", "flights", "airports", "users");

    private TestDb() {
    }

    public static Database fresh() {
        String url = env("ARMS_TEST_DB_URL",
                "jdbc:mysql://localhost:3306/arms_test?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false");
        String afterHost = url.substring(url.indexOf("//") + 2);
        String dbName = afterHost.substring(afterHost.indexOf('/') + 1).split("\\?")[0];
        if (!dbName.endsWith("_test")) {
            throw new IllegalStateException("Refusing to run tests on database '" + dbName + "': the name must end in _test");
        }
        Database db = new Database(url, env("ARMS_TEST_DB_USER", "root"), env("ARMS_TEST_DB_PASSWORD", "root"));
        db.read(c -> {
            try (Statement s = c.createStatement()) {
                s.execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String table : TABLES) s.execute("DROP TABLE IF EXISTS " + table);
                s.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            return null;
        });
        db.migrate();
        return db;
    }

    /** Runs a COUNT(*) style query with one long parameter. */
    public static int count(Database db, String sql, Object... params) {
        return db.read(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    public static String string(Database db, String sql, Object... params) {
        return db.read(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        });
    }

    public static void execute(Database db, String sql) {
        db.read(c -> {
            try (Statement s = c.createStatement()) {
                s.execute(sql);
            }
            return null;
        });
    }

    public static Connection connect(Database db) throws SQLException {
        return db.connect();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value != null ? value : fallback;
    }
}
