package net.btiza.core.economy;

import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

/** Outcome of an economy operation. balances holds the post-transaction balances of affected accounts. */
public record TransactionResult(Status status, Map<UUID, Long> balances) {

    public enum Status {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        INVALID_AMOUNT,
        LIMIT_EXCEEDED,
        SELF_TRANSFER,
        DATABASE_ERROR
    }

    public TransactionResult {
        balances = balances == null ? Map.of() : Map.copyOf(balances);
    }

    public static TransactionResult ok(Map<UUID, Long> balances) {
        return new TransactionResult(Status.SUCCESS, balances);
    }

    public static TransactionResult fail(Status status) {
        return new TransactionResult(status, Map.of());
    }

    public boolean success() {
        return status == Status.SUCCESS;
    }

    public OptionalLong balanceOf(UUID id) {
        Long value = balances.get(id);
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }
}
