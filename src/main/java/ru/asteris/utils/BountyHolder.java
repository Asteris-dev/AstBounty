package ru.asteris.utils;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class BountyHolder implements InventoryHolder {

    private final String menuType;

    public BountyHolder(String menuType) {
        this.menuType = menuType;
    }

    public String getMenuType() {
        return menuType;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}