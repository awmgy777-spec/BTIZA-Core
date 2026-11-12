package net.btiza.core;

import net.btiza.core.config.CoreSettings;
import net.btiza.core.config.CoreSettingsLoader;
import net.btiza.core.database.Database;
import net.btiza.core.economy.EconomyService;
import net.btiza.core.gui.MenuListener;
import net.btiza.core.listener.PlayerListener;
import net.btiza.core.message.MessageService;
import net.btiza.core.protection.ProtectionService;
import net.btiza.core.util.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * BTIZA-Core. Provides the shared database, economy service, messages, GUI framework, and protection hook.
 *
 * Other plugins get services through Bukkit's ServicesManager:
 *   EconomyService economy = Bukkit.getServicesManager().load(EconomyService.class);
 *   ProtectionService protection = Bukkit.getServicesManager().load(ProtectionService.class);
 */
public final class BtizaCore extends JavaPlugin {

    private static volatile BtizaCore instance;

    private volatile CoreSettings settings;
    private Database database;
    private EconomyService economy;
    private ProtectionService protection;
    private MessageService messages;
    private Executor mainThread;

    public static BtizaCore get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        settings = CoreSettingsLoader.load(getConfig(), getLogger());

        messages = new MessageService(this);
        messages.reload();

        // Callbacks from the database thread are handed to the main thread. If the plugin is disabled,
        // the money change is already committed, so the skipped callback only affects feedback.
        mainThread = task -> {
            if (isEnabled()) {
                Bukkit.getScheduler().runTask(this, task);
            }
        };

        database = new Database(new File(getDataFolder(), "btiza.db"), getLogger());
        try {
            database.open();
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Could not open the BTIZA database. Disabling BTIZA-Core.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        economy = new EconomyService(database, settings, mainThread, getLogger());
        protection = ProtectionService.detect(getLogger(), settings.worldGuardProtection());

        getServer().getServicesManager().register(EconomyService.class, economy, this, ServicePriority.Normal);
        getServer().getServicesManager().register(ProtectionService.class, protection, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);

        // Covers a plugin reload while players are online.
        for (Player player : Bukkit.getOnlinePlayers()) {
            economy.onJoin(player.getUniqueId(), player.getName());
        }

        if (settings.ownerUuid() == null) {
            getLogger().warning("owner.uuid is not set in config.yml. The Owner rank stays disabled until you add your UUID.");
        }
        getLogger().info("BTIZA-Core enabled. Protection provider: " + protection.name());
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
        instance = null;
    }

    /**
     * Reloads config.yml and messages.yml and applies economy limits to the running service.
     * Protection provider changes and database settings still require a restart.
     */
    public void reloadSettings() {
        reloadConfig();
        settings = CoreSettingsLoader.load(getConfig(), getLogger());
        economy.updateSettings(settings);
        messages.reload();
    }

    public CoreSettings settings() {
        return settings;
    }

    public EconomyService economy() {
        return economy;
    }

    public ProtectionService protection() {
        return protection;
    }

    public MessageService messages() {
        return messages;
    }

    public NumberFormatter formatter() {
        return settings.formatter();
    }

    public boolean isOwner(UUID playerId) {
        return playerId != null && playerId.equals(settings.ownerUuid());
    }

    public Executor mainThread() {
        return mainThread;
    }
}
