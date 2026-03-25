package ru.asteris.astbounty.commands;

import org.bukkit.command.CommandSender;
import ru.asteris.astbounty.Main;
import ru.asteris.astlib.commands.AbstractCommand;
import ru.asteris.astlib.utils.ColorUtils;

public class AstBountyCommand extends AbstractCommand {

    public AstBountyCommand() {
        super("astbounty");
        addSubCommand(new ReloadSubCommand());
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Main plugin = Main.getInstance();
        String prefix = plugin.getConfig().getString("messages.prefix");

        if (!sender.hasPermission("astbounty.admin")) {
            sender.sendMessage(ColorUtils.colorize(prefix + plugin.getConfig().getString("messages.no-permission")));
            return;
        }
        sender.sendMessage(ColorUtils.colorize(prefix + "&cИспользование: /astbounty reload"));
    }
}