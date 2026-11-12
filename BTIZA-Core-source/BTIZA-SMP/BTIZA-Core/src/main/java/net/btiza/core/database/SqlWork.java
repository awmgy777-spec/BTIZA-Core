package net.btiza.core.database;

import java.sql.Connection;
import java.sql.SQLException;

/** A unit of database work that runs on the single database thread. */
@FunctionalInterface
public interface SqlWork<T> {
    T apply(Connection connection) throws SQLException;
}
