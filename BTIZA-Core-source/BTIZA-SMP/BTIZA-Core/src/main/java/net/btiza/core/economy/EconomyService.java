package net.btiza.core.economy;

import net.btiza.core.config.CoreSettings;
import net.btiza.core.database.Database;
import net.btiza.core.database.SqlWork;
import net.btiza.core.economy.TransactionResult.Status;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The shared economy used by every BTIZA plugin.
 *
 * Every balance change runs on the database thread inside one transaction, so the balance check
 * and the write cannot interleave with another transaction. Futures complete on the main server
 * thread, so callers can touch Bukkit objects directly. The balance cache holds online players only.
 */
public final class EconomyService {

    public static final int MAX_TOP = 100;
    private static final int MAX_REASON_LENGTH = 200;

    public record BalanceEntry(UUID id, String name, long balance) {
    }

    private final Database database;
    private final Executor mainThread;
    private final Logger logger;
    private final Map<UUID, Long> cache = new ConcurrentHashMap<>();
    private volatile CoreSettings settings;

    public EconomyService(Database database, CoreSettings settings, Executor mainThread, Logger logger) {
        this.database = database;
        this.settings = settings;
        this.mainThread = mainThread;
        this.logger = logger;
    }

    public void updateSettings(CoreSettings updated) {
        this.settings = updated;
    }

    /** Balance of an online player from memory. Empty if the player is offline or not yet loaded. */
    public OptionalLong cachedBalance(UUID id) {
        Long value = cache.get(id);
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    /**
     * Current balance from the database. Completes with empty for unknown accounts.
     * Completes exceptionally when the database fails, so an error is never reported as a zero balance.
     */
    public CompletableFuture<OptionalLong> balance(UUID id) {
        return database.submit(c -> readBalance(c, id))
                .whenComplete((balance, error) -> logIfFailed("balance lookup", error))
                .thenApplyAsync(Function.identity(), mainThread);
    }

    /** Top accounts by balance, highest first. Completes exceptionally when the database fails. */
    public CompletableFuture<List<BalanceEntry>> top(int limit) {
        int size = Math.max(1, Math.min(MAX_TOP, limit));
        return database.submit(c -> queryTop(c, size))
                .whenComplete((rows, error) -> logIfFailed("leaderboard query", error))
                .thenApplyAsync(Function.identity(), mainThread);
    }

    public CompletableFuture<TransactionResult> deposit(UUID id, long amount, String reason) {
        CoreSettings s = settings;
        return mutate("deposit", c -> {
            Status invalid = checkAmount(amount, s);
            if (invalid != null) {
                return TransactionResult.fail(invalid);
            }
            ensureAccount(c, id, id.toString(), 0L);
            long next;
            try {
                next = Math.addExact(readBalance(c, id).orElse(0L), amount);
            } catch (ArithmeticException overflow) {
                return TransactionResult.fail(Status.LIMIT_EXCEEDED);
            }
            if (next > s.maxBalance()) {
                return TransactionResult.fail(Status.LIMIT_EXCEEDED);
            }
            writeBalance(c, id, next);
            audit(c, "DEPOSIT", null, id, amount, reason);
            return TransactionResult.ok(Map.of(id, next));
        });
    }

    public CompletableFuture<TransactionResult> withdraw(UUID id, long amount, String reason) {
        CoreSettings s = settings;
        return mutate("withdraw", c -> {
            Status invalid = checkAmount(amount, s);
            if (invalid != null) {
                return TransactionResult.fail(invalid);
            }
            OptionalLong current = readBalance(c, id);
            if (current.isEmpty() || current.getAsLong() < amount) {
                return TransactionResult.fail(Status.INSUFFICIENT_FUNDS);
            }
            long next = current.getAsLong() - amount;
            writeBalance(c, id, next);
            audit(c, "WITHDRAW", id, null, amount, reason);
            return TransactionResult.ok(Map.of(id, next));
        });
    }

    public CompletableFuture<TransactionResult> transfer(UUID from, UUID to, long amount, String reason) {
        CoreSettings s = settings;
        return mutate("transfer", c -> {
            if (from.equals(to)) {
                return TransactionResult.fail(Status.SELF_TRANSFER);
            }
            Status invalid = checkAmount(amount, s);
            if (invalid != null) {
                return TransactionResult.fail(invalid);
            }
            OptionalLong fromBalance = readBalance(c, from);
            if (fromBalance.isEmpty() || fromBalance.getAsLong() < amount) {
                return TransactionResult.fail(Status.INSUFFICIENT_FUNDS);
            }
            ensureAccount(c, to, to.toString(), 0L);
            long toCurrent = readBalance(c, to).orElse(0L);
            long toNext;
            try {
                toNext = Math.addExact(toCurrent, amount);
            } catch (ArithmeticException overflow) {
                return TransactionResult.fail(Status.LIMIT_EXCEEDED);
            }
            if (toNext > s.maxBalance()) {
                return TransactionResult.fail(Status.LIMIT_EXCEEDED);
            }
            long fromNext = fromBalance.getAsLong() - amount;
            writeBalance(c, from, fromNext);
            writeBalance(c, to, toNext);
            audit(c, "TRANSFER", from, to, amount, reason);
            return TransactionResult.ok(Map.of(from, fromNext, to, toNext));
        });
    }

    /** Admin override. Sets the balance to an exact value. */
    public CompletableFuture<TransactionResult> set(UUID id, long amount, String reason) {
        CoreSettings s = settings;
        return mutate("set", c -> {
            if (amount < 0) {
                return TransactionResult.fail(Status.INVALID_AMOUNT);
            }
            if (amount > s.maxBalance()) {
                return TransactionResult.fail(Status.LIMIT_EXCEEDED);
            }
            ensureAccount(c, id, id.toString(), 0L);
            writeBalance(c, id, amount);
            audit(c, "SET", null, id, amount, reason);
            return TransactionResult.ok(Map.of(id, amount));
        });
    }

    /** Creates the account on first join, refreshes the name, and loads the balance into the cache. */
    public CompletableFuture<Void> onJoin(UUID id, String name) {
        CoreSettings s = settings;
        String safeName = name == null ? id.toString() : name;
        return database.submit(c -> Database.transaction(c, cc -> {
                    ensureAccount(cc, id, safeName, s.startingBalance());
                    touch(cc, id, safeName);
                    return readBalance(cc, id).orElse(s.startingBalance());
                }))
                .thenAccept(balance -> cache.put(id, balance))
                .whenComplete((ignored, error) -> logIfFailed("player load", error))
                .thenApplyAsync(Function.identity(), mainThread);
    }

    /** Drops the cache entry immediately and records the last-seen time in the background. */
    public void onQuit(UUID id, String name) {
        cache.remove(id);
        String safeName = name == null ? id.toString() : name;
        database.submit(c -> {
            touch(c, id, safeName);
            return Boolean.TRUE;
        }).whenComplete((ignored, error) -> logIfFailed("quit update", error));
    }

    private CompletableFuture<TransactionResult> mutate(String action, SqlWork<TransactionResult> work) {
        return database.submit(c -> {
                    TransactionResult result = Database.transaction(c, work);
                    if (result.success()) {
                        // Only the database thread writes the cache, after the commit succeeded.
                        result.balances().forEach((id, balance) -> cache.computeIfPresent(id, (key, old) -> balance));
                    }
                    return result;
                })
                .exceptionally(error -> {
                    logError(action, error);
                    return TransactionResult.fail(Status.DATABASE_ERROR);
                })
                .thenApplyAsync(Function.identity(), mainThread);
    }

    private void logError(String action, Throwable error) {
        logger.log(Level.SEVERE, "Economy " + action + " failed", error);
    }

    private void logIfFailed(String action, Throwable error) {
        if (error != null) {
            logError(action, error);
        }
    }

    private static Status checkAmount(long amount, CoreSettings s) {
        if (amount <= 0) {
            return Status.INVALID_AMOUNT;
        }
        if (amount > s.maxTransaction()) {
            return Status.LIMIT_EXCEEDED;
        }
        return null;
    }

    private static OptionalLong readBalance(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT balance FROM btiza_players WHERE uuid = ?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? OptionalLong.of(rs.getLong(1)) : OptionalLong.empty();
            }
        }
    }

    private static void ensureAccount(Connection c, UUID id, String name, long initialBalance) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO btiza_players (uuid, name, balance, first_join, last_seen) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, id.toString());
            ps.setString(2, name);
            ps.setLong(3, initialBalance);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate();
        }
    }

    private static void writeBalance(Connection c, UUID id, long balance) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE btiza_players SET balance = ? WHERE uuid = ?")) {
            ps.setLong(1, balance);
            ps.setString(2, id.toString());
            ps.executeUpdate();
        }
    }

    private static void touch(Connection c, UUID id, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE btiza_players SET name = ?, last_seen = ? WHERE uuid = ?")) {
            ps.setString(1, name);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, id.toString());
            ps.executeUpdate();
        }
    }

    private static void audit(Connection c, String kind, UUID from, UUID to, long amount, String reason) throws SQLException {
        String safeReason = reason == null ? "" : reason.substring(0, Math.min(MAX_REASON_LENGTH, reason.length()));
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO btiza_transactions (kind, from_uuid, to_uuid, amount, reason, created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, kind);
            ps.setString(2, from == null ? null : from.toString());
            ps.setString(3, to == null ? null : to.toString());
            ps.setLong(4, amount);
            ps.setString(5, safeReason);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private static List<BalanceEntry> queryTop(Connection c, int limit) throws SQLException {
        List<BalanceEntry> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT uuid, name, balance FROM btiza_players ORDER BY balance DESC, name ASC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new BalanceEntry(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3)));
                }
            }
        }
        return out;
    }
}
