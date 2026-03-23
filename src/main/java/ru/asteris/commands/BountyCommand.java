package ru.asteris.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import ru.asteris.Main;
import ru.asteris.managers.Bounty;
import ru.asteris.managers.Contribution;
import ru.asteris.managers.PendingBounty;
import ru.asteris.utils.ColorUtils;

public class BountyCommand implements CommandExecutor {

    private final Main plugin;

    public BountyCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        FileConfiguration config = plugin.getConfig();
        String prefix = config.getString("messages.prefix", "");
        String symbol = config.getString("settings.currency-symbol", "$");

        if (!player.hasPermission("astbounty.use")) {
            player.sendMessage(ColorUtils.parse(prefix + config.getString("messages.no-permission", "")));
            return true;
        }

        if (plugin.getBountyListener().getPending(player.getUniqueId()) != null) {
            player.sendMessage(ColorUtils.parse(prefix + config.getString("messages.pending-setup", "&cЗавершите настройку предыдущего заказа!")));
            plugin.getBountyManager().playSound(player, "error");
            return true;
        }

        if (args.length == 0) {
            if (plugin.getBountyManager().hasActiveBounty(player.getUniqueId())) {
                for (Bounty b : plugin.getBountyManager().getAllBounties()) {
                    if (player.getUniqueId().equals(b.getHunter())) {
                        OfflinePlayer target = Bukkit.getOfflinePlayer(b.getTarget());
                        String statusStr = target.isOnline() ?
                                config.getString("messages.status-online", "&aОнлайн") :
                                config.getString("messages.status-offline", "&cОффлайн");

                        String msg = config.getString("messages.current-bounty-status", "")
                                .replace("{target}", target.getName() != null ? target.getName() : "Unknown")
                                .replace("{status}", statusStr)
                                .replace("{money}", ColorUtils.formatMoney(b.getTotalBank()))
                                .replace("{time}", plugin.getBountyManager().formatTime(b.getTimeLeft()))
                                .replace("{symbol}", symbol);
                        player.sendMessage(ColorUtils.parse(prefix + msg));
                        plugin.getBountyManager().playSound(player, "click");
                        return true;
                    }
                }
            }

            if (plugin.getBountyManager().getAllBounties().isEmpty()) {
                player.sendMessage(ColorUtils.parse(prefix + config.getString("messages.menu-empty", "")));
                plugin.getBountyManager().playSound(player, "error");
                return true;
            }
            plugin.getMenuManager().openMainMenu(player, plugin.getBountyManager());
            return true;
        }

        if (args.length == 2) {
            OfflinePlayer target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
                    if (args[0].equalsIgnoreCase(p.getName())) {
                        target = p;
                        break;
                    }
                }
            }

            if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                player.sendMessage(ColorUtils.parse(prefix + config.getString("messages.player-offline", "")));
                plugin.getBountyManager().playSound(player, "error");
                return true;
            }

            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(ColorUtils.parse(prefix + config.getString("messages.self-bounty", "")));
                plugin.getBountyManager().playSound(player, "error");
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[1]);
                if (amount <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                player.sendMessage(ColorUtils.parse(prefix + config.getString("messages.invalid-amount", "")));
                plugin.getBountyManager().playSound(player, "error");
                return true;
            }

            int minAmount = config.getInt("settings.min-bounty-amount", 1000);
            if (amount < minAmount) {
                String msg = config.getString("messages.min-amount", "").replace("{money}", ColorUtils.formatMoney(minAmount)).replace("{symbol}", symbol);
                player.sendMessage(ColorUtils.parse(prefix + msg));
                plugin.getBountyManager().playSound(player, "error");
                return true;
            }

            if (plugin.getEconomy().getBalance(player) < amount) {
                player.sendMessage(ColorUtils.parse(prefix + config.getString("messages.not-enough-money", "")));
                plugin.getBountyManager().playSound(player, "error");
                return true;
            }

            if (plugin.getBountyManager().hasPlaceCooldown(player.getUniqueId(), target.getUniqueId())) {
                String timeLeft = plugin.getBountyManager().formatTime(plugin.getBountyManager().getPlaceCooldownLeft(player.getUniqueId(), target.getUniqueId()));
                String msg = config.getString("messages.place-cooldown", "").replace("{time}", timeLeft);
                player.sendMessage(ColorUtils.parse(prefix + msg));
                plugin.getBountyManager().playSound(player, "error");
                return true;
            }

            int maxPerAssigner = config.getInt("settings.max-bounties-per-assigner", 3);
            int assignerCount = 0;
            for (Bounty b : plugin.getBountyManager().getAllBounties()) {
                for (Contribution c : b.getContributions()) {
                    if (c.getAssigner().equals(player.getUniqueId())) {
                        assignerCount++;
                        break;
                    }
                }
            }
            if (assignerCount >= maxPerAssigner) {
                String msg = config.getString("messages.max-assigner-bounties", "").replace("{max}", String.valueOf(maxPerAssigner));
                player.sendMessage(ColorUtils.parse(prefix + msg));
                plugin.getBountyManager().playSound(player, "error");
                return true;
            }

            Bounty bounty = plugin.getBountyManager().getBounty(target.getUniqueId());
            int limit = config.getInt("settings.max-bounties-per-target", 5);
            if (bounty != null && bounty.getContributions().size() >= limit) {
                player.sendMessage(ColorUtils.parse(prefix + config.getString("messages.target-limit", "")));
                plugin.getBountyManager().playSound(player, "error");
                return true;
            }

            PendingBounty pending = new PendingBounty(target.getUniqueId(), amount);
            plugin.getBountyListener().addPending(player.getUniqueId(), pending);
            plugin.getMenuManager().openPercentageMenu(player, target, pending);
            return true;
        }

        player.sendMessage(ColorUtils.parse(prefix + config.getString("messages.usage", "")));
        plugin.getBountyManager().playSound(player, "error");
        return true;
    }
}