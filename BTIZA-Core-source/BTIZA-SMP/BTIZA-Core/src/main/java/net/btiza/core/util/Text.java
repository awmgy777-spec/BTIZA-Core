package net.btiza.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/** MiniMessage helpers. Italics are turned off by default so item text looks clean. */
public final class Text {

    /** Chat prefix used by every BTIZA plugin. */
    public static final String PREFIX = "<dark_gray>[<red><bold>BTIZA</bold></red><dark_gray>]</dark_gray> <gray>";

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component mini(String input) {
        return MINI.deserialize(input).decoration(TextDecoration.ITALIC, false);
    }

    /** Escapes MiniMessage tags in user-supplied text such as player names, so they cannot inject formatting. */
    public static String escape(String untrusted) {
        return untrusted == null ? "" : MINI.escapeTags(untrusted);
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(mini(PREFIX + message));
    }
}
