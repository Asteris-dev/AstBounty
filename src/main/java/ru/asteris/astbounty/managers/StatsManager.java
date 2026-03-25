package ru.asteris.astbounty.managers;

import org.bukkit.Bukkit;
import ru.asteris.astbounty.Main;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StatsManager {

    private final Main plugin;
    private final DatabaseManager db;
    private final Map<UUID, PlayerStats> statsCache = new ConcurrentHashMap<>();

    public StatsManager(Main plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void loadUser(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String query = "SELECT * FROM astbounty_stats WHERE uuid = ?";
            try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        statsCache.put(uuid, new PlayerStats(rs.getInt("placed"), rs.getInt("target"), rs.getInt("completed")));
                    } else {
                        statsCache.put(uuid, new PlayerStats(0, 0, 0));
                    }
                }
            } catch (SQLException ignored) {
            }
        });
    }

    public void unloadUser(UUID uuid) {
        saveUserSync(uuid);
        statsCache.remove(uuid);
    }

    public void saveUserSync(UUID uuid) {
        PlayerStats stats = statsCache.get(uuid);
        if (stats == null) return;
        String query = "REPLACE INTO astbounty_stats (uuid, placed, target, completed) VALUES (?, ?, ?, ?)";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, stats.placed);
            ps.setInt(3, stats.target);
            ps.setInt(4, stats.completed);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public int getPlacedTotal(UUID uuid) { return statsCache.containsKey(uuid) ? statsCache.get(uuid).placed : 0; }
    public int getTargetTotal(UUID uuid) { return statsCache.containsKey(uuid) ? statsCache.get(uuid).target : 0; }
    public int getCompletedTotal(UUID uuid) { return statsCache.containsKey(uuid) ? statsCache.get(uuid).completed : 0; }

    public void addPlaced(UUID uuid) { if (statsCache.containsKey(uuid)) statsCache.get(uuid).placed++; }
    public void addTarget(UUID uuid) { if (statsCache.containsKey(uuid)) statsCache.get(uuid).target++; }
    public void addCompleted(UUID uuid) { if (statsCache.containsKey(uuid)) statsCache.get(uuid).completed++; }

    private static class PlayerStats {
        int placed;
        int target;
        int completed;
        PlayerStats(int placed, int target, int completed) {
            this.placed = placed;
            this.target = target;
            this.completed = completed;
        }
    }
}