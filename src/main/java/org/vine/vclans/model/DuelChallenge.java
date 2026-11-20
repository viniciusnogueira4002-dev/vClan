package org.vine.vclans.model;

import java.time.Instant;
import java.util.UUID;

public class DuelChallenge {

    private final UUID challengerClanId;
    private final UUID targetClanId;
    private final String arenaName;
    private final UUID senderUuid;
    private final int teamSize;
    private final Instant createdAt;
    private final Instant expiresAt;

    public DuelChallenge(UUID challengerClanId, UUID targetClanId, String arenaName, UUID senderUuid, int timeoutSeconds, int teamSize) {
        this.challengerClanId = challengerClanId;
        this.targetClanId = targetClanId;
        this.arenaName = arenaName;
        this.senderUuid = senderUuid;
        this.teamSize = teamSize;
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plusSeconds(timeoutSeconds);
    }

    public UUID getChallengerClanId() {
        return challengerClanId;
    }

    public UUID getTargetClanId() {
        return targetClanId;
    }

    public String getArenaName() {
        return arenaName;
    }

    public UUID getSenderUuid() {
        return senderUuid;
    }

    public int getTeamSize() {
        return teamSize;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
