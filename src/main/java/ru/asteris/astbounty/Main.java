package ru.asteris.astbounty;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.asteris.astbounty.commands.AstBountyCommand;
import ru.asteris.astbounty.commands.BountyCommand;
import ru.asteris.astbounty.managers.BountyListener;
import ru.asteris.astbounty.managers.BountyManager;
import ru.asteris.astbounty.managers.DatabaseManager;
import ru.asteris.astbounty.managers.MenuManager;
import ru.asteris.astbounty.managers.StatsManager;
import ru.asteris.astbounty.managers.Bounty;
import ru.asteris.astlib.utils.AstExpansion;

public class Main extends JavaPlugin {

    private static Main instance;
    private DatabaseManager databaseManager;
    private BountyManager bountyManager;
    private MenuManager menuManager;
    private StatsManager statsManager;
    private BountyListener bountyListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        databaseManager = new DatabaseManager(this);
        statsManager = new StatsManager(this, databaseManager);
        menuManager = new MenuManager(this);
        bountyManager = new BountyManager(this, databaseManager);
        bountyListener = new BountyListener(this);

        bountyManager.loadBounties();

        for (Player player : Bukkit.getOnlinePlayers()) {
            statsManager.loadUser(player.getUniqueId());
        }

        getServer().getPluginManager().registerEvents(bountyListener, this);

        new BountyCommand();
        new AstBountyCommand();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            registerPlaceholders();
        } else {
            getLogger().warning("PlaceholderAPI не найден! Плейсхолдеры AstBounty отключены.");
        }
    }

    private void registerPlaceholders() {
        new AstExpansion("astbounty", "Asteris", getDescription().getVersion(), (player, params) -> {
            if (player == null) return "";
            if (params.equalsIgnoreCase("placed_total")) return String.valueOf(statsManager.getPlacedTotal(player.getUniqueId()));
            if (params.equalsIgnoreCase("target_total")) return String.valueOf(statsManager.getTargetTotal(player.getUniqueId()));
            if (params.equalsIgnoreCase("completed_total")) return String.valueOf(statsManager.getCompletedTotal(player.getUniqueId()));
            if (params.equalsIgnoreCase("current_target")) {
                for (Bounty bounty : bountyManager.getAllBounties()) {
                    if (player.getUniqueId().equals(bounty.getHunter())) {
                        OfflinePlayer target = Bukkit.getOfflinePlayer(bounty.getTarget());
                        return target.getName() != null ? target.getName() : getConfig().getString("messages.no-target", "Нет цели");
                    }
                }
                return getConfig().getString("messages.no-target", "Нет цели");
            }
            return null;
        }).register();
    }

    @Override
    public void onDisable() {
        if (bountyManager != null) {
            bountyManager.saveBounties();
        }
        if (statsManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                statsManager.saveUserSync(player.getUniqueId());
            }
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        instance = null;
    }

    public static Main getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public BountyManager getBountyManager() {
        return bountyManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public BountyListener getBountyListener() {
        return bountyListener;
    }
}