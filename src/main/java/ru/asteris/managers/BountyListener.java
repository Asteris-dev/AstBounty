package ru.asteris.managers;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import ru.asteris.Main;
import ru.asteris.utils.BountyHolder;
import ru.asteris.utils.ColorUtils;

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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        plugin.getStatsManager().loadUser(joined.getUniqueId());

        Bounty bounty = plugin.getBountyManager().getBounty(joined.getUniqueId());
        if (bounty != null && bounty.getHunter() != null) {
            Player hunter = Bukkit.getPlayer(bounty.getHunter());
            if (hunter != null) {
                String msg = plugin.getConfig().getString("messages.target-joined", "")
                        .replace("{target}", joined.getName());
                hunter.sendMessage(ColorUtils.parse(plugin.getConfig().getString("messages.prefix", "") + msg));
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
                String msg = plugin.getConfig().getString("messages.target-left", "")
                        .replace("{target}", quit.getName());
                hunter.sendMessage(ColorUtils.parse(plugin.getConfig().getString("messages.prefix", "") + msg));
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getClickedInventory() == null) return;
        if (!(event.getInventory().getHolder() instanceof BountyHolder)) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        BountyHolder holder = (BountyHolder) event.getInventory().getHolder();
        String menuType = holder.getMenuType();

        MenuManager menuManager = plugin.getMenuManager();
        FileConfiguration menuConfig = menuManager.getMenuConfig();
        FileConfiguration config = plugin.getConfig();
        String prefix = config.getString("messages.prefix", "");
        String symbol = config.getString("settings.currency-symbol", "$");

        if (menuType.equals("percentage")) {
            if (event.getCurrentItem() == null) return;

            int slot = event.getRawSlot();
            PendingBounty pending = pendingBounties.get(player.getUniqueId());
            if (pending == null) return;

            OfflinePlayer target = Bukkit.getOfflinePlayer(pending.getTarget());

            int anonSlot = menuConfig.getInt("percentage-menu.items.anonymous-toggle.slot", 20);
            int add1Slot = menuConfig.getInt("percentage-menu.items.add-1.slot", 5);
            int add10Slot = menuConfig.getInt("percentage-menu.items.add-10.slot", 6);
            int sub1Slot = menuConfig.getInt("percentage-menu.items.sub-1.slot", 3);
            int sub10Slot = menuConfig.getInt("percentage-menu.items.sub-10.slot", 2);
            int confirmSlot = menuConfig.getInt("percentage-menu.items.confirm.slot", 24);

            if (slot == anonSlot && config.getBoolean("settings.anonymous.enabled", true)) {
                pending.setAnonymous(!pending.isAnonymous());
                menuManager.openPercentageMenu(player, target, pending);
                plugin.getBountyManager().playSound(player, "click");
                return;
            }

            if (slot == add1Slot) { pending.addPercentage(1); menuManager.openPercentageMenu(player, target, pending); plugin.getBountyManager().playSound(player, "click"); return; }
            if (slot == add10Slot) { pending.addPercentage(10); menuManager.openPercentageMenu(player, target, pending); plugin.getBountyManager().playSound(player, "click"); return; }
            if (slot == sub1Slot) { pending.addPercentage(-1); menuManager.openPercentageMenu(player, target, pending); plugin.getBountyManager().playSound(player, "click"); return; }
            if (slot == sub10Slot) { pending.addPercentage(-10); menuManager.openPercentageMenu(player, target, pending); plugin.getBountyManager().playSound(player, "click"); return; }

            if (slot == confirmSlot) {
                pendingBounties.remove(player.getUniqueId());
                double amount = pending.getAmount();
                double anonCost = config.getDouble("settings.anonymous.cost", 0.0);
                double totalCost = amount + (pending.isAnonymous() ? anonCost : 0.0);

                if (plugin.getEconomy().getBalance(player) >= totalCost) {
                    plugin.getEconomy().withdrawPlayer(player, totalCost);
                    plugin.getBountyManager().addBounty(pending.getTarget(), player.getUniqueId(), amount, pending.getPercentage(), pending.isAnonymous());
                    plugin.getStatsManager().addPlaced(player.getUniqueId());
                    plugin.getStatsManager().addTarget(pending.getTarget());
                    plugin.getBountyManager().setPlaceCooldown(player.getUniqueId(), pending.getTarget());
                    plugin.getMenuManager().updateMenus();

                    String moneyText = ColorUtils.formatMoney(amount);
                    if (pending.isAnonymous()) {
                        String anonFormat = config.getString("settings.anonymous.money-format", "{money} &a+ {cost}");
                        moneyText = anonFormat.replace("{money}", moneyText).replace("{cost}", ColorUtils.formatMoney(anonCost));
                    }

                    String msg = config.getString("messages.bounty-placed", "")
                            .replace("{target}", target.getName() != null ? target.getName() : "Unknown")
                            .replace("{money}", moneyText)
                            .replace("{symbol}", symbol);
                    player.sendMessage(ColorUtils.parse(prefix + msg));
                    plugin.getBountyManager().playSound(player, "place");

                    Player targetPlayer = target.getPlayer();
                    if (targetPlayer != null) {
                        String notifyMsg = config.getString("messages.target-notified-placed", "")
                                .replace("{money}", ColorUtils.formatMoney(amount))
                                .replace("{symbol}", symbol);
                        targetPlayer.sendMessage(ColorUtils.parse(prefix + notifyMsg));
                    }
                    player.closeInventory();
                } else {
                    player.sendMessage(ColorUtils.parse(prefix + config.getString("messages.not-enough-money", "")));
                    plugin.getBountyManager().playSound(player, "error");
                    player.closeInventory();
                }
            }
        } else if (menuType.equals("main")) {
            if (event.getCurrentItem() == null) return;

            ItemStack item = event.getCurrentItem();
            if (item.getItemMeta() instanceof SkullMeta) {
                SkullMeta meta = (SkullMeta) item.getItemMeta();
                if (meta.getOwningPlayer() != null) {
                    OfflinePlayer target = meta.getOwningPlayer();
                    BountyManager bountyManager = plugin.getBountyManager();
                    Bounty bounty = bountyManager.getBounty(target.getUniqueId());

                    if (bounty != null && bounty.getHunter() == null) {

                        if (player.getUniqueId().equals(target.getUniqueId())) {
                            if (event.getClick() == ClickType.RIGHT && config.getBoolean("settings.buyout.enabled", true)) {
                                double buyoutCost = bountyManager.calculateBuyoutCost(bounty);
                                if (plugin.getEconomy().getBalance(player) >= buyoutCost) {
                                    plugin.getEconomy().withdrawPlayer(player, buyoutCost);

                                    if (config.getBoolean("settings.buyout.return-money", true)) {
                                        for (Contribution c : bounty.getContributions()) {
                                            plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(c.getAssigner()), c.getAmount());
                                            OfflinePlayer assigner = Bukkit.getOfflinePlayer(c.getAssigner());
                                            if (assigner.isOnline() && assigner.getPlayer() != null) {
                                                String notifyAssigner = config.getString("messages.buyout-notified-assigner", "")
                                                        .replace("{target}", target.getName() != null ? target.getName() : "Unknown");
                                                assigner.getPlayer().sendMessage(ColorUtils.parse(prefix + notifyAssigner));
                                            }
                                        }
                                    }

                                    bountyManager.removeBounty(target.getUniqueId());
                                    plugin.getMenuManager().updateMenus();
                                    String msg = config.getString("messages.buyout-success", "")
                                            .replace("{money}", ColorUtils.formatMoney(buyoutCost))
                                            .replace("{symbol}", symbol);
                                    player.sendMessage(ColorUtils.parse(prefix + msg));
                                    bountyManager.playSound(player, "buyout");
                                    player.closeInventory();
                                } else {
                                    player.sendMessage(ColorUtils.parse(prefix + config.getString("messages.not-enough-money", "")));
                                    bountyManager.playSound(player, "error");
                                }
                                return;
                            } else {
                                player.sendMessage(ColorUtils.parse(prefix + config.getString("messages.cannot-hunt-self", "")));
                                bountyManager.playSound(player, "error");
                                return;
                            }
                        }

                        if (bountyManager.hasActiveBounty(player.getUniqueId())) {
                            player.sendMessage(ColorUtils.parse(prefix + config.getString("messages.already-hunting", "")));
                            bountyManager.playSound(player, "error");
                            return;
                        }
                        if (bountyManager.hasCooldown(player.getUniqueId())) {
                            String msg = config.getString("messages.on-cooldown", "")
                                    .replace("{time}", bountyManager.formatTime(bountyManager.getCooldownLeft(player.getUniqueId())));
                            player.sendMessage(ColorUtils.parse(prefix + msg));
                            bountyManager.playSound(player, "error");
                            return;
                        }

                        for (Contribution c : bounty.getContributions()) {
                            if (c.getAssigner().equals(player.getUniqueId())) {
                                String errorMsg = config.getString("messages.cannot-hunt-invested", "&cВы не можете взять заказ, в который вы вложились.");
                                player.sendMessage(ColorUtils.parse(prefix + errorMsg));
                                bountyManager.playSound(player, "error");
                                return;
                            }
                        }

                        double takeCost = bountyManager.calculateTakeCost(bounty);
                        if (plugin.getEconomy().getBalance(player) >= takeCost) {
                            plugin.getEconomy().withdrawPlayer(player, takeCost);
                            long timeLimit = config.getLong("settings.time-limit-seconds") * 1000L;
                            bounty.setHunter(player.getUniqueId(), timeLimit);
                            plugin.getMenuManager().updateMenus();
                            String msg = config.getString("messages.bounty-accepted", "")
                                    .replace("{target}", target.getName() != null ? target.getName() : "Unknown")
                                    .replace("{time}", bountyManager.formatTime(timeLimit));
                            player.sendMessage(ColorUtils.parse(prefix + msg));
                            bountyManager.playSound(player, "take");

                            Player targetPlayer = Bukkit.getPlayer(target.getUniqueId());
                            if (targetPlayer != null) {
                                String notifyMsg = config.getString("messages.target-notified-taken", "").replace("{hunter}", player.getName());
                                targetPlayer.sendMessage(ColorUtils.parse(prefix + notifyMsg));
                            }
                            player.closeInventory();
                        } else {
                            player.sendMessage(ColorUtils.parse(prefix + config.getString("messages.not-enough-money", "")));
                            bountyManager.playSound(player, "error");
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BountyHolder) {
            BountyHolder holder = (BountyHolder) event.getInventory().getHolder();
            if (holder.getMenuType().equals("percentage")) {
                pendingBounties.remove(event.getPlayer().getUniqueId());
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