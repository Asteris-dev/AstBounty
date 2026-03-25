package ru.asteris.astbounty.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import ru.asteris.astbounty.Main;
import ru.asteris.astlib.utils.AstGui;
import ru.asteris.astlib.utils.ColorUtils;
import ru.asteris.astlib.utils.ItemBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MenuManager {

    private final Main plugin;
    private FileConfiguration menuConfig;

    public MenuManager(Main plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "menu.yml");
        if (!file.exists()) {
            plugin.saveResource("menu.yml", false);
        }
        menuConfig = YamlConfiguration.loadConfiguration(file);
    }

    public void updateMenus() {
        String title = menuConfig.getString("main-menu.title", "");
        int size = menuConfig.getInt("main-menu.size", 54);

        for (Player p : Bukkit.getOnlinePlayers()) {
            Inventory top = p.getOpenInventory().getTopInventory();
            if (top != null && top.getSize() == size && p.getOpenInventory().getTitle().equals(ColorUtils.colorize(p, title))) {
                openMainMenu(p);
            }
        }
    }

    public void openPercentageMenu(Player player, OfflinePlayer target, PendingBounty pending) {
        String title = menuConfig.getString("percentage-menu.title", "");
        int size = menuConfig.getInt("percentage-menu.size", 27);
        AstGui gui = new AstGui(size, ColorUtils.colorize(player, title));

        ConfigurationSection items = menuConfig.getConfigurationSection("percentage-menu.items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                int slot = items.getInt(key + ".slot");
                Material mat = Material.matchMaterial(items.getString(key + ".material", "PAPER").toUpperCase());
                if (mat == null) mat = Material.PAPER;

                String name = items.getString(key + ".name", "");
                List<String> lore = items.getStringList(key + ".lore");
                List<String> coloredLore = new ArrayList<>();

                double anonCost = plugin.getConfig().getDouble("settings.anonymous.cost", 0.0);
                String statusOn = menuConfig.getString("percentage-menu.status-on");
                String statusOff = menuConfig.getString("percentage-menu.status-off");
                String status = pending.isAnonymous() ? statusOn : statusOff;
                String targetName = target.getName() != null ? target.getName() : "Unknown";
                String symbol = plugin.getConfig().getString("settings.currency-symbol");

                String formattedAmount = ColorUtils.formatMoney(pending.getAmount());
                String formattedCost = ColorUtils.formatMoney(anonCost);

                String moneyFormat = formattedAmount;
                if (pending.isAnonymous()) {
                    moneyFormat = menuConfig.getString("percentage-menu.anon-money-format")
                            .replace("{money}", formattedAmount)
                            .replace("{symbol}", symbol)
                            .replace("{cost}", formattedCost);
                }

                String percentFormat = menuConfig.getString("percentage-menu.percent-format")
                        .replace("{percentage}", String.valueOf(pending.getPercentage()))
                        .replace("{hunter_percentage}", String.valueOf(100 - pending.getPercentage()));

                for (String line : lore) {
                    coloredLore.add(ColorUtils.colorize(player, line
                            .replace("{target}", targetName)
                            .replace("{money}", moneyFormat)
                            .replace("{percentage}%", percentFormat)
                            .replace("{cost}", formattedCost)
                            .replace("{status}", status)
                            .replace("{symbol}", symbol)
                    ));
                }

                ItemBuilder builder = new ItemBuilder(mat)
                        .setName(ColorUtils.colorize(player, name))
                        .setLore(coloredLore);

                if (key.equals("anonymous-toggle") && plugin.getConfig().getBoolean("settings.anonymous.enabled")) {
                    gui.setItem(slot, builder.build(), event -> {
                        pending.setAnonymous(!pending.isAnonymous());
                        openPercentageMenu(player, target, pending);
                        plugin.getBountyManager().playSound(player, "click");
                    });
                } else if (key.equals("add-1")) {
                    gui.setItem(slot, builder.build(), event -> { pending.addPercentage(1); openPercentageMenu(player, target, pending); plugin.getBountyManager().playSound(player, "click"); });
                } else if (key.equals("add-10")) {
                    gui.setItem(slot, builder.build(), event -> { pending.addPercentage(10); openPercentageMenu(player, target, pending); plugin.getBountyManager().playSound(player, "click"); });
                } else if (key.equals("sub-1")) {
                    gui.setItem(slot, builder.build(), event -> { pending.addPercentage(-1); openPercentageMenu(player, target, pending); plugin.getBountyManager().playSound(player, "click"); });
                } else if (key.equals("sub-10")) {
                    gui.setItem(slot, builder.build(), event -> { pending.addPercentage(-10); openPercentageMenu(player, target, pending); plugin.getBountyManager().playSound(player, "click"); });
                } else if (key.equals("confirm")) {
                    gui.setItem(slot, builder.build(), event -> {
                        plugin.getBountyListener().removePending(player.getUniqueId());
                        double amount = pending.getAmount();
                        double totalCost = amount + (pending.isAnonymous() ? anonCost : 0.0);
                        String prefix = plugin.getConfig().getString("messages.prefix");

                        if (ru.asteris.astlib.Main.getInstance().getVaultManager().getBalance(player) >= totalCost) {
                            ru.asteris.astlib.Main.getInstance().getVaultManager().withdraw(player, totalCost);
                            plugin.getBountyManager().addBounty(pending.getTarget(), player.getUniqueId(), amount, pending.getPercentage(), pending.isAnonymous());
                            plugin.getStatsManager().addPlaced(player.getUniqueId());
                            plugin.getStatsManager().addTarget(pending.getTarget());
                            plugin.getBountyManager().setPlaceCooldown(player.getUniqueId(), pending.getTarget());

                            String moneyText = ColorUtils.formatMoney(amount);
                            if (pending.isAnonymous()) {
                                moneyText = plugin.getConfig().getString("settings.anonymous.money-format").replace("{money}", moneyText).replace("{cost}", ColorUtils.formatMoney(anonCost));
                            }
                            String msg = plugin.getConfig().getString("messages.bounty-placed")
                                    .replace("{target}", targetName)
                                    .replace("{money}", moneyText)
                                    .replace("{symbol}", symbol);
                            player.sendMessage(ColorUtils.colorize(player, prefix + msg));
                            plugin.getBountyManager().playSound(player, "place");

                            Player targetPlayer = target.getPlayer();
                            if (targetPlayer != null) {
                                String notifyMsg = plugin.getConfig().getString("messages.target-notified-placed").replace("{money}", ColorUtils.formatMoney(amount)).replace("{symbol}", symbol);
                                targetPlayer.sendMessage(ColorUtils.colorize(targetPlayer, prefix + notifyMsg));
                            }
                            player.closeInventory();
                            updateMenus();
                        } else {
                            player.sendMessage(ColorUtils.colorize(player, prefix + plugin.getConfig().getString("messages.not-enough-money")));
                            plugin.getBountyManager().playSound(player, "error");
                            player.closeInventory();
                        }
                    });
                } else {
                    gui.setItem(slot, builder.build(), event -> {});
                }
            }
        }
        player.openInventory(gui.getInventory());
    }

    public void openMainMenu(Player player) {
        String title = menuConfig.getString("main-menu.title", "");
        int size = menuConfig.getInt("main-menu.size", 54);
        AstGui gui = new AstGui(size, ColorUtils.colorize(player, title));

        ConfigurationSection config = menuConfig.getConfigurationSection("main-menu.items");
        if (config == null) return;

        String headName = config.getString("bounty-head.name", "");
        List<String> headLore = config.getStringList("bounty-head.lore");
        String assignerFormat = config.getString("assigner-format", "");
        String buyoutFormat = config.getString("buyout-format", "");
        boolean buyoutEnabled = plugin.getConfig().getBoolean("settings.buyout.enabled");
        String symbol = plugin.getConfig().getString("settings.currency-symbol");
        String prefix = plugin.getConfig().getString("messages.prefix");

        int slot = 0;
        for (Bounty bounty : plugin.getBountyManager().getAllBounties()) {
            if (slot >= 36) break;
            if (bounty.getHunter() != null) continue;

            OfflinePlayer target = Bukkit.getOfflinePlayer(bounty.getTarget());
            List<String> coloredLore = new ArrayList<>();
            for (String line : headLore) {
                if (line.contains("{assigners}")) {
                    for (Contribution c : bounty.getContributions()) {
                        OfflinePlayer assigner = Bukkit.getOfflinePlayer(c.getAssigner());
                        String assignerName = c.isAnonymous() ? plugin.getConfig().getString("settings.anonymous.name") : (assigner.getName() != null ? assigner.getName() : "Unknown");
                        String formattedAssigner = assignerFormat
                                .replace("{name}", assignerName)
                                .replace("{money}", ColorUtils.formatMoney(c.getAmount()))
                                .replace("{killer_percent}", String.valueOf(c.getKillerPercentage()))
                                .replace("{symbol}", symbol);
                        coloredLore.add(ColorUtils.colorize(player, formattedAssigner));
                    }
                } else if (line.contains("{buyout_lore}")) {
                    if (buyoutEnabled && player.getUniqueId().equals(bounty.getTarget())) {
                        coloredLore.add(ColorUtils.colorize(player, buyoutFormat
                                .replace("{money}", ColorUtils.formatMoney(plugin.getBountyManager().calculateBuyoutCost(bounty)))
                                .replace("{symbol}", symbol)
                        ));
                    }
                } else {
                    coloredLore.add(ColorUtils.colorize(player, line
                            .replace("{total_bank}", ColorUtils.formatMoney(bounty.getTotalBank()))
                            .replace("{avg_killer_percent}", String.valueOf(bounty.getAverageKillerPercent()))
                            .replace("{take_cost}", ColorUtils.formatMoney(plugin.getBountyManager().calculateTakeCost(bounty)))
                            .replace("{global_expire}", plugin.getBountyManager().formatTime(bounty.getGlobalExpireTime() - System.currentTimeMillis()))
                            .replace("{symbol}", symbol)
                    ));
                }
            }
            ItemBuilder headBuilder = new ItemBuilder(Material.PLAYER_HEAD)
                    .setSkullOwner(target)
                    .setName(ColorUtils.colorize(player, headName.replace("{target}", target.getName() != null ? target.getName() : "Unknown")))
                    .setLore(coloredLore);

            gui.setItem(slot++, headBuilder.build(), event -> {
                if (player.getUniqueId().equals(target.getUniqueId())) {
                    if (event.getClick() == ClickType.RIGHT && buyoutEnabled) {
                        double buyoutCost = plugin.getBountyManager().calculateBuyoutCost(bounty);
                        if (ru.asteris.astlib.Main.getInstance().getVaultManager().getBalance(player) >= buyoutCost) {
                            ru.asteris.astlib.Main.getInstance().getVaultManager().withdraw(player, buyoutCost);

                            if (plugin.getConfig().getBoolean("settings.buyout.return-money")) {
                                for (Contribution c : bounty.getContributions()) {
                                    ru.asteris.astlib.Main.getInstance().getVaultManager().deposit(Bukkit.getOfflinePlayer(c.getAssigner()), c.getAmount());
                                    OfflinePlayer assigner = Bukkit.getOfflinePlayer(c.getAssigner());
                                    if (assigner.isOnline() && assigner.getPlayer() != null) {
                                        String notifyAssigner = plugin.getConfig().getString("messages.buyout-notified-assigner").replace("{target}", target.getName() != null ? target.getName() : "Unknown");
                                        assigner.getPlayer().sendMessage(ColorUtils.colorize(assigner.getPlayer(), prefix + notifyAssigner));
                                    }
                                }
                            }
                            plugin.getBountyManager().removeBounty(target.getUniqueId());
                            String msg = plugin.getConfig().getString("messages.buyout-success").replace("{money}", ColorUtils.formatMoney(buyoutCost)).replace("{symbol}", symbol);
                            player.sendMessage(ColorUtils.colorize(player, prefix + msg));
                            plugin.getBountyManager().playSound(player, "buyout");
                            player.closeInventory();
                            updateMenus();
                        } else {
                            player.sendMessage(ColorUtils.colorize(player, prefix + plugin.getConfig().getString("messages.not-enough-money")));
                            plugin.getBountyManager().playSound(player, "error");
                        }
                    } else {
                        player.sendMessage(ColorUtils.colorize(player, prefix + plugin.getConfig().getString("messages.cannot-hunt-self")));
                        plugin.getBountyManager().playSound(player, "error");
                    }
                    return;
                }

                if (plugin.getBountyManager().hasActiveBounty(player.getUniqueId())) {
                    player.sendMessage(ColorUtils.colorize(player, prefix + plugin.getConfig().getString("messages.already-hunting")));
                    plugin.getBountyManager().playSound(player, "error");
                    return;
                }
                if (plugin.getBountyManager().hasCooldown(player.getUniqueId())) {
                    String msg = plugin.getConfig().getString("messages.on-cooldown").replace("{time}", plugin.getBountyManager().formatTime(plugin.getBountyManager().getCooldownLeft(player.getUniqueId())));
                    player.sendMessage(ColorUtils.colorize(player, prefix + msg));
                    plugin.getBountyManager().playSound(player, "error");
                    return;
                }
                for (Contribution c : bounty.getContributions()) {
                    if (c.getAssigner().equals(player.getUniqueId())) {
                        player.sendMessage(ColorUtils.colorize(player, prefix + plugin.getConfig().getString("messages.cannot-hunt-invested")));
                        plugin.getBountyManager().playSound(player, "error");
                        return;
                    }
                }

                double takeCost = plugin.getBountyManager().calculateTakeCost(bounty);
                if (ru.asteris.astlib.Main.getInstance().getVaultManager().getBalance(player) >= takeCost) {
                    ru.asteris.astlib.Main.getInstance().getVaultManager().withdraw(player, takeCost);
                    long timeLimit = plugin.getConfig().getLong("settings.time-limit-seconds") * 1000L;
                    bounty.setHunter(player.getUniqueId(), timeLimit);
                    String msg = plugin.getConfig().getString("messages.bounty-accepted").replace("{target}", target.getName() != null ? target.getName() : "Unknown").replace("{time}", plugin.getBountyManager().formatTime(timeLimit));
                    player.sendMessage(ColorUtils.colorize(player, prefix + msg));
                    plugin.getBountyManager().playSound(player, "take");

                    Player targetPlayer = Bukkit.getPlayer(target.getUniqueId());
                    if (targetPlayer != null) {
                        String notifyMsg = plugin.getConfig().getString("messages.target-notified-taken").replace("{hunter}", player.getName());
                        targetPlayer.sendMessage(ColorUtils.colorize(targetPlayer, prefix + notifyMsg));
                    }
                    player.closeInventory();
                    updateMenus();
                } else {
                    player.sendMessage(ColorUtils.colorize(player, prefix + plugin.getConfig().getString("messages.not-enough-money")));
                    plugin.getBountyManager().playSound(player, "error");
                }
            });
        }

        ConfigurationSection decor = config.getConfigurationSection("decor");
        if (decor != null) {
            Material mat = Material.matchMaterial(decor.getString("material", "BLACK_STAINED_GLASS_PANE"));
            ItemBuilder glass = new ItemBuilder(mat != null ? mat : Material.BLACK_STAINED_GLASS_PANE).setName(ColorUtils.colorize(player, decor.getString("name", " ")));
            for (Integer s : decor.getIntegerList("slots")) {
                if (s < size) gui.setItem(s, glass.build(), event -> {});
            }
        }

        ConfigurationSection info = config.getConfigurationSection("info-item");
        if (info != null) {
            Material mat = Material.matchMaterial(info.getString("material", "BOOK"));
            List<String> infoLore = new ArrayList<>();
            for (String line : info.getStringList("lore")) infoLore.add(ColorUtils.colorize(player, line));
            ItemBuilder book = new ItemBuilder(mat != null ? mat : Material.BOOK)
                    .setName(ColorUtils.colorize(player, info.getString("name", "")))
                    .setLore(infoLore);
            int infoSlot = info.getInt("slot", 48);
            if (infoSlot < size) gui.setItem(infoSlot, book.build(), event -> {});
        }

        ConfigurationSection stats = config.getConfigurationSection("stats-item");
        if (stats != null) {
            Material mat = Material.matchMaterial(stats.getString("material", "PAPER"));
            String currentTarget = plugin.getConfig().getString("messages.no-target");
            for (Bounty b : plugin.getBountyManager().getAllBounties()) {
                if (player.getUniqueId().equals(b.getHunter())) {
                    OfflinePlayer t = Bukkit.getOfflinePlayer(b.getTarget());
                    currentTarget = t.getName() != null ? t.getName() : "Unknown";
                    break;
                }
            }
            List<String> statsLore = new ArrayList<>();
            for (String line : stats.getStringList("lore")) {
                statsLore.add(ColorUtils.colorize(player, line
                        .replace("{placed_total}", String.valueOf(plugin.getStatsManager().getPlacedTotal(player.getUniqueId())))
                        .replace("{target_total}", String.valueOf(plugin.getStatsManager().getTargetTotal(player.getUniqueId())))
                        .replace("{completed_total}", String.valueOf(plugin.getStatsManager().getCompletedTotal(player.getUniqueId())))
                        .replace("{current_target}", currentTarget)
                ));
            }
            ItemBuilder paper = new ItemBuilder(mat != null ? mat : Material.PAPER)
                    .setName(ColorUtils.colorize(player, stats.getString("name", "")))
                    .setLore(statsLore);
            int statsSlot = stats.getInt("slot", 50);
            if (statsSlot < size) gui.setItem(statsSlot, paper.build(), event -> {});
        }

        player.openInventory(gui.getInventory());
    }

    public FileConfiguration getMenuConfig() {
        return menuConfig;
    }
}