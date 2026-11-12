package net.btiza.core.config;

import net.btiza.core.util.NumberFormatter;

import java.util.UUID;
import java.util.logging.Logger;

/** Validated, immutable snapshot of BTIZA-Core settings. Contains no Bukkit types. */
public record CoreSettings(
        String currencySymbol,
        long startingBalance,
        long maxBalance,
        long maxTransaction,
        UUID ownerUuid,
        String ownerName,
        boolean worldGuardProtection,
        NumberFormatter formatter) {

    public static final long DEFAULT_MAX_BALANCE = 1_000_000_000_000_000L;
    public static final long DEFAULT_MAX_TRANSACTION = 1_000_000_000_000L;

    /** Builds settings from raw values, correcting anything invalid and logging each correction. */
    public static CoreSettings create(String symbol, int decimals, long startingBalance, long maxBalance,
                                      long maxTransaction, String ownerRaw, String ownerName,
                                      boolean worldGuardProtection, Logger log) {
        String sym = symbol == null || symbol.isBlank() ? "$" : symbol;

        long max = maxBalance;
        if (max <= 0) {
            log.warning("economy.max-balance must be positive. Using " + DEFAULT_MAX_BALANCE);
            max = DEFAULT_MAX_BALANCE;
        }

        long start = startingBalance;
        if (start < 0 || start > max) {
            log.warning("economy.starting-balance is outside 0..max-balance. Using 0");
            start = 0;
        }

        long maxTx = maxTransaction;
        if (maxTx <= 0 || maxTx > max) {
            log.warning("economy.max-transaction is invalid. Using " + Math.min(DEFAULT_MAX_TRANSACTION, max));
            maxTx = Math.min(DEFAULT_MAX_TRANSACTION, max);
        }

        UUID owner = parseUuid(ownerRaw);
        if (owner == null && ownerRaw != null && !ownerRaw.isBlank()) {
            log.warning("owner.uuid is not a valid UUID. The Owner rank is disabled until it is fixed.");
        }

        return new CoreSettings(sym, start, max, maxTx, owner,
                ownerName == null ? "" : ownerName, worldGuardProtection,
                new NumberFormatter(decimals, sym));
    }

    /** Parses a UUID with or without dashes. Returns null when blank or invalid. */
    public static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (s.length() == 32 && s.indexOf('-') < 0) {
            s = s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16)
                    + "-" + s.substring(16, 20) + "-" + s.substring(20);
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
