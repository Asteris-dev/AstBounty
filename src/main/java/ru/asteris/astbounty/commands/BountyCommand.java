package ru.asteris.astbounty.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.asteris.astbounty.Main;
import ru.asteris.astbounty.managers.Bounty;
import ru.asteris.astbounty.managers.Contribution;
import ru.asteris.astbounty.managers.PendingBounty;
import ru.asteris.astlib.commands.AbstractCommand;
import ru.asteris.astlib.utils.ColorUtils;

import java.util.ArrayList;
import java.util.List;

public class BountyCommand extends AbstractCommand {

    public BountyCommand() {
        super("bounty");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return;
        Player player = (Player) sender;
        Main plugin = Main.getInstance();

        String prefix = plugin.getConfig().getString("messages.prefix");
        String symbol = plugin.getConfig().getString("settings.currency-symbol");
        String itemRewardText = plugin.getConfig().getString("settings.item-reward-text", "&b[Предмет]");

        if (!player.hasPermission("astbounty.use")) {
            player.sendMessage(ColorUtils.colorize(player, prefix + plugin.getConfig().getString("messages.no-permission")));
            return;
        }

        if (plugin.getBountyListener().getPending(player.getUniqueId()) != null) {
            player.sendMessage(ColorUtils.colorize(player, prefix + plugin.getConfig().getString("messages.pending-setup")));
            plugin.getBountyManager().playSound(player, "error");
            return;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("menu")) {
            if (plugin.getBountyManager().hasActiveBounty(player.getUniqueId())) {
                for (Bounty b : plugin.getBountyManager().getAllBounties()) {
                    if (player.getUniqueId().equals(b.getHunter())) {
                        OfflinePlayer target = Bukkit.getOfflinePlayer(b.getTarget());
                        String statusStr = target.isOnline() ?
                                plugin.getConfig().getString("messages.status-online") :
                                plugin.getConfig().getString("messages.status-offline");

                        String moneyStr = b.getTotalBank() > 0 ? ColorUtils.formatMoney(b.getTotalBank()) + symbol : "";
                        String itemStr = b.getContributions().stream().anyMatch(c -> c.getItem() != null) ? itemRewardText : "";

                        String msg = plugin.getConfig().getString("messages.current-bounty-status")
                                .replace("{target}", target.getName() != null ? target.getName() : "Unknown")
                                .replace("{status}", statusStr)
                                .replace("{money}", moneyStr)
                                .replace("{item}", itemStr)
                                .replace("{time}", plugin.getBountyManager().formatTime(b.getTimeLeft()))
                                .replace("{symbol}", "");

                        msg = msg.replace("  ", " ").replace(" | | ", " | ");

                        player.sendMessage(ColorUtils.colorize(player, prefix + msg));
                        plugin.getBountyManager().playSound(player, "click");
                        return;
                    }
                }
            }

            if (plugin.getBountyManager().getAllBounties().isEmpty()) {
                player.sendMessage(ColorUtils.colorize(player, prefix + plugin.getConfig().getString("messages.menu-empty")));
                plugin.getBountyManager().playSound(player, "error");
                return;
            }
            plugin.getMenuManager().openMainMenu(player);
            return;
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
                player.sendMessage(ColorUtils.colorize(player, prefix + plugin.getConfig().getString("messages.player-offline")));
                plugin.getBountyManager().playSound(player, "error");
                return;
            }

            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(ColorUtils.colorize(player, prefix + plugin.getConfig().getString("messages.self-bounty")));
                plugin.getBountyManager().playSound(player, "error");
                return;
            }

            double amount = 0;
            ItemStack itemReward = null;

            if (args[1].equalsIgnoreCase("item")) {
                itemReward = player.getInventory().getItemInMainHand().clone();
                if (itemReward == null || itemReward.getType() == org.bukkit.Material.AIR) {
                    String msg = plugin.getConfig().getString("messages.must-hold-item", "&cДля заказа предметом нужно взять его в руку!");
                    player.sendMessage(ColorUtils.colorize(player, prefix + msg));
                    plugin.getBountyManager().playSound(player, "error");
                    return;
                }
            } else {
                try {
                    amount = Double.parseDouble(args[1]);
                    if (amount <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    player.sendMessage(ColorUtils.colorize(player, prefix + plugin.getConfig().getString("messages.invalid-amount")));
                    plugin.getBountyManager().playSound(player, "error");
                    return;
                }

                int minAmount = plugin.getConfig().getInt("settings.min-bounty-amount");
                if (amount < minAmount) {
                    String msg = plugin.getConfig().getString("messages.min-amount").replace("{money}", ColorUtils.formatMoney(minAmount)).replace("{symbol}", symbol);
                    player.sendMessage(ColorUtils.colorize(player, prefix + msg));
                    plugin.getBountyManager().playSound(player, "error");
                    return;
                }

                if (ru.asteris.astlib.Main.getInstance().getVaultManager().getBalance(player) < amount) {
                    player.sendMessage(ColorUtils.colorize(player, prefix + plugin.getConfig().getString("messages.not-enough-money")));
                    plugin.getBountyManager().playSound(player, "error");
                    return;
                }
            }

            if (plugin.getBountyManager().hasPlaceCooldown(player.getUniqueId(), target.getUniqueId())) {
                String timeLeft = plugin.getBountyManager().formatTime(plugin.getBountyManager().getPlaceCooldownLeft(player.getUniqueId(), target.getUniqueId()));
                String msg = plugin.getConfig().getString("messages.place-cooldown").replace("{time}", timeLeft);
                player.sendMessage(ColorUtils.colorize(player, prefix + msg));
                plugin.getBountyManager().playSound(player, "error");
                return;
            }

            int maxPerAssigner = plugin.getConfig().getInt("settings.max-bounties-per-assigner");
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
                String msg = plugin.getConfig().getString("messages.max-assigner-bounties").replace("{max}", String.valueOf(maxPerAssigner));
                player.sendMessage(ColorUtils.colorize(player, prefix + msg));
                plugin.getBountyManager().playSound(player, "error");
                return;
            }

            Bounty bounty = plugin.getBountyManager().getBounty(target.getUniqueId());
            int limit = plugin.getConfig().getInt("settings.max-bounties-per-target");
            if (bounty != null && bounty.getContributions().size() >= limit) {
                player.sendMessage(ColorUtils.colorize(player, prefix + plugin.getConfig().getString("messages.target-limit")));
                plugin.getBountyManager().playSound(player, "error");
                return;
            }

            PendingBounty pending = new PendingBounty(target.getUniqueId(), amount, itemReward);
            if (itemReward != null) {
                player.getInventory().setItemInMainHand(null);
            }
            plugin.getBountyListener().addPending(player.getUniqueId(), pending);
            plugin.getMenuManager().openPercentageMenu(player, target, pending);
            return;
        }

        List<String> helpMessages = plugin.getConfig().getStringList("messages.help");
        if (helpMessages.isEmpty()) {
            player.sendMessage(ColorUtils.colorize(player, prefix + plugin.getConfig().getString("messages.usage", "&cИспользуйте: /bounty menu")));
        } else {
            for (String line : helpMessages) {
                player.sendMessage(ColorUtils.colorize(player, line));
            }
        }
        plugin.getBountyManager().playSound(player, "error");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partialName = args[0].toLowerCase();
            if ("menu".startsWith(partialName)) {
                completions.add("menu");
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partialName)) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 2) {
            if (!args[0].equalsIgnoreCase("menu") && "item".startsWith(args[1].toLowerCase())) {
                completions.add("item");
            }
        }
        return completions;
    }
}