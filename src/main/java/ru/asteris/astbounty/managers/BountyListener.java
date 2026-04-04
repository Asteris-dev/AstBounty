package ru.asteris.astbounty.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import ru.asteris.astbounty.Main;
import ru.asteris.astlib.utils.ColorUtils;

import java.util.HashMap;
import java.util.List;
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

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<ItemStack> returnedItems = plugin.getDatabaseManager().getAndRemoveReturnedItems(joined.getUniqueId());
            if (!returnedItems.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (ItemStack item : returnedItems) {
                        joined.getInventory().addItem(item).values().forEach(i -> joined.getWorld().dropItem(joined.getLocation(), i));
                    }
                    joined.sendMessage(ColorUtils.colorize(joined, plugin.getConfig().getString("messages.prefix", "") + "&a✔ Предметы с истекших заказов возвращены вам в инвентарь!"));
                });
            }
        });

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

        PendingBounty pending = pendingBounties.remove(quit.getUniqueId());
        if (pending != null && pending.getItem() != null) {
            plugin.getDatabaseManager().addReturnedItem(quit.getUniqueId(), pending.getItem());
        }

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
            PendingBounty pending = pendingBounties.get(player.getUniqueId());
            if (pending.isUpdating()) return;

            String basePath = pending.getItem() != null ? "create-on-item" : "create-on-money";
            String menuTitle = ColorUtils.colorize(player, plugin.getMenuManager().getMenuConfig().getString(basePath + ".title", ""));

            if (event.getView().getTitle().equals(menuTitle)) {
                pendingBounties.remove(player.getUniqueId());
                if (pending.getItem() != null) {
                    player.getInventory().addItem(pending.getItem()).values().forEach(i -> player.getWorld().dropItem(player.getLocation(), i));
                    player.sendMessage(ColorUtils.colorize(player, plugin.getConfig().getString("messages.prefix", "") + "&eНастройка заказа отменена. Предмет возвращен."));
                }
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