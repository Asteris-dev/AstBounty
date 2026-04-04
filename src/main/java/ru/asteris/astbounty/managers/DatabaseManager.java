package ru.asteris.astbounty.managers;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import ru.asteris.astbounty.Main;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

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
        if (useMySQL) return ru.asteris.astlib.Main.getInstance().getDatabaseManager().getConnection();
        if (sqliteConnection == null || sqliteConnection.isClosed()) {
            File file = new File(plugin.getDataFolder(), "astbounty.db");
            sqliteConnection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        }
        return sqliteConnection;
    }

    private void initTables() {
        String bounties = "CREATE TABLE IF NOT EXISTS astbounty_bounties (target_uuid VARCHAR(36) PRIMARY KEY, hunter_uuid VARCHAR(36), time_left BIGINT, global_expire_time BIGINT);";
        String contributions = "CREATE TABLE IF NOT EXISTS astbounty_contributions (id INT AUTO_INCREMENT PRIMARY KEY, target_uuid VARCHAR(36), assigner_uuid VARCHAR(36), amount DOUBLE, item_data TEXT, killer_percentage INT, is_anonymous BOOLEAN);";
        String cooldowns = "CREATE TABLE IF NOT EXISTS astbounty_cooldowns (id INT AUTO_INCREMENT PRIMARY KEY, assigner_uuid VARCHAR(36), target_uuid VARCHAR(36), expire_time BIGINT);";
        String stats = "CREATE TABLE IF NOT EXISTS astbounty_stats (uuid VARCHAR(36) PRIMARY KEY, placed INT, target INT, completed INT);";
        String returns = "CREATE TABLE IF NOT EXISTS astbounty_returned_items (id INT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36), item_data TEXT);";

        if (!useMySQL) {
            contributions = "CREATE TABLE IF NOT EXISTS astbounty_contributions (id INTEGER PRIMARY KEY AUTOINCREMENT, target_uuid VARCHAR(36), assigner_uuid VARCHAR(36), amount DOUBLE, item_data TEXT, killer_percentage INT, is_anonymous BOOLEAN);";
            cooldowns = "CREATE TABLE IF NOT EXISTS astbounty_cooldowns (id INTEGER PRIMARY KEY AUTOINCREMENT, assigner_uuid VARCHAR(36), target_uuid VARCHAR(36), expire_time BIGINT);";
            returns = "CREATE TABLE IF NOT EXISTS astbounty_returned_items (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid VARCHAR(36), item_data TEXT);";
        }

        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute(bounties);
            st.execute(contributions);
            st.execute(cooldowns);
            st.execute(stats);
            st.execute(returns);
            try { st.execute("ALTER TABLE astbounty_contributions ADD COLUMN item_data TEXT;"); } catch (SQLException ignored) {}
        } catch (SQLException ignored) {}
    }

    public void addReturnedItem(UUID uuid, ItemStack item) {
        if (item == null) return;
        String query = "INSERT INTO astbounty_returned_items (uuid, item_data) VALUES (?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, itemToBase64(item));
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public List<ItemStack> getAndRemoveReturnedItems(UUID uuid) {
        List<ItemStack> items = new ArrayList<>();
        String select = "SELECT * FROM astbounty_returned_items WHERE uuid = ?";
        String delete = "DELETE FROM astbounty_returned_items WHERE uuid = ?";
        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(select)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(itemFromBase64(rs.getString("item_data")));
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(delete)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
        } catch (SQLException ignored) {}
        return items;
    }

    public String itemToBase64(ItemStack item) {
        if (item == null) return null;
        try {
            ByteArrayOutputStream io = new ByteArrayOutputStream();
            BukkitObjectOutputStream os = new BukkitObjectOutputStream(io);
            os.writeObject(item);
            os.close();
            return Base64.getEncoder().encodeToString(io.toByteArray());
        } catch (Exception e) { return null; }
    }

    public ItemStack itemFromBase64(String data) {
        if (data == null || data.isEmpty()) return null;
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream is = new BukkitObjectInputStream(in);
            ItemStack item = (ItemStack) is.readObject();
            is.close();
            return item;
        } catch (Exception e) { return null; }
    }

    public void close() {
        try { if (sqliteConnection != null && !sqliteConnection.isClosed()) sqliteConnection.close(); } catch (SQLException ignored) {}
    }
}