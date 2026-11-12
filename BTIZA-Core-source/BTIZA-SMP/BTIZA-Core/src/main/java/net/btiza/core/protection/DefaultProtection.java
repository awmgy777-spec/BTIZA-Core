package net.btiza.core.protection;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Used when WorldGuard is unavailable. Grants access. Other plugins must enforce their own rules. */
final class DefaultProtection implements ProtectionService {

    private final String reason;

    DefaultProtection(String reason) {
        this.reason = reason;
    }

    @Override
    public String name() {
        return "none (" + reason + ")";
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        return player != null && location != null;
    }
}
