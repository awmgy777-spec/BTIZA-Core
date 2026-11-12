package net.btiza.core.protection;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * Answers whether a player may modify a location. BTIZA features (the 3x3 pickaxe, RTP, spawners)
 * call this instead of checking WorldGuard directly, so they work with or without WorldGuard.
 */
public interface ProtectionService {

    /** Permission that lets a player bypass region checks in BTIZA features. */
    String BYPASS_PERMISSION = "btiza.bypass.protection";

    String name();

    /** Returns true only when the player is allowed to modify the location. */
    boolean canBuild(Player player, Location location);

    /**
     * Chooses WorldGuard when the config allows it and the plugin is enabled.
     * Otherwise allows everything, and the calling plugin must enforce its own rules.
     */
    static ProtectionService detect(Logger logger, boolean worldGuardEnabledInConfig) {
        if (!worldGuardEnabledInConfig) {
            return new DefaultProtection("WorldGuard disabled in config");
        }
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            try {
                return new WorldGuardProtection(logger);
            } catch (Throwable error) {
                logger.log(java.util.logging.Level.WARNING,
                        "WorldGuard is installed but its API could not be loaded. Using fallback protection.", error);
            }
        }
        return new DefaultProtection("WorldGuard not installed");
    }
}
