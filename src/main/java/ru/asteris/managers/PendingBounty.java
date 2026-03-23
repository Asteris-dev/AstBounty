package ru.asteris.managers;

import java.util.UUID;

public class PendingBounty {

    private final UUID target;
    private final double amount;
    private boolean anonymous;
    private int percentage;

    public PendingBounty(UUID target, double amount) {
        this.target = target;
        this.amount = amount;
        this.anonymous = false;
        this.percentage = 50;
    }

    public UUID getTarget() {
        return target;
    }

    public double getAmount() {
        return amount;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public void setAnonymous(boolean anonymous) {
        this.anonymous = anonymous;
    }

    public int getPercentage() {
        return percentage;
    }

    public void addPercentage(int add) {
        this.percentage += add;
        if (this.percentage > 100) this.percentage = 100;
        if (this.percentage < 0) this.percentage = 0;
    }
}