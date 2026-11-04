package com.arms.db;

/** An unexpected database failure (connection lost, bad SQL). Not something the user caused. */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
