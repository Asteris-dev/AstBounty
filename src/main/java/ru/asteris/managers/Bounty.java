package ru.asteris.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Bounty {

    private final UUID target;
    private final List<Contribution> contributions;
    private UUID hunter;
    private long timeLeft;
    private long globalExpireTime;

    public Bounty(UUID target) {
        this.target = target;
        this.contributions = new ArrayList<>();
    }

    public UUID getTarget() {
        return target;
    }

    public List<Contribution> getContributions() {
        return contributions;
    }

    public void addContribution(Contribution contribution) {
        this.contributions.add(contribution);
    }

    public UUID getHunter() {
        return hunter;
    }

    public void setHunter(UUID hunter, long timeLeft) {
        this.hunter = hunter;
        this.timeLeft = timeLeft;
    }

    public long getTimeLeft() {
        return timeLeft;
    }

    public void setTimeLeft(long timeLeft) {
        this.timeLeft = timeLeft;
    }

    public void decrementTime(long amount) {
        this.timeLeft -= amount;
    }

    public long getGlobalExpireTime() {
        return globalExpireTime;
    }

    public void setGlobalExpireTime(long globalExpireTime) {
        this.globalExpireTime = globalExpireTime;
    }

    public double getTotalBank() {
        return contributions.stream().mapToDouble(Contribution::getAmount).sum();
    }

    public int getAverageKillerPercent() {
        if (contributions.isEmpty()) return 0;
        return (int) contributions.stream().mapToInt(Contribution::getKillerPercentage).average().orElse(0.0);
    }
}