package ru.asteris.astbounty.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.asteris.astbounty.Main;
import ru.asteris.astlib.utils.ColorUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BountyListener implements Listener {

    private final Main plugin;
    private final Map<UUID, PendingBounty> pendingBounties = new HashMap<>();

    public BountyListener(Main plugin) {
        this.plugin = plugin;
    }

    public void addPending(UUID player, PendingBounty pending) {
        pendingBounties.put(player, pending);
    }

    public PendingBounty getPending(UUID player) {
        return pendingBounties.get(player);
    }

    public void removePending(UUID player) {
        pendingBounties.remove(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        plugin.getStatsManager().loadUser(joined.getUniqueId());

        Bounty bounty = plugin.getBountyManager().getBounty(joined.getUniqueId());
        if (bounty != null && bounty.getHunter() != null) {
            Player hunter = Bukkit.getPlayer(bounty.getHunter());
            if (hunter != null) {
                String msg = plugin.getConfig().getString("messages.target-joined", "").replace("{target}", joined.getName());
                hunter.sendMessage(ColorUtils.colorize(hunter, plugin.getConfig().getString("messages.prefix", "") + msg));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player quit = event.getPlayer();
        plugin.getStatsManager().unloadUser(quit.getUniqueId());
        pendingBounties.remove(quit.getUniqueId());

        Bounty bounty = plugin.getBountyManager().getBounty(quit.getUniqueId());
        if (bounty != null && bounty.getHunter() != null) {
            Player hunter = Bukkit.getPlayer(bounty.getHunter());
            if (hunter != null) {
                String msg = plugin.getConfig().getString("messages.target-left", "").replace("{target}", quit.getName());
                hunter.sendMessage(ColorUtils.colorize(hunter, plugin.getConfig().getString("messages.prefix", "") + msg));
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        if (pendingBounties.containsKey(player.getUniqueId())) {
            String menuTitle = ColorUtils.colorize(player, plugin.getMenuManager().getMenuConfig().getString("percentage-menu.title", ""));
            if (event.getView().getTitle().equals(menuTitle)) {
                pendingBounties.remove(player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player target = event.getEntity();
        Player killer = target.getKiller();
        if (killer != null) {
            plugin.getBountyManager().completeBounty(target.getUniqueId(), killer);
        }
    }
}