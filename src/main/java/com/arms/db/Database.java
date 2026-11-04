package com.arms.db;

import com.arms.config.AppConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Connections, transactions and schema setup in one place.
 *
 * <p>{@link #inTransaction} is the heart of data safety: every statement inside it commits
 * together or not at all, and a deadlock victim is retried automatically.
 */
public final class Database {

    private static final int MAX_ATTEMPTS = 5;

    private final String url;
    private final String user;
    private final String password;

    public Database(AppConfig config) {
        this(config.dbUrl(), config.dbUser(), config.dbPassword());
    }

    public Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** Runs work in a transaction at MySQL's default isolation level (REPEATABLE READ). */
    public <T> T inTransaction(SqlWork<T> work) {
        return inTransaction(Connection.TRANSACTION_REPEATABLE_READ, work);
    }

    /**
     * Runs work in one transaction: commit if it returns, roll back if it throws.
     * If MySQL picks this transaction as a deadlock victim (error 1213) or a lock wait
     * times out (1205), everything was rolled back, so it is safe to run it again.
     */
    public <T> T inTransaction(int isolationLevel, SqlWork<T> work) {
        for (int attempt = 1; ; attempt++) {
            try (Connection connection = connect()) {
                connection.setAutoCommit(false);
                connection.setTransactionIsolation(isolationLevel);
                try {
                    T result = work.apply(connection);
                    connection.commit();
                    return result;
                } catch (SQLException | RuntimeException e) {
                    connection.rollback();
                    if (isRetryable(e) && attempt < MAX_ATTEMPTS) {
                        backOff(attempt);
                        continue;
                    }
                    throw e;
                }
            } catch (SQLException e) {
                throw new DataAccessException("Database error: " + e.getMessage(), e);
            }
        }
    }

    /** Runs read-only work with auto-commit (no explicit transaction needed). */
    public <T> T read(SqlWork<T> work) {
        try (Connection connection = connect()) {
            return work.apply(connection);
        } catch (SQLException e) {
            throw new DataAccessException("Database error: " + e.getMessage(), e);
        }
    }

    /** Creates any missing tables from db/schema.sql. Safe to run on every start. */
    public void migrate() {
        String script;
        try (InputStream in = Database.class.getResourceAsStream("/db/schema.sql")) {
            if (in == null) throw new IllegalStateException("db/schema.sql not found on the classpath");
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read db/schema.sql", e);
        }
        read(connection -> {
            try (Statement statement = connection.createStatement()) {
                for (String sql : script.replaceAll("(?m)--.*$", "").split(";")) {
                    if (!sql.isBlank()) statement.execute(sql);
                }
            }
            return null;
        });
    }

    /** Waits a short, random, growing time so retrying transactions don't collide again in lockstep. */
    private static void backOff(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, 25) * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean isRetryable(Exception e) {
        return e instanceof SQLException sql
                && ("40001".equals(sql.getSQLState()) || sql.getErrorCode() == 1213 || sql.getErrorCode() == 1205);
    }
}
