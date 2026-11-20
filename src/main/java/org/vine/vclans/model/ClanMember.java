package org.vine.vclans.model;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.UUID;

public class ClanMember {

    private final UUID uuid;
    private String lastKnownName;
    private ClanRole role;
    private final Instant joinedAt;
    private Instant lastSeen;
    private long donatedTp;
    private double donatedMoney;
    private int warPointsContributed;

    public ClanMember(UUID uuid, String lastKnownName, ClanRole role, Instant joinedAt, Instant lastSeen) {
        this(uuid, lastKnownName, role, joinedAt, lastSeen, 0L, 0.0, 0);
    }

    public ClanMember(UUID uuid, String lastKnownName, ClanRole role, Instant joinedAt, Instant lastSeen,
                      long donatedTp, double donatedMoney, int warPointsContributed) {
        this.uuid = uuid;
        this.lastKnownName = lastKnownName;
        this.role = role;
        this.joinedAt = joinedAt;
        this.lastSeen = lastSeen;
        this.donatedTp = Math.max(0L, donatedTp);
        this.donatedMoney = Double.isFinite(donatedMoney) ? Math.max(0.0, donatedMoney) : 0.0;
        this.warPointsContributed = Math.max(0, warPointsContributed);
    }

    public ClanMember(UUID uuid, String name, ClanRole role) {
        this(uuid, name, role, Instant.now(), Instant.now());
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getLastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName;
    }

    public ClanRole getRole() {
        return role;
    }

    public void setRole(ClanRole role) {
        this.role = role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void updateLastSeen() {
        this.lastSeen = Instant.now();
    }

    public boolean isOnline() {
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.isOnline();
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public long getDonatedTp() {
        return donatedTp;
    }

    public void setDonatedTp(long donatedTp) {
        this.donatedTp = Math.max(0L, donatedTp);
    }

    public void addDonatedTp(long amount) {
        if (amount <= 0L) return;
        try {
            this.donatedTp = Math.addExact(this.donatedTp, amount);
        } catch (ArithmeticException overflow) {
            this.donatedTp = Long.MAX_VALUE;
        }
    }

    public double getDonatedMoney() {
        return donatedMoney;
    }

    public void setDonatedMoney(double donatedMoney) {
        if (Double.isFinite(donatedMoney)) {
            this.donatedMoney = Math.max(0.0, donatedMoney);
        }
    }

    public void addDonatedMoney(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) return;
        this.donatedMoney = Math.round((this.donatedMoney + amount) * 100.0) / 100.0;
    }

    public int getWarPointsContributed() {
        return warPointsContributed;
    }

    public void setWarPointsContributed(int warPointsContributed) {
        this.warPointsContributed = Math.max(0, warPointsContributed);
    }

    public void addWarPointsContributed(int amount) {
        if (amount <= 0) return;
        try {
            this.warPointsContributed = Math.addExact(this.warPointsContributed, amount);
        } catch (ArithmeticException overflow) {
            this.warPointsContributed = Integer.MAX_VALUE;
        }
    }
}

