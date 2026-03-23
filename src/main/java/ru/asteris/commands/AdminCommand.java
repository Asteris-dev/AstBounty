package ru.asteris.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import ru.asteris.Main;
import ru.asteris.utils.ColorUtils;

public class AdminCommand implements CommandExecutor {

    private final Main plugin;

    public AdminCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("astbounty.admin")) {
            sender.sendMessage(ColorUtils.colorize(plugin.getConfig().getString("messages.prefix", "") + plugin.getConfig().getString("messages.no-permission", "")));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.getMenuManager().loadConfig();
            sender.sendMessage(ColorUtils.colorize(plugin.getConfig().getString("messages.prefix", "") + plugin.getConfig().getString("messages.reloaded", "")));
            return true;
        }

        sender.sendMessage(ColorUtils.colorize(plugin.getConfig().getString("messages.prefix", "") + "&fПерезагрузка: /astbounty reload"));
        return true;
    }
}