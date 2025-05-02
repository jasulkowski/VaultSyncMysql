package com.kynnu.vaultsync;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VaultSyncPlugin extends JavaPlugin implements Listener {
    private Economy economy = null;
    private DatabaseManager dbManager;
    private BukkitTask syncTask;
    private int syncInterval;
    private final String LOG_PREFIX = "[VaultSyncMySQL] ";
    private boolean forceFullSync;
    private boolean syncNewPlayersImmediately;
    private String syncMethod;
    private boolean debugMode;
    private Set<UUID> knownPlayers = new HashSet<>();

    @Override
    public void onEnable() {

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        saveDefaultConfig();

        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
            getLogger().info(LOG_PREFIX + "Created default configuration file");
        }

        loadConfig();

        if (debugMode) {
            getLogger().info(LOG_PREFIX + "Debug mode enabled - verbose logging will be shown");
        }

        if (!setupEconomy()) {
            getLogger().severe(LOG_PREFIX + "Vault not found! Plugin will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        dbManager = new DatabaseManager(this);
        if (!dbManager.initialize()) {
            getLogger().severe(LOG_PREFIX + "Cannot connect to database! Plugin will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        dbManager.createTable();

        getServer().getPluginManager().registerEvents(this, this);

        loadKnownPlayersFromDatabase();

        if (forceFullSync) {
            getLogger().info(LOG_PREFIX + "Force full sync enabled. Starting synchronization of all players...");
            Bukkit.getScheduler().runTaskAsynchronously(this, this::performFullSync);
        }

        if (syncMethod.equalsIgnoreCase("time")) {
            startTimedSync();
            getLogger().info(LOG_PREFIX + "Using timed synchronization method (every " +
                    (syncInterval / 1200) + " minutes)");
        } else if (syncMethod.equalsIgnoreCase("events")) {
            getLogger().info(LOG_PREFIX + "Using event-based synchronization method (on player join/quit)");
        } else {
            getLogger().warning(LOG_PREFIX + "Unknown sync method: " + syncMethod +
                    ", defaulting to timed synchronization");
            startTimedSync();
        }

        getLogger().info(LOG_PREFIX + "VaultSync has been successfully enabled!");
    }

    @Override
    public void onDisable() {
        if (syncTask != null) {
            syncTask.cancel();
        }

        getLogger().info(LOG_PREFIX + "Performing final synchronization before shutdown...");
        for (Player player : Bukkit.getOnlinePlayers()) {
            String uuid = player.getUniqueId().toString();
            String name = player.getName();
            double balance = economy.getBalance(player);
            dbManager.updatePlayerBalance(uuid, name, balance);
        }

        if (dbManager != null) {
            dbManager.closeConnection();
        }

        getLogger().info(LOG_PREFIX + "VaultSync has been disabled!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }

        economy = rsp.getProvider();
        return economy != null;
    }

    private void loadConfig() {

        reloadConfig();
        FileConfiguration config = getConfig();
        syncInterval = config.getInt("sync-interval-minutes", 10) * 1200;
        forceFullSync = config.getBoolean("force-full-sync", false);
        syncNewPlayersImmediately = config.getBoolean("sync-new-players-immediately", true);
        syncMethod = config.getString("sync-method", "time").toLowerCase();
        debugMode = config.getBoolean("debug-mode", false);
    }

    private void startTimedSync() {
        syncTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            getLogger().info(LOG_PREFIX + "Starting timed synchronization from Vault to MySQL...");
            int count = 0;

            for (Player player : Bukkit.getOnlinePlayers()) {
                String uuid = player.getUniqueId().toString();
                String name = player.getName();
                double balance = economy.getBalance(player);

                dbManager.updatePlayerBalance(uuid, name, balance);
                count++;
            }

            if (debugMode) {
                getLogger().info(LOG_PREFIX + "Synchronized " + count + " online players");
            }

            getLogger().info(LOG_PREFIX + "Timed synchronization completed!");
        }, 100L, syncInterval);
    }

    private void performFullSync() {
        getLogger().info(LOG_PREFIX + "Starting full synchronization of all players...");

        for (Player player : Bukkit.getOnlinePlayers()) {
            String uuid = player.getUniqueId().toString();
            String name = player.getName();
            double balance = economy.getBalance(player);

            dbManager.updatePlayerBalance(uuid, name, balance);
            knownPlayers.add(player.getUniqueId());

            if (debugMode) {
                getLogger().info(LOG_PREFIX + "Synchronized online player: " + name + " with balance: " + balance);
            }
        }

        OfflinePlayer[] offlinePlayers = Bukkit.getOfflinePlayers();
        int count = 0;

        for (OfflinePlayer offlinePlayer : offlinePlayers) {
            if (offlinePlayer.isOnline()) continue;

            String uuid = offlinePlayer.getUniqueId().toString();
            String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";
            double balance = economy.getBalance(offlinePlayer);

            dbManager.updatePlayerBalance(uuid, name, balance);
            knownPlayers.add(offlinePlayer.getUniqueId());
            count++;

            if (debugMode && count % 10 == 0) {
                getLogger().info(LOG_PREFIX + "Synchronized offline player: " + name + " with balance: " + balance);
            }

            if (count % 100 == 0) {
                getLogger().info(LOG_PREFIX + "Synchronized " + count + " offline players so far...");
            }
        }

        getLogger().info(LOG_PREFIX + "Full synchronization completed! Total offline players synchronized: " + count);
    }

    private void syncPlayer(Player player) {
        String uuid = player.getUniqueId().toString();
        String name = player.getName();
        double balance = economy.getBalance(player);

        dbManager.updatePlayerBalance(uuid, name, balance);
        knownPlayers.add(player.getUniqueId());

        if (debugMode) {
            getLogger().info(LOG_PREFIX + "Synchronized player: " + name + " with balance: " + balance);
        }
    }

    private void loadKnownPlayersFromDatabase() {
        Set<UUID> players = dbManager.getAllPlayerUUIDs();
        if (players != null) {
            knownPlayers = players;
            getLogger().info(LOG_PREFIX + "Loaded " + knownPlayers.size() + " known players from database.");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final UUID playerUUID = player.getUniqueId();

        if (syncNewPlayersImmediately && !knownPlayers.contains(playerUUID)) {

            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                syncPlayer(player);
                getLogger().info(LOG_PREFIX + "New player " + player.getName() +
                        " detected and synchronized with balance: " + economy.getBalance(player));
            });
        }

        else if (syncMethod.equalsIgnoreCase("events")) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                syncPlayer(player);
                if (debugMode) {
                    getLogger().info(LOG_PREFIX + "Player " + player.getName() +
                            " joined and synchronized with balance: " + economy.getBalance(player));
                }
            });
        }
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {

        if (syncMethod.equalsIgnoreCase("events")) {
            final Player player = event.getPlayer();


            String uuid = player.getUniqueId().toString();
            String name = player.getName();
            double balance = economy.getBalance(player);

            dbManager.updatePlayerBalance(uuid, name, balance);

            if (debugMode) {
                getLogger().info(LOG_PREFIX + "Player " + name +
                        " quit and synchronized with balance: " + balance);
            }
        }
    }

    public Economy getEconomy() {
        return economy;
    }

    public boolean isDebugMode() {
        return debugMode;
    }
}
