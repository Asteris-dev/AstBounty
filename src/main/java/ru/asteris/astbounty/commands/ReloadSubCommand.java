package ru.asteris.astbounty.commands;

import org.bukkit.command.CommandSender;
import ru.asteris.astbounty.Main;
import ru.asteris.astlib.commands.SubCommand;
import ru.asteris.astlib.utils.ColorUtils;

public class ReloadSubCommand extends SubCommand {

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getPermission() {
        return "astbounty.admin";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Main plugin = Main.getInstance();
        String prefix = plugin.getConfig().getString("messages.prefix");

        plugin.reloadConfig();
        plugin.getMenuManager().loadConfig();

        sender.sendMessage(ColorUtils.colorize(prefix + plugin.getConfig().getString("messages.reloaded")));
    }
}