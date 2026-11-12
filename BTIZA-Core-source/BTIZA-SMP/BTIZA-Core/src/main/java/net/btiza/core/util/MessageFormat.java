package net.btiza.core.util;

import java.util.Map;

/** Replaces {name} placeholders in a template. Pure Java, so it can be tested without a server. */
public final class MessageFormat {

    private MessageFormat() {
    }

    /** Unknown placeholders are left as written. Null values become empty strings. */
    public static String render(String template, Map<String, String> placeholders) {
        String out = template;
        if (placeholders == null) {
            return out;
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            out = out.replace("{" + entry.getKey() + "}", value);
        }
        return out;
    }
}
