package net.btiza.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NumberFormatterTest {

    private final NumberFormatter f = new NumberFormatter(2, "$");

    @Test
    void compactFormatsEachTier() {
        assertEquals("999", f.compact(999));
        assertEquals("1K", f.compact(1_000));
        assertEquals("1.5K", f.compact(1_500));
        assertEquals("1.23M", f.compact(1_234_567));
        assertEquals("1B", f.compact(1_000_000_000L));
        assertEquals("1T", f.compact(1_000_000_000_000L));
    }

    @Test
    void roundingPromotesToNextTier() {
        assertEquals("1M", f.compact(999_999));
    }

    @Test
    void negativeValues() {
        assertEquals("-1.5K", f.compact(-1_500));
        assertEquals("-$1.5K", f.money(-1_500));
    }

    @Test
    void moneyAndFullFormats() {
        assertEquals("$1.5K", f.money(1_500));
        assertEquals("$1,234,567", f.full(1_234_567));
        assertEquals("-$1,000", f.full(-1_000));
    }

    @Test
    void parseAcceptsCommonInput() {
        assertEquals(1_500L, f.parse("1500").getAsLong());
        assertEquals(1_500L, f.parse("1.5k").getAsLong());
        assertEquals(1_500_000L, f.parse("1.5M").getAsLong());
        assertEquals(2_500L, f.parse("$2,500").getAsLong());
        assertEquals(2_000_000_000_000L, f.parse("2t").getAsLong());
    }

    @Test
    void parseRejectsBadInput() {
        assertTrue(f.parse("abc").isEmpty());
        assertTrue(f.parse("1.5").isEmpty());
        assertTrue(f.parse("").isEmpty());
        assertTrue(f.parse(null).isEmpty());
        assertTrue(f.parse("99999999999999999999T").isEmpty());
    }
}
