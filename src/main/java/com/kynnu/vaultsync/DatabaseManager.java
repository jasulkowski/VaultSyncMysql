package com.kynnu.vaultsync;

import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;


public class DatabaseManager {
    private final VaultSyncPlugin plugin;
    private Connection connection;
    private String host, database, username, password, table;
    private int port;
    private final String LOG_PREFIX = "[VaultSyncMySQL] ";

    public DatabaseManager(VaultSyncPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        host = config.getString("database.host", "localhost");
        port = config.getInt("database.port", 3306);
        database = config.getString("database.name", "minecraft");
        username = config.getString("database.username", "root");
        password = config.getString("database.password", "");
        table = config.getString("database.table", "player_money");
    }

    public boolean initialize() {
        try {
            if (connection != null && !connection.isClosed()) {
                return true;
            }

            synchronized (this) {
                if (connection != null && !connection.isClosed()) {
                    return true;
                }

                try {
                    Class.forName("com.mysql.jdbc.Driver");
                } catch (ClassNotFoundException e) {
                    // Próbujemy nowszy sterownik, jeśli stary nie jest dostępny
                    try {
                        Class.forName("com.mysql.cj.jdbc.Driver");
                    } catch (ClassNotFoundException ex) {
                        plugin.getLogger().log(Level.SEVERE, LOG_PREFIX + "MySQL driver not found!", ex);
                        return false;
                    }
                }

                String connectionUrl = "jdbc:mysql://" + host + ":" + port + "/" + database +
                        "?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf8";

                connection = DriverManager.getConnection(connectionUrl, username, password);
                plugin.getLogger().info(LOG_PREFIX + "Successfully connected to MySQL database!");

                return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, LOG_PREFIX + "Error connecting to database: " + e.getMessage(), e);
            return false;
        }
    }

    public void createTable() {
        if (!initialize()) {
            plugin.getLogger().severe(LOG_PREFIX + "Cannot create table - database connection failed!");
            return;
        }

        try (Statement statement = connection.createStatement()) {
            String createTableSQL = "CREATE TABLE IF NOT EXISTS " + table + " (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY, " +
                    "player_name VARCHAR(16) NOT NULL, " +
                    "balance DOUBLE NOT NULL, " +
                    "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ")";

            statement.executeUpdate(createTableSQL);
            plugin.getLogger().info(LOG_PREFIX + "Table '" + table + "' checked/created successfully!");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, LOG_PREFIX + "Error creating table: " + e.getMessage(), e);
        }
    }

    public void updatePlayerBalance(String uuid, String name, double balance) {
        if (!initialize()) {
            plugin.getLogger().warning(LOG_PREFIX + "Skipping balance update for " + name + " - database connection failed!");
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + table + " (player_uuid, player_name, balance) " +
                        "VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE player_name = ?, balance = ?")) {

            statement.setString(1, uuid);
            statement.setString(2, name);
            statement.setDouble(3, balance);
            statement.setString(4, name);
            statement.setDouble(5, balance);

            statement.executeUpdate();

            if (plugin.isDebugMode()) {
                plugin.getLogger().info(LOG_PREFIX + "Updated balance for " + name + " (" + uuid + "): " + balance);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, LOG_PREFIX + "Error updating player balance for " + name + ": " + e.getMessage(), e);
        }
    }

    public Set<UUID> getAllPlayerUUIDs() {
        if (!initialize()) {
            plugin.getLogger().warning(LOG_PREFIX + "Cannot get player UUIDs - database connection failed!");
            return new HashSet<>();
        }

        Set<UUID> players = new HashSet<>();

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT player_uuid FROM " + table)) {

            while (resultSet.next()) {
                try {
                    UUID uuid = UUID.fromString(resultSet.getString("player_uuid"));
                    players.add(uuid);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().log(Level.WARNING, LOG_PREFIX + "Invalid UUID in database: " + resultSet.getString("player_uuid"));
                }
            }

            plugin.getLogger().info(LOG_PREFIX + "Successfully loaded " + players.size() + " player UUIDs from database.");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, LOG_PREFIX + "Error getting player UUIDs from database: " + e.getMessage(), e);
            return new HashSet<>();
        }

        return players;
    }

    public double getPlayerBalance(UUID playerUUID) {
        if (!initialize()) {
            plugin.getLogger().warning(LOG_PREFIX + "Cannot get player balance - database connection failed!");
            return -1;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT balance FROM " + table + " WHERE player_uuid = ?")) {

            statement.setString(1, playerUUID.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getDouble("balance");
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, LOG_PREFIX + "Error getting player balance from database: " + e.getMessage(), e);
        }

        return -1;
    }

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean reconnect() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, LOG_PREFIX + "Error closing old connection: " + e.getMessage(), e);
        }

        connection = null;
        return initialize();
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info(LOG_PREFIX + "Database connection closed successfully.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, LOG_PREFIX + "Error closing database connection: " + e.getMessage(), e);
        }
    }
}
