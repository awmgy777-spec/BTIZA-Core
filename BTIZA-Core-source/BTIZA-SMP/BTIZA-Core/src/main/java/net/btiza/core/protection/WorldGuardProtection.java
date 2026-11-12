package net.btiza.core.protection;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Region-based build checks through WorldGuard. Only instantiated when the WorldGuard plugin is enabled,
 * so this class is never loaded on servers without it.
 *
 * Fails closed: if the check throws, the action is denied. The first error is logged with its stack trace.
 * Later errors are counted but not logged, so a broken WorldGuard cannot flood the console.
 */
final class WorldGuardProtection implements ProtectionService {

    private final Logger logger;
    private final AtomicBoolean errorLogged = new AtomicBoolean(false);

    WorldGuardProtection(Logger logger) {
        this.logger = logger;
        // Touch the API now so a broken install fails during detect(), not during gameplay.
        WorldGuard.getInstance();
    }

    @Override
    public String name() {
        return "WorldGuard";
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return false;
        }
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return true;
        }
        try {
            LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            var query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            return query.testBuild(BukkitAdapter.adapt(location), localPlayer, Flags.BUILD);
        } catch (RuntimeException error) {
            if (errorLogged.compareAndSet(false, true)) {
                logger.log(Level.SEVERE, "WorldGuard build check failed. Denying the action. Further errors are not logged.", error);
            }
            return false;
        }
    }
}
