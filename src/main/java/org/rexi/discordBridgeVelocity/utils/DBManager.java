package org.rexi.discordBridgeVelocity.utils;

import java.sql.*;
import java.util.Optional;
import java.util.UUID;

public class DBManager {
    private Connection connection;
    private final String dbType;

    public DBManager(String dbType, String sqliteFile, String mysqlHost, int mysqlPort, String mysqlDatabase, String mysqlUser, String mysqlPass) throws SQLException {
        this.dbType = dbType.toLowerCase();
        if (dbType.equalsIgnoreCase("MYSQL")) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            connection = DriverManager.getConnection(
                    "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase + "?useSSL=false",
                    mysqlUser, mysqlPass
            );
        } else {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile);
        }
        initTable();
    }

    private void initTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS discord_links (" +
                "minecraft_uuid TEXT PRIMARY KEY, " +
                "discord_id TEXT NOT NULL, " +
                "minecraft_name TEXT NOT NULL, " +
                "linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "recovery_code TEXT UNIQUE NOT NULL" +
                ")";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * Guarda o actualiza un vínculo entre un jugador de Minecraft y un usuario de Discord.
     * Si ya existe, se actualiza la información (nombre, Discord ID y recovery code).
     */
    public void saveLink(String uuid, String name, String discordId) throws SQLException {
        checkConnection();
        String recoveryCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase(); // código único de recuperación

        String sql = "INSERT INTO discord_links (minecraft_uuid, minecraft_name, discord_id, recovery_code) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(minecraft_uuid) DO UPDATE SET " +
                "minecraft_name = excluded.minecraft_name, " +
                "discord_id = excluded.discord_id, " +
                "recovery_code = excluded.recovery_code, " +
                "linked_at = CURRENT_TIMESTAMP";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            stmt.setString(2, name);
            stmt.setString(3, discordId);
            stmt.setString(4, recoveryCode);
            stmt.executeUpdate();
        }
    }

    public Optional<String> getDiscordId(String uuid) {
        try {
            checkConnection();
            String sql = "SELECT discord_id FROM discord_links WHERE minecraft_uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, uuid);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) return Optional.of(rs.getString("discord_id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<String> getMinecraftUUID(String discordId) {
        try {
            checkConnection();
            String sql = "SELECT minecraft_uuid FROM discord_links WHERE discord_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, discordId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) return Optional.of(rs.getString("minecraft_uuid"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<String> getMinecraftName(String discordId) {
        try {
            checkConnection();
            String sql = "SELECT minecraft_name FROM discord_links WHERE discord_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, discordId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) return Optional.of(rs.getString("minecraft_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<String> getRecoveryCode(String uuid) {
        try {
            checkConnection();
            String sql = "SELECT recovery_code FROM discord_links WHERE minecraft_uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, uuid);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) return Optional.of(rs.getString("recovery_code"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) connection.close();
    }

    private synchronized void checkConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            // vuelve a abrir la conexión
            connection = DriverManager.getConnection("jdbc:sqlite:plugins/discord_bridge_velocity/database.db");
        }
    }
}

