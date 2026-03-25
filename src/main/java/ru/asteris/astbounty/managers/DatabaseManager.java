package ru.asteris.astbounty.managers;

import ru.asteris.astbounty.Main;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final Main plugin;
    private Connection sqliteConnection;
    private final boolean useMySQL;

    public DatabaseManager(Main plugin) {
        this.plugin = plugin;

        String type = plugin.getConfig().getString("database.type", "SQLITE").toUpperCase();
        this.useMySQL = type.equals("MYSQL") &&
                ru.asteris.astlib.Main.getInstance().getDatabaseManager() != null &&
                ru.asteris.astlib.Main.getInstance().getDatabaseManager().isEnabled();

        initTables();
    }

    public Connection getConnection() throws SQLException {
        if (useMySQL) {
            return ru.asteris.astlib.Main.getInstance().getDatabaseManager().getConnection();
        }

        if (sqliteConnection == null || sqliteConnection.isClosed()) {
            File file = new File(plugin.getDataFolder(), "astbounty.db");
            sqliteConnection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        }
        return sqliteConnection;
    }

    private void initTables() {
        String bounties = "CREATE TABLE IF NOT EXISTS astbounty_bounties (target_uuid VARCHAR(36) PRIMARY KEY, hunter_uuid VARCHAR(36), time_left BIGINT, global_expire_time BIGINT);";
        String contributions = "CREATE TABLE IF NOT EXISTS astbounty_contributions (id INT AUTO_INCREMENT PRIMARY KEY, target_uuid VARCHAR(36), assigner_uuid VARCHAR(36), amount DOUBLE, killer_percentage INT, is_anonymous BOOLEAN);";
        String cooldowns = "CREATE TABLE IF NOT EXISTS astbounty_cooldowns (id INT AUTO_INCREMENT PRIMARY KEY, assigner_uuid VARCHAR(36), target_uuid VARCHAR(36), expire_time BIGINT);";
        String stats = "CREATE TABLE IF NOT EXISTS astbounty_stats (uuid VARCHAR(36) PRIMARY KEY, placed INT, target INT, completed INT);";

        if (!useMySQL) {
            contributions = "CREATE TABLE IF NOT EXISTS astbounty_contributions (id INTEGER PRIMARY KEY AUTOINCREMENT, target_uuid VARCHAR(36), assigner_uuid VARCHAR(36), amount DOUBLE, killer_percentage INT, is_anonymous BOOLEAN);";
            cooldowns = "CREATE TABLE IF NOT EXISTS astbounty_cooldowns (id INTEGER PRIMARY KEY AUTOINCREMENT, assigner_uuid VARCHAR(36), target_uuid VARCHAR(36), expire_time BIGINT);";
        }

        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute(bounties);
            st.execute(contributions);
            st.execute(cooldowns);
            st.execute(stats);
        } catch (SQLException ignored) {
        }
    }

    public void close() {
        try {
            if (sqliteConnection != null && !sqliteConnection.isClosed()) {
                sqliteConnection.close();
            }
        } catch (SQLException ignored) {
        }
    }
}