package net.btiza.core.config;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class CoreSettingsTest {

    private static final Logger LOG = Logger.getLogger("test");
    private static final String RAW = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    void uuidAcceptsDashedAndUndashed() {
        UUID expected = UUID.fromString(RAW);
        assertEquals(expected, CoreSettings.parseUuid(RAW));
        assertEquals(expected, CoreSettings.parseUuid(RAW.replace("-", "")));
    }

    @Test
    void uuidRejectsBlankAndGarbage() {
        assertNull(CoreSettings.parseUuid(""));
        assertNull(CoreSettings.parseUuid(null));
        assertNull(CoreSettings.parseUuid("not-a-uuid"));
    }

    @Test
    void invalidValuesAreCorrected() {
        CoreSettings s = CoreSettings.create("", 2, -5, 0, -1, "bad", "awmgy", false, LOG);
        assertEquals("$", s.currencySymbol());
        assertEquals(0, s.startingBalance());
        assertEquals(CoreSettings.DEFAULT_MAX_BALANCE, s.maxBalance());
        assertEquals(CoreSettings.DEFAULT_MAX_TRANSACTION, s.maxTransaction());
        assertNull(s.ownerUuid());
    }

    @Test
    void ownerUuidLoaded() {
        CoreSettings s = CoreSettings.create("$", 2, 0, 1_000_000L, 1_000L, RAW, "awmgy", false, LOG);
        assertEquals(UUID.fromString(RAW), s.ownerUuid());
        assertEquals("awmgy", s.ownerName());
    }
}
