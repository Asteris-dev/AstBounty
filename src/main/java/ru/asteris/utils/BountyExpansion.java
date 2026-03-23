package ru.asteris.utils;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import ru.asteris.Main;
import ru.asteris.managers.Bounty;

public class BountyExpansion extends PlaceholderExpansion {

    private final Main plugin;

    public BountyExpansion(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "astbounty";
    }

    @Override
    public String getAuthor() {
        return "Asteris";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";

        if (params.equalsIgnoreCase("placed_total")) {
            return String.valueOf(plugin.getStatsManager().getPlacedTotal(player.getUniqueId()));
        }

        if (params.equalsIgnoreCase("target_total")) {
            return String.valueOf(plugin.getStatsManager().getTargetTotal(player.getUniqueId()));
        }

        if (params.equalsIgnoreCase("completed_total")) {
            return String.valueOf(plugin.getStatsManager().getCompletedTotal(player.getUniqueId()));
        }

        if (params.equalsIgnoreCase("current_target")) {
            for (Bounty bounty : plugin.getBountyManager().getAllBounties()) {
                if (player.getUniqueId().equals(bounty.getHunter())) {
                    OfflinePlayer target = plugin.getServer().getOfflinePlayer(bounty.getTarget());
                    return target.getName() != null ? target.getName() : "Unknown";
                }
            }
            return plugin.getConfig().getString("messages.no-target", "Нет цели");
        }

        return null;
    }
}