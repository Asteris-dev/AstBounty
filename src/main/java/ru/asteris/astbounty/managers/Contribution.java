package ru.asteris.astbounty.managers;

import java.util.UUID;

public class Contribution {

    private final UUID assigner;
    private final double amount;
    private final int killerPercentage;
    private final boolean anonymous;

    public Contribution(UUID assigner, double amount, int killerPercentage, boolean anonymous) {
        this.assigner = assigner;
        this.amount = amount;
        this.killerPercentage = killerPercentage;
        this.anonymous = anonymous;
    }

    public UUID getAssigner() {
        return assigner;
    }

    public double getAmount() {
        return amount;
    }

    public int getKillerPercentage() {
        return killerPercentage;
    }

    public boolean isAnonymous() {
        return anonymous;
    }
}