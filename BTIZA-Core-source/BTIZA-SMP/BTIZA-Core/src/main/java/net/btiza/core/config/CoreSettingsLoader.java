package net.btiza.core.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Logger;

/** Reads config.yml into an immutable CoreSettings snapshot. */
public final class CoreSettingsLoader {

    private CoreSettingsLoader() {
    }

    public static CoreSettings load(FileConfiguration config, Logger log) {
        return CoreSettings.create(
                config.getString("currency.symbol", "$"),
                config.getInt("currency.decimals", 2),
                config.getLong("economy.starting-balance", 0L),
                config.getLong("economy.max-balance", CoreSettings.DEFAULT_MAX_BALANCE),
                config.getLong("economy.max-transaction", CoreSettings.DEFAULT_MAX_TRANSACTION),
                config.getString("owner.uuid", ""),
                config.getString("owner.name", "awmgy"),
                config.getBoolean("protection.worldguard", true),
                log);
    }
}
