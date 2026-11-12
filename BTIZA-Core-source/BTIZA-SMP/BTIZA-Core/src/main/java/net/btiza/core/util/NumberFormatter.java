package net.btiza.core.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formats and parses BTIZA money values.
 * 1,000 becomes 1K, 1,000,000 becomes 1M, 1,000,000,000 becomes 1B, and 1,000,000,000,000 becomes 1T.
 * Uses BigDecimal so rounding never drifts.
 */
public final class NumberFormatter {

    private static final String[] SUFFIXES = {"", "K", "M", "B", "T"};
    private static final int MAX_TIER = SUFFIXES.length - 1;
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final Pattern INPUT = Pattern.compile("^(-?)(\\d+(?:\\.\\d+)?)([kmbt]?)$", Pattern.CASE_INSENSITIVE);

    private final int decimals;
    private final String symbol;

    public NumberFormatter(int decimals, String symbol) {
        this.decimals = Math.max(0, Math.min(4, decimals));
        this.symbol = symbol == null ? "" : symbol;
    }

    /** Compact value without the currency symbol. Example: 1500 becomes "1.5K". */
    public String compact(long value) {
        BigDecimal v = BigDecimal.valueOf(value);
        String sign = v.signum() < 0 ? "-" : "";
        return sign + compactAbs(v.abs());
    }

    /** Compact value with the currency symbol. Example: 1500 becomes "$1.5K". */
    public String money(long value) {
        BigDecimal v = BigDecimal.valueOf(value);
        String sign = v.signum() < 0 ? "-" : "";
        return sign + symbol + compactAbs(v.abs());
    }

    /** Full grouped value with the currency symbol. Example: "$1,234,567". */
    public String full(long value) {
        BigDecimal v = BigDecimal.valueOf(value);
        String sign = v.signum() < 0 ? "-" : "";
        return sign + symbol + NumberFormat.getIntegerInstance(Locale.US).format(v.abs());
    }

    /**
     * Parses player or admin input such as "1500", "1.5k", "2,000", "$3M" or "1b".
     * Returns empty for fractional dollars, unknown suffixes, or values that overflow a long.
     */
    public OptionalLong parse(String raw) {
        if (raw == null) {
            return OptionalLong.empty();
        }
        String s = raw.trim().replace(",", "").replace("$", "").replace("_", "");
        Matcher m = INPUT.matcher(s);
        if (!m.matches()) {
            return OptionalLong.empty();
        }
        int tier = switch (m.group(3).toLowerCase(Locale.ROOT)) {
            case "k" -> 1;
            case "m" -> 2;
            case "b" -> 3;
            case "t" -> 4;
            default -> 0;
        };
        BigDecimal value = new BigDecimal(m.group(2)).multiply(THOUSAND.pow(tier)).stripTrailingZeros();
        if (value.scale() > 0) {
            return OptionalLong.empty();
        }
        try {
            long result = value.longValueExact();
            return OptionalLong.of("-".equals(m.group(1)) ? -result : result);
        } catch (ArithmeticException overflow) {
            return OptionalLong.empty();
        }
    }

    private String compactAbs(BigDecimal v) {
        if (v.compareTo(THOUSAND) < 0) {
            return v.toBigInteger().toString();
        }
        BigDecimal scaled = v;
        int tier = 0;
        while (tier < MAX_TIER && scaled.compareTo(THOUSAND) >= 0) {
            scaled = scaled.divide(THOUSAND, 12, RoundingMode.HALF_UP);
            tier++;
        }
        // Rounding can push 999.99 up to 1000.00. Promote to the next tier in that case.
        while (true) {
            BigDecimal rounded = scaled.setScale(decimals, RoundingMode.HALF_UP);
            if (rounded.compareTo(THOUSAND) >= 0 && tier < MAX_TIER) {
                scaled = rounded.divide(THOUSAND, 12, RoundingMode.HALF_UP);
                tier++;
            } else {
                return rounded.stripTrailingZeros().toPlainString() + SUFFIXES[tier];
            }
        }
    }
}
