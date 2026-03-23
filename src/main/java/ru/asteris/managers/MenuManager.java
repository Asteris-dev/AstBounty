package ru.asteris.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import ru.asteris.Main;
import ru.asteris.utils.BountyHolder;
import ru.asteris.utils.ColorUtils;

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
        for (Player p : Bukkit.getOnlinePlayers()) {
            Inventory top = p.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder() instanceof BountyHolder) {
                BountyHolder holder = (BountyHolder) top.getHolder();
                if (holder.getMenuType().equals("main")) {
                    openMainMenu(p, plugin.getBountyManager());
                }
            }
        }
    }

    public void openPercentageMenu(Player player, OfflinePlayer target, PendingBounty pending) {
        String title = ColorUtils.colorize(menuConfig.getString("percentage-menu.title", ""));
        int size = menuConfig.getInt("percentage-menu.size", 27);

        Inventory inv;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof BountyHolder &&
                ((BountyHolder) player.getOpenInventory().getTopInventory().getHolder()).getMenuType().equals("percentage")) {
            inv = player.getOpenInventory().getTopInventory();
            inv.clear();
        } else {
            inv = Bukkit.createInventory(new BountyHolder("percentage"), size, title);
        }

        ConfigurationSection items = menuConfig.getConfigurationSection("percentage-menu.items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                int slot = items.getInt(key + ".slot");
                Material mat = Material.matchMaterial(items.getString(key + ".material", "PAPER").toUpperCase());
                if (mat == null) mat = Material.PAPER;

                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();

                if (meta != null) {
                    meta.setDisplayName(ColorUtils.colorize(items.getString(key + ".name", "")));
                    List<String> lore = items.getStringList(key + ".lore");
                    List<String> coloredLore = new ArrayList<>();

                    double anonCost = plugin.getConfig().getDouble("settings.anonymous.cost", 0.0);

                    String statusOn = menuConfig.getString("percentage-menu.status-on", "&aВключено");
                    String statusOff = menuConfig.getString("percentage-menu.status-off", "&cВыключено");
                    String status = pending.isAnonymous() ? statusOn : statusOff;

                    String targetName = target.getName() != null ? target.getName() : "Unknown";
                    String symbol = plugin.getConfig().getString("settings.currency-symbol", "$");

                    String formattedAmount = ColorUtils.formatMoney(pending.getAmount());
                    String formattedCost = ColorUtils.formatMoney(anonCost);

                    String moneyFormat = formattedAmount;
                    if (pending.isAnonymous()) {
                        moneyFormat = menuConfig.getString("percentage-menu.anon-money-format", "{money} {symbol} &a+ {cost}")
                                .replace("{money}", formattedAmount)
                                .replace("{symbol}", symbol)
                                .replace("{cost}", formattedCost);
                    }

                    String percentFormat = menuConfig.getString("percentage-menu.percent-format", "{percentage}% &8(&7Вам: &a{hunter_percentage}%&8)")
                            .replace("{percentage}", String.valueOf(pending.getPercentage()))
                            .replace("{hunter_percentage}", String.valueOf(100 - pending.getPercentage()));

                    for (String line : lore) {
                        coloredLore.add(ColorUtils.colorize(line
                                .replace("{target}", targetName)
                                .replace("{money}", moneyFormat)
                                .replace("{percentage}%", percentFormat)
                                .replace("{cost}", formattedCost)
                                .replace("{status}", status)
                                .replace("{symbol}", symbol)
                        ));
                    }
                    meta.setLore(coloredLore);
                    item.setItemMeta(meta);
                }
                inv.setItem(slot, item);
            }
        }

        if (inv != player.getOpenInventory().getTopInventory()) {
            player.openInventory(inv);
        }
    }

    public void openMainMenu(Player player, BountyManager bountyManager) {
        String title = ColorUtils.colorize(menuConfig.getString("main-menu.title", ""));
        int size = menuConfig.getInt("main-menu.size", 54);

        Inventory inv;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof BountyHolder &&
                ((BountyHolder) player.getOpenInventory().getTopInventory().getHolder()).getMenuType().equals("main")) {
            inv = player.getOpenInventory().getTopInventory();
            inv.clear();
        } else {
            inv = Bukkit.createInventory(new BountyHolder("main"), size, title);
        }

        ConfigurationSection config = menuConfig.getConfigurationSection("main-menu.items");
        if (config == null) return;

        String headName = config.getString("bounty-head.name", "");
        List<String> headLore = config.getStringList("bounty-head.lore");
        String assignerFormat = config.getString("assigner-format", "");
        String buyoutFormat = config.getString("buyout-format", "");
        boolean buyoutEnabled = plugin.getConfig().getBoolean("settings.buyout.enabled", true);
        String symbol = plugin.getConfig().getString("settings.currency-symbol", "$");

        int slot = 0;
        for (Bounty bounty : bountyManager.getAllBounties()) {
            if (slot >= 36) break;
            if (bounty.getHunter() != null) continue;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(bounty.getTarget());
                meta.setOwningPlayer(target);
                meta.setDisplayName(ColorUtils.colorize(headName.replace("{target}", target.getName() != null ? target.getName() : "Unknown")));

                List<String> coloredLore = new ArrayList<>();
                for (String line : headLore) {
                    if (line.contains("{assigners}")) {
                        for (Contribution c : bounty.getContributions()) {
                            OfflinePlayer assigner = Bukkit.getOfflinePlayer(c.getAssigner());
                            String assignerName = c.isAnonymous() ? plugin.getConfig().getString("settings.anonymous.name", "&8Аноним") : (assigner.getName() != null ? assigner.getName() : "Unknown");
                            String formattedAssigner = assignerFormat
                                    .replace("{name}", assignerName)
                                    .replace("{money}", ColorUtils.formatMoney(c.getAmount()))
                                    .replace("{killer_percent}", String.valueOf(c.getKillerPercentage()))
                                    .replace("{symbol}", symbol);
                            coloredLore.add(ColorUtils.colorize(formattedAssigner));
                        }
                    } else if (line.contains("{buyout_lore}")) {
                        if (buyoutEnabled && player.getUniqueId().equals(bounty.getTarget())) {
                            double buyoutCost = bountyManager.calculateBuyoutCost(bounty);
                            coloredLore.add(ColorUtils.colorize(buyoutFormat
                                    .replace("{money}", ColorUtils.formatMoney(buyoutCost))
                                    .replace("{symbol}", symbol)
                            ));
                        }
                    } else {
                        String globalExpireStr = bountyManager.formatTime(bounty.getGlobalExpireTime() - System.currentTimeMillis());
                        coloredLore.add(ColorUtils.colorize(line
                                        .replace("{total_bank}", ColorUtils.formatMoney(bounty.getTotalBank())))
                                .replace("{avg_killer_percent}", String.valueOf(bounty.getAverageKillerPercent()))
                                .replace("{take_cost}", ColorUtils.formatMoney(bountyManager.calculateTakeCost(bounty)))
                                .replace("{global_expire}", globalExpireStr)
                                .replace("{symbol}", symbol)
                        );
                    }
                }
                meta.setLore(coloredLore);
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        ConfigurationSection decor = config.getConfigurationSection("decor");
        if (decor != null) {
            Material mat = Material.matchMaterial(decor.getString("material", "BLACK_STAINED_GLASS_PANE"));
            ItemStack glass = new ItemStack(mat != null ? mat : Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta meta = glass.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtils.colorize(decor.getString("name", " ")));
                glass.setItemMeta(meta);
            }
            for (Integer s : decor.getIntegerList("slots")) {
                if (s < size) inv.setItem(s, glass);
            }
        }

        ConfigurationSection info = config.getConfigurationSection("info-item");
        if (info != null) {
            Material mat = Material.matchMaterial(info.getString("material", "BOOK"));
            ItemStack book = new ItemStack(mat != null ? mat : Material.BOOK);
            ItemMeta meta = book.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtils.colorize(info.getString("name", "")));
                List<String> lore = new ArrayList<>();
                for (String line : info.getStringList("lore")) lore.add(ColorUtils.colorize(line));
                meta.setLore(lore);
                book.setItemMeta(meta);
            }
            int infoSlot = info.getInt("slot", 48);
            if (infoSlot < size) inv.setItem(infoSlot, book);
        }

        ConfigurationSection stats = config.getConfigurationSection("stats-item");
        if (stats != null) {
            Material mat = Material.matchMaterial(stats.getString("material", "PAPER"));
            ItemStack paper = new ItemStack(mat != null ? mat : Material.PAPER);
            ItemMeta meta = paper.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtils.colorize(stats.getString("name", "")));
                List<String> lore = new ArrayList<>();

                String currentTarget = plugin.getConfig().getString("messages.no-target", "Нет цели");
                for (Bounty b : bountyManager.getAllBounties()) {
                    if (player.getUniqueId().equals(b.getHunter())) {
                        OfflinePlayer t = Bukkit.getOfflinePlayer(b.getTarget());
                        currentTarget = t.getName() != null ? t.getName() : "Unknown";
                        break;
                    }
                }

                for (String line : stats.getStringList("lore")) {
                    lore.add(ColorUtils.colorize(line
                            .replace("{placed_total}", String.valueOf(plugin.getStatsManager().getPlacedTotal(player.getUniqueId())))
                            .replace("{target_total}", String.valueOf(plugin.getStatsManager().getTargetTotal(player.getUniqueId())))
                            .replace("{completed_total}", String.valueOf(plugin.getStatsManager().getCompletedTotal(player.getUniqueId())))
                            .replace("{current_target}", currentTarget)
                    ));
                }
                meta.setLore(lore);
                paper.setItemMeta(meta);
            }
            int statsSlot = stats.getInt("slot", 50);
            if (statsSlot < size) inv.setItem(statsSlot, paper);
        }

        if (inv != player.getOpenInventory().getTopInventory()) {
            player.openInventory(inv);
        }
    }

    public FileConfiguration getMenuConfig() {
        return menuConfig;
    }
}