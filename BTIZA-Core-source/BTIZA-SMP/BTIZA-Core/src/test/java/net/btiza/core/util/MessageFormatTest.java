package net.btiza.core.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageFormatTest {

    @Test
    void replacesKnownPlaceholders() {
        Map<String, String> values = new HashMap<>();
        values.put("amount", "$1.5K");
        values.put("player", "awmgy");
        assertEquals("awmgy paid $1.5K", MessageFormat.render("{player} paid {amount}", values));
    }

    @Test
    void leavesUnknownPlaceholdersAndHandlesNulls() {
        Map<String, String> values = new HashMap<>();
        values.put("name", null);
        assertEquals("hi {other} ", MessageFormat.render("hi {other} {name}", values));
    }
}
