package com.arms.db;

import java.sql.Connection;
import java.sql.SQLException;

/** A unit of database work that runs on a connection the caller provides. */
@FunctionalInterface
public interface SqlWork<T> {
    T apply(Connection connection) throws SQLException;
}
