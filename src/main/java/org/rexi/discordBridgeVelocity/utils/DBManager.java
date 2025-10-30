package org.rexi.discordBridgeVelocity.utils;

import java.sql.*;
import java.util.Optional;
import java.util.UUID;

public class DBManager {

    private final String dbType;
    private final String sqliteFile;
    private final String mysqlHost;
    private final int mysqlPort;
    private final String mysqlDatabase;
    private final String mysqlUser;
    private final String mysqlPass;

    public DBManager(String dbType, String sqliteFile, String mysqlHost, int mysqlPort, String mysqlDatabase, String mysqlUser, String mysqlPass) throws SQLException {
        this.dbType = dbType.toLowerCase();
        this.sqliteFile = sqliteFile;
        this.mysqlHost = mysqlHost;
        this.mysqlPort = mysqlPort;
        this.mysqlDatabase = mysqlDatabase;
        this.mysqlUser = mysqlUser;
        this.mysqlPass = mysqlPass;

        if (dbType.equalsIgnoreCase("MYSQL")) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        initTable();
    }

    private Connection getConnection() throws SQLException {
        if (dbType.equalsIgnoreCase("MYSQL")) {
            return DriverManager.getConnection(
                    "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase + "?useSSL=false",
                    mysqlUser, mysqlPass
            );
        } else {
            return DriverManager.getConnection("jdbc:sqlite:" + sqliteFile);
        }
    }

    private void initTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS discord_links (" +
                "minecraft_uuid TEXT PRIMARY KEY, " +
                "discord_id TEXT NOT NULL, " +
                "minecraft_name TEXT NOT NULL, " +
                "linked_at BIGINT NOT NULL, " +
                "recovery_code TEXT UNIQUE NOT NULL" +
                ")";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    public void saveLink(String uuid, String name, String discordId) {
        String recoveryCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String sql = "INSERT INTO discord_links (minecraft_uuid, minecraft_name, discord_id, recovery_code, linked_at) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT(minecraft_uuid) DO UPDATE SET " +
                "minecraft_name = excluded.minecraft_name, " +
                "discord_id = excluded.discord_id, " +
                "recovery_code = excluded.recovery_code, " +
                "linked_at = excluded.linked_at";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            stmt.setString(2, name);
            stmt.setString(3, discordId);
            stmt.setString(4, recoveryCode);
            stmt.setLong(5, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Optional<String> getDiscordId(String uuid) {
        String sql = "SELECT discord_id FROM discord_links WHERE minecraft_uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return Optional.of(rs.getString("discord_id"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<String> getMinecraftUUID(String discordId) {
        String sql = "SELECT minecraft_uuid FROM discord_links WHERE discord_id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, discordId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return Optional.of(rs.getString("minecraft_uuid"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<String> getMinecraftName(String discordId) {
        String sql = "SELECT minecraft_name FROM discord_links WHERE discord_id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, discordId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return Optional.of(rs.getString("minecraft_name"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<String> getRecoveryCode(String uuid) {
        String sql = "SELECT recovery_code FROM discord_links WHERE minecraft_uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return Optional.of(rs.getString("recovery_code"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<Long> getLinkDate(String identifier) {
        String sql = "SELECT linked_at FROM discord_links WHERE discord_id = ? OR minecraft_uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, identifier);
            stmt.setString(2, identifier);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return Optional.of(rs.getLong("linked_at"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }
}
