package ru.asteris.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import ru.asteris.Main;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DatabaseManager {

    private final Main plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(Main plugin) {
        this.plugin = plugin;
        connect();
    }

    private void connect() {
        FileConfiguration config = plugin.getConfig();
        HikariConfig hikariConfig = new HikariConfig();
        String type = config.getString("database.type", "SQLITE").toUpperCase();

        if (type.equals("MYSQL")) {
            String host = config.getString("database.mysql.host", "localhost");
            int port = config.getInt("database.mysql.port", 3306);
            String database = config.getString("database.mysql.database", "astbounty");
            String username = config.getString("database.mysql.username", "root");
            String password = config.getString("database.mysql.password", "");
            boolean useSsl = config.getBoolean("database.mysql.use-ssl", false);

            hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSsl + "&autoReconnect=true");
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
        } else {
            File dbFile = new File(plugin.getDataFolder(), "database.db");
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
        }

        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(10000);

        dataSource = new HikariDataSource(hikariConfig);
        createTables();
    }

    private void createTables() {
        String queryStats = "CREATE TABLE IF NOT EXISTS astbounty_stats (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "placed INT DEFAULT 0, " +
                "targeted INT DEFAULT 0, " +
                "completed INT DEFAULT 0)";

        String queryBounties = "CREATE TABLE IF NOT EXISTS astbounty_bounties (" +
                "target_uuid VARCHAR(36) PRIMARY KEY, " +
                "hunter_uuid VARCHAR(36), " +
                "time_left BIGINT, " +
                "global_expire_time BIGINT DEFAULT 0)";

        String queryContributions = "CREATE TABLE IF NOT EXISTS astbounty_contributions (" +
                "target_uuid VARCHAR(36), " +
                "assigner_uuid VARCHAR(36), " +
                "amount DOUBLE, " +
                "killer_percentage INT, " +
                "is_anonymous BOOLEAN DEFAULT 0)";

        String queryCooldowns = "CREATE TABLE IF NOT EXISTS astbounty_cooldowns (" +
                "assigner_uuid VARCHAR(36), " +
                "target_uuid VARCHAR(36), " +
                "expire_time BIGINT, " +
                "PRIMARY KEY (assigner_uuid, target_uuid))";

        try (Connection conn = getConnection();
             PreparedStatement ps1 = conn.prepareStatement(queryStats);
             PreparedStatement ps2 = conn.prepareStatement(queryBounties);
             PreparedStatement ps3 = conn.prepareStatement(queryContributions);
             PreparedStatement ps4 = conn.prepareStatement(queryCooldowns)) {
            ps1.executeUpdate();
            ps2.executeUpdate();
            ps3.executeUpdate();
            ps4.executeUpdate();
        } catch (SQLException ignored) {
        }

        try (Connection conn = getConnection();
             PreparedStatement psAlter = conn.prepareStatement("ALTER TABLE astbounty_bounties ADD COLUMN global_expire_time BIGINT DEFAULT 0")) {
            psAlter.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}