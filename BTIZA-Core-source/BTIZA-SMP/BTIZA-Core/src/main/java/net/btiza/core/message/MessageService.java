package net.btiza.core.message;

import net.btiza.core.economy.TransactionResult.Status;
import net.btiza.core.util.MessageFormat;
import net.btiza.core.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Loads messages.yml from the plugin folder. Keys missing from the server copy fall back to the
 * defaults bundled in the jar, so a partially edited file never shows blank text.
 */
public final class MessageService {

    private final JavaPlugin plugin;
    private volatile YamlConfiguration messages = new YamlConfiguration();
    private volatile YamlConfiguration defaults = new YamlConfiguration();

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml")) {
            defaults = in == null
                    ? new YamlConfiguration()
                    : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Could not read bundled messages.yml defaults: " + e.getMessage());
        }
    }

    /** Raw MiniMessage template for a key, with a visible marker if the key is missing everywhere. */
    public String template(String key) {
        String value = messages.getString(key);
        if (value == null) {
            value = defaults.getString(key);
        }
        return value == null ? "<red>[missing message: " + key + "]" : value;
    }

    /** Builds a component. Placeholder values are escaped so player input cannot inject formatting. */
    public Component component(String key, Map<String, String> placeholders) {
        return Text.mini(render(key, placeholders));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(Text.mini(Text.PREFIX + render(key, placeholders)));
    }

    /** Message shown for a failed economy transaction. */
    public Component describe(Status status) {
        return component(economyKey(status), Map.of());
    }

    private String render(String key, Map<String, String> placeholders) {
        Map<String, String> escaped = placeholders == null ? Map.of() : escapeValues(placeholders);
        return MessageFormat.render(template(key), escaped);
    }

    private static Map<String, String> escapeValues(Map<String, String> values) {
        Map<String, String> out = new java.util.HashMap<>();
        values.forEach((k, v) -> out.put(k, Text.escape(v)));
        return out;
    }

    private static String economyKey(Status status) {
        return switch (status) {
            case SUCCESS -> "economy.success";
            case INSUFFICIENT_FUNDS -> "economy.insufficient-funds";
            case INVALID_AMOUNT -> "economy.invalid-amount";
            case LIMIT_EXCEEDED -> "economy.limit-exceeded";
            case SELF_TRANSFER -> "economy.self-transfer";
            case DATABASE_ERROR -> "economy.database-error";
        };
    }
}
