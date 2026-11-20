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

    public ClanMember(UUID uuid, String lastKnownName, ClanRole role, Instant joinedAt, Instant lastSeen) {
        this.uuid = uuid;
        this.lastKnownName = lastKnownName;
        this.role = role;
        this.joinedAt = joinedAt;
        this.lastSeen = lastSeen;
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
}

