package ru.asteris;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import ru.asteris.commands.AdminCommand;
import ru.asteris.commands.BountyCommand;
import ru.asteris.managers.BountyListener;
import ru.asteris.managers.BountyManager;
import ru.asteris.managers.DatabaseManager;
import ru.asteris.managers.MenuManager;
import ru.asteris.managers.StatsManager;
import ru.asteris.utils.BountyExpansion;

public class Main extends JavaPlugin {

    private static Main instance;
    private Economy economy;
    private DatabaseManager databaseManager;
    private BountyManager bountyManager;
    private MenuManager menuManager;
    private StatsManager statsManager;
    private BountyListener bountyListener;

    @Override
    public void onEnable() {
        instance = this;

        if (!setupEconomy()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

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

        getCommand("bounty").setExecutor(new BountyCommand(this));
        getCommand("astbounty").setExecutor(new AdminCommand(this));
        getServer().getPluginManager().registerEvents(bountyListener, this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BountyExpansion(this).register();
        }
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

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public static Main getInstance() {
        return instance;
    }

    public Economy getEconomy() {
        return economy;
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