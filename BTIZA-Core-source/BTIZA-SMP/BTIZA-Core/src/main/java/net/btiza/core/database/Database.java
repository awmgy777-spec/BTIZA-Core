package net.btiza.core.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * SQLite database in WAL mode. Every statement runs on one dedicated thread,
 * which serializes writes and keeps the main server thread free of disk I/O.
 */
public final class Database implements AutoCloseable {

    private static final int SCHEMA_VERSION = 1;

    private static final String[] SCHEMA_V1 = {
        """
        CREATE TABLE IF NOT EXISTS btiza_players (
            uuid TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            balance INTEGER NOT NULL DEFAULT 0,
            first_join INTEGER NOT NULL,
            last_seen INTEGER NOT NULL
        )""",
        "CREATE INDEX IF NOT EXISTS idx_btiza_players_balance ON btiza_players (balance DESC)",
        """
        CREATE TABLE IF NOT EXISTS btiza_transactions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            kind TEXT NOT NULL,
            from_uuid TEXT,
            to_uuid TEXT,
            amount INTEGER NOT NULL,
            reason TEXT NOT NULL,
            created_at INTEGER NOT NULL
        )""",
        "CREATE INDEX IF NOT EXISTS idx_btiza_tx_from ON btiza_transactions (from_uuid)",
        "CREATE INDEX IF NOT EXISTS idx_btiza_tx_to ON btiza_transactions (to_uuid)"
    };

    private final File file;
    private final Logger logger;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "BTIZA-Database");
        thread.setDaemon(true);
        return thread;
    });
    private Connection connection;

    public Database(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    /** Opens the file, applies pragmas, and migrates the schema. Call once on the main thread. */
    public void open() throws SQLException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new SQLException("Cannot create folder " + parent.getAbsolutePath());
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver is not available", e);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=FULL"); // economy data: never lose a committed transaction
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA busy_timeout=5000");
        }
        migrate();
    }

    /** Runs work on the database thread. The returned future completes on that thread. */
    public <T> CompletableFuture<T> submit(SqlWork<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            writer.execute(() -> {
                try {
                    future.complete(work.apply(connection));
                } catch (Throwable error) {
                    future.completeExceptionally(error);
                }
            });
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    /** Runs work inside a transaction. Rolls back if the work throws. */
    public static <T> T transaction(Connection connection, SqlWork<T> work) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            T result = work.apply(connection);
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private void migrate() throws SQLException {
        int version;
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            rs.next();
            version = rs.getInt(1);
        }
        if (version > SCHEMA_VERSION) {
            throw new SQLException("Database schema v" + version + " is newer than this build supports (v" + SCHEMA_VERSION + ")");
        }
        if (version < 1) {
            transaction(connection, c -> {
                try (Statement st = c.createStatement()) {
                    for (String sql : SCHEMA_V1) {
                        st.execute(sql);
                    }
                    st.execute("PRAGMA user_version = 1");
                }
                return null;
            });
            logger.info("BTIZA database migrated to schema v1");
        }
    }

    @Override
    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Database queue did not finish within 10 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.warning("Failed to close database: " + e.getMessage());
        }
    }
}
