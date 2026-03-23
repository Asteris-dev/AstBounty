package ru.asteris.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import ru.asteris.Main;
import ru.asteris.utils.ColorUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BountyManager {

    private final Main plugin;
    private final DatabaseManager db;
    private final Map<UUID, Bounty> bounties = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, Long> placeCooldowns = new ConcurrentHashMap<>();

    public BountyManager(Main plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
        startTasks();
    }

    public void loadBounties() {
        bounties.clear();
        placeCooldowns.clear();
        String queryBounties = "SELECT * FROM astbounty_bounties";
        String queryContribs = "SELECT * FROM astbounty_contributions WHERE target_uuid = ?";
        String queryCooldowns = "SELECT * FROM astbounty_cooldowns";

        try (Connection conn = db.getConnection();
             PreparedStatement psB = conn.prepareStatement(queryBounties);
             ResultSet rsB = psB.executeQuery()) {

            while (rsB.next()) {
                UUID target = UUID.fromString(rsB.getString("target_uuid"));
                Bounty bounty = new Bounty(target);
                String hunterStr = rsB.getString("hunter_uuid");
                if (hunterStr != null && !hunterStr.isEmpty()) {
                    bounty.setHunter(UUID.fromString(hunterStr), rsB.getLong("time_left"));
                }
                bounty.setGlobalExpireTime(rsB.getLong("global_expire_time"));

                try (PreparedStatement psC = conn.prepareStatement(queryContribs)) {
                    psC.setString(1, target.toString());
                    try (ResultSet rsC = psC.executeQuery()) {
                        while (rsC.next()) {
                            UUID assigner = UUID.fromString(rsC.getString("assigner_uuid"));
                            double amount = rsC.getDouble("amount");
                            int percent = rsC.getInt("killer_percentage");
                            boolean anon = rsC.getBoolean("is_anonymous");
                            bounty.addContribution(new Contribution(assigner, amount, percent, anon));
                        }
                    }
                }
                bounties.put(target, bounty);
            }

            try (PreparedStatement psCool = conn.prepareStatement(queryCooldowns);
                 ResultSet rsCool = psCool.executeQuery()) {
                while (rsCool.next()) {
                    String key = rsCool.getString("assigner_uuid") + "_" + rsCool.getString("target_uuid");
                    placeCooldowns.put(key, rsCool.getLong("expire_time"));
                }
            }
        } catch (SQLException ignored) {
        }
    }

    public void saveBounties() {
        String clearBounties = "DELETE FROM astbounty_bounties";
        String clearContribs = "DELETE FROM astbounty_contributions";
        String clearCooldowns = "DELETE FROM astbounty_cooldowns";

        String insertBounty = "INSERT INTO astbounty_bounties (target_uuid, hunter_uuid, time_left, global_expire_time) VALUES (?, ?, ?, ?)";
        String insertContrib = "INSERT INTO astbounty_contributions (target_uuid, assigner_uuid, amount, killer_percentage, is_anonymous) VALUES (?, ?, ?, ?, ?)";
        String insertCooldown = "INSERT INTO astbounty_cooldowns (assigner_uuid, target_uuid, expire_time) VALUES (?, ?, ?)";

        try (Connection conn = db.getConnection();
             PreparedStatement psClearB = conn.prepareStatement(clearBounties);
             PreparedStatement psClearC = conn.prepareStatement(clearContribs);
             PreparedStatement psClearCool = conn.prepareStatement(clearCooldowns)) {

            psClearB.executeUpdate();
            psClearC.executeUpdate();
            psClearCool.executeUpdate();

            for (Bounty bounty : bounties.values()) {
                try (PreparedStatement psB = conn.prepareStatement(insertBounty)) {
                    psB.setString(1, bounty.getTarget().toString());
                    psB.setString(2, bounty.getHunter() != null ? bounty.getHunter().toString() : null);
                    psB.setLong(3, bounty.getTimeLeft());
                    psB.setLong(4, bounty.getGlobalExpireTime());
                    psB.executeUpdate();
                }

                for (Contribution c : bounty.getContributions()) {
                    try (PreparedStatement psC = conn.prepareStatement(insertContrib)) {
                        psC.setString(1, bounty.getTarget().toString());
                        psC.setString(2, c.getAssigner().toString());
                        psC.setDouble(3, c.getAmount());
                        psC.setInt(4, c.getKillerPercentage());
                        psC.setBoolean(5, c.isAnonymous());
                        psC.executeUpdate();
                    }
                }
            }

            for (Map.Entry<String, Long> entry : placeCooldowns.entrySet()) {
                if (entry.getValue() > System.currentTimeMillis()) {
                    String[] parts = entry.getKey().split("_");
                    try (PreparedStatement psCool = conn.prepareStatement(insertCooldown)) {
                        psCool.setString(1, parts[0]);
                        psCool.setString(2, parts[1]);
                        psCool.setLong(3, entry.getValue());
                        psCool.executeUpdate();
                    }
                }
            }
        } catch (SQLException ignored) {
        }
    }

    public void removeBounty(UUID target) {
        bounties.remove(target);
    }

    public Bounty getBounty(UUID target) {
        return bounties.get(target);
    }

    public Collection<Bounty> getAllBounties() {
        return bounties.values();
    }

    public void addBounty(UUID target, UUID assigner, double amount, int killerPercent, boolean isAnonymous) {
        Bounty bounty = bounties.computeIfAbsent(target, Bounty::new);
        bounty.addContribution(new Contribution(assigner, amount, killerPercent, isAnonymous));

        long globalSeconds = plugin.getConfig().getLong("settings.global-expiration-seconds", 604800);
        bounty.setGlobalExpireTime(System.currentTimeMillis() + (globalSeconds * 1000L));
    }

    public boolean hasPlaceCooldown(UUID assigner, UUID target) {
        String key = assigner.toString() + "_" + target.toString();
        return placeCooldowns.containsKey(key) && placeCooldowns.get(key) > System.currentTimeMillis();
    }

    public long getPlaceCooldownLeft(UUID assigner, UUID target) {
        String key = assigner.toString() + "_" + target.toString();
        return hasPlaceCooldown(assigner, target) ? placeCooldowns.get(key) - System.currentTimeMillis() : 0;
    }

    public void setPlaceCooldown(UUID assigner, UUID target) {
        long seconds = plugin.getConfig().getLong("settings.same-target-cooldown-seconds", 86400);
        String key = assigner.toString() + "_" + target.toString();
        placeCooldowns.put(key, System.currentTimeMillis() + (seconds * 1000L));
    }

    public boolean hasActiveBounty(UUID hunter) {
        return bounties.values().stream().anyMatch(b -> hunter.equals(b.getHunter()));
    }

    public boolean hasCooldown(UUID player) {
        return cooldowns.containsKey(player) && cooldowns.get(player) > System.currentTimeMillis();
    }

    public long getCooldownLeft(UUID player) {
        return hasCooldown(player) ? cooldowns.get(player) - System.currentTimeMillis() : 0;
    }

    public void setCooldown(UUID player) {
        long seconds = plugin.getConfig().getLong("settings.fail-cooldown-seconds");
        cooldowns.put(player, System.currentTimeMillis() + (seconds * 1000L));
    }

    public double calculateTakeCost(Bounty bounty) {
        FileConfiguration config = plugin.getConfig();
        String type = config.getString("settings.bounty-take-cost.type", "PERCENT");
        double value = config.getDouble("settings.bounty-take-cost.value", 10.0);

        if (type.equalsIgnoreCase("PERCENT")) {
            return bounty.getTotalBank() * (value / 100.0);
        }
        return value;
    }

    public double calculateBuyoutCost(Bounty bounty) {
        return bounty.getTotalBank() * plugin.getConfig().getDouble("settings.buyout.multiplier", 1.5);
    }

    public void completeBounty(UUID target, Player killer) {
        Bounty bounty = bounties.get(target);
        if (bounty == null || !killer.getUniqueId().equals(bounty.getHunter())) return;

        FileConfiguration config = plugin.getConfig();
        String prefix = config.getString("messages.prefix", "");
        String symbol = config.getString("settings.currency-symbol", "$");
        String targetName = Bukkit.getOfflinePlayer(target).getName() != null ? Bukkit.getOfflinePlayer(target).getName() : "Unknown";
        double totalEarned = 0;

        for (Contribution c : bounty.getContributions()) {
            double killerShare = c.getAmount() * (c.getKillerPercentage() / 100.0);
            double assignerShare = c.getAmount() - killerShare;
            totalEarned += killerShare;

            OfflinePlayer assignerPlayer = Bukkit.getOfflinePlayer(c.getAssigner());
            plugin.getEconomy().depositPlayer(assignerPlayer, assignerShare);

            if (assignerShare > 0 && assignerPlayer.isOnline() && assignerPlayer.getPlayer() != null) {
                String returnMsg = config.getString("messages.bounty-reward-returned", "")
                        .replace("{target}", targetName)
                        .replace("{hunter}", killer.getName())
                        .replace("{money}", ColorUtils.formatMoney(assignerShare))
                        .replace("{symbol}", symbol);
                assignerPlayer.getPlayer().sendMessage(ColorUtils.parse(prefix + returnMsg));
            }
        }

        plugin.getEconomy().depositPlayer(killer, totalEarned);
        plugin.getStatsManager().addCompleted(killer.getUniqueId());
        bounties.remove(target);

        String msg = config.getString("messages.bounty-completed", "")
                .replace("{target}", targetName)
                .replace("{money}", ColorUtils.formatMoney(totalEarned))
                .replace("{symbol}", symbol);
        killer.sendMessage(ColorUtils.parse(prefix + msg));
        playSound(killer, "complete");

        if (config.getBoolean("settings.trophy-head.enabled", true)) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(target));
                meta.setDisplayName(ColorUtils.colorize(config.getString("settings.trophy-head.name", "").replace("{target}", targetName)));
                List<String> lore = new ArrayList<>();
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
                String dateStr = sdf.format(new Date());

                for (String line : config.getStringList("settings.trophy-head.lore")) {
                    lore.add(ColorUtils.colorize(line
                            .replace("{hunter}", killer.getName())
                            .replace("{money}", ColorUtils.formatMoney(totalEarned))
                            .replace("{symbol}", symbol)
                            .replace("{date}", dateStr)
                    ));
                }
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            killer.getInventory().addItem(head).values().forEach(item -> killer.getWorld().dropItem(killer.getLocation(), item));
        }

        plugin.getMenuManager().updateMenus();
    }

    private void startTasks() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            FileConfiguration config = plugin.getConfig();
            boolean actionBarEnabled = config.getBoolean("settings.actionbar.enabled");
            String actionBarMsg = config.getString("settings.actionbar.message", "");
            boolean pauseOffline = config.getBoolean("settings.pause-timer-on-offline", true);
            String symbol = config.getString("settings.currency-symbol", "$");
            long now = System.currentTimeMillis();
            String prefix = config.getString("messages.prefix", "");
            boolean menuNeedsUpdate = false;

            Iterator<Map.Entry<UUID, Bounty>> iterator = bounties.entrySet().iterator();
            while (iterator.hasNext()) {
                Bounty bounty = iterator.next().getValue();
                UUID hunterId = bounty.getHunter();

                if (hunterId == null) {
                    if (now >= bounty.getGlobalExpireTime() && bounty.getGlobalExpireTime() > 0) {
                        double anonCost = config.getDouble("settings.anonymous.cost", 0.0);
                        boolean returnAnon = config.getBoolean("settings.anonymous.return-on-expire", true);

                        for (Contribution c : bounty.getContributions()) {
                            double refund = c.getAmount();
                            if (c.isAnonymous() && returnAnon) {
                                refund += anonCost;
                            }
                            plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(c.getAssigner()), refund);
                        }

                        String targetName = Bukkit.getOfflinePlayer(bounty.getTarget()).getName();
                        String expireMsg = config.getString("messages.bounty-expired", "")
                                .replace("{target}", targetName != null ? targetName : "Unknown");
                        Bukkit.broadcastMessage(ColorUtils.colorize(prefix + expireMsg));
                        iterator.remove();
                        menuNeedsUpdate = true;
                    }
                    continue;
                }

                boolean isTargetOnline = Bukkit.getPlayer(bounty.getTarget()) != null;
                if (!pauseOffline || isTargetOnline) {
                    bounty.decrementTime(1000L);
                }

                if (bounty.getTimeLeft() <= 0) {
                    bounty.setHunter(null, 0);
                    setCooldown(hunterId);
                    Player hunter = Bukkit.getPlayer(hunterId);
                    if (hunter != null) {
                        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(bounty.getTarget());
                        String msg = config.getString("messages.bounty-failed", "")
                                .replace("{target}", targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown");
                        hunter.sendMessage(ColorUtils.parse(prefix + msg));
                        playSound(hunter, "fail");
                    }
                    menuNeedsUpdate = true;
                    continue;
                }

                if (actionBarEnabled) {
                    Player hunter = Bukkit.getPlayer(hunterId);
                    if (hunter != null) {
                        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(bounty.getTarget());
                        String name = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
                        String msg = actionBarMsg
                                .replace("{name}", name)
                                .replace("{money}", ColorUtils.formatMoney(bounty.getTotalBank()))
                                .replace("{symbol}", symbol);
                        hunter.sendActionBar(ColorUtils.parse(msg));
                    }
                }
            }
            if (menuNeedsUpdate) {
                plugin.getMenuManager().updateMenus();
            }
        }, 20L, 20L);
    }

    public void playSound(Player player, String type) {
        String soundData = plugin.getConfig().getString("sounds." + type);
        if (soundData != null && !soundData.isEmpty() && !soundData.equalsIgnoreCase("none")) {
            String[] split = soundData.split(";");
            try {
                Sound sound = Sound.valueOf(split[0].toUpperCase());
                float volume = split.length > 1 ? Float.parseFloat(split[1]) : 1.0f;
                float pitch = split.length > 2 ? Float.parseFloat(split[2]) : 1.0f;
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (Exception ignored) {
            }
        }
    }

    public String formatTime(long millis) {
        if (millis <= 0) return "0с.";
        long totalSecs = millis / 1000;
        long days = totalSecs / 86400;
        long hours = (totalSecs % 86400) / 3600;
        long mins = (totalSecs % 3600) / 60;
        long secs = totalSecs % 60;

        String format = plugin.getConfig().getString("settings.time-format", "%d%д. %h%ч. %m%м. %s%с.");

        if (days == 0) {
            format = format.replace("%d%д. ", "").replace("%d%д.", "").replace("%d%d. ", "");
        } else {
            format = format.replace("%d%", String.valueOf(days));
        }

        return format.replace("%h%", String.valueOf(hours))
                .replace("%m%", String.valueOf(mins))
                .replace("%s%", String.valueOf(secs)).trim();
    }
}