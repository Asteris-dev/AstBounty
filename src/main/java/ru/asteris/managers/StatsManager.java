package ru.asteris.managers;

import org.bukkit.Bukkit;
import ru.asteris.Main;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatsManager {

    private final Main plugin;
    private final DatabaseManager db;
    private final Map<UUID, int[]> cache = new HashMap<>();

    public StatsManager(Main plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void loadUser(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String query = "SELECT placed, targeted, completed FROM astbounty_stats WHERE uuid = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    cache.put(uuid, new int[]{rs.getInt("placed"), rs.getInt("targeted"), rs.getInt("completed")});
                } else {
                    cache.put(uuid, new int[]{0, 0, 0});
                    createUser(uuid);
                }
            } catch (SQLException ignored) {
            }
        });
    }

    private void createUser(UUID uuid) {
        String query = "INSERT INTO astbounty_stats (uuid, placed, targeted, completed) VALUES (?, 0, 0, 0)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public void saveUser(UUID uuid) {
        int[] stats = cache.get(uuid);
        if (stats == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveToDB(uuid, stats));
    }

    public void saveUserSync(UUID uuid) {
        int[] stats = cache.get(uuid);
        if (stats == null) return;
        saveToDB(uuid, stats);
    }

    private void saveToDB(UUID uuid, int[] stats) {
        String query = "UPDATE astbounty_stats SET placed = ?, targeted = ?, completed = ? WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, stats[0]);
            ps.setInt(2, stats[1]);
            ps.setInt(3, stats[2]);
            ps.setString(4, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public void unloadUser(UUID uuid) {
        saveUser(uuid);
        cache.remove(uuid);
    }

    public int getPlacedTotal(UUID uuid) {
        return cache.getOrDefault(uuid, new int[]{0, 0, 0})[0];
    }

    public int getTargetTotal(UUID uuid) {
        return cache.getOrDefault(uuid, new int[]{0, 0, 0})[1];
    }

    public int getCompletedTotal(UUID uuid) {
        return cache.getOrDefault(uuid, new int[]{0, 0, 0})[2];
    }

    public void addPlaced(UUID uuid) {
        int[] stats = cache.computeIfAbsent(uuid, k -> new int[]{0, 0, 0});
        stats[0]++;
        saveUser(uuid);
    }

    public void addTarget(UUID uuid) {
        int[] stats = cache.computeIfAbsent(uuid, k -> new int[]{0, 0, 0});
        stats[1]++;
        saveUser(uuid);
    }

    public void addCompleted(UUID uuid) {
        int[] stats = cache.computeIfAbsent(uuid, k -> new int[]{0, 0, 0});
        stats[2]++;
        saveUser(uuid);
    }
}