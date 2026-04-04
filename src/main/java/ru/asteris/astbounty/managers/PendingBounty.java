package ru.asteris.astbounty.managers;

import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public class PendingBounty {

    private final UUID target;
    private final double amount;
    private final ItemStack item;
    private boolean anonymous;
    private int percentage;
    private boolean updating;

    public PendingBounty(UUID target, double amount, ItemStack item) {
        this.target = target;
        this.amount = amount;
        this.item = item;
        this.anonymous = false;
        this.percentage = 50;
        this.updating = false;
    }

    public UUID getTarget() { return target; }
    public double getAmount() { return amount; }
    public ItemStack getItem() { return item; }
    public boolean isAnonymous() { return anonymous; }
    public void setAnonymous(boolean anonymous) { this.anonymous = anonymous; }
    public int getPercentage() { return percentage; }

    public boolean isUpdating() { return updating; }
    public void setUpdating(boolean updating) { this.updating = updating; }

    public void addPercentage(int add) {
        this.percentage += add;
        if (this.percentage > 100) this.percentage = 100;
        if (this.percentage < 0) this.percentage = 0;
    }
}