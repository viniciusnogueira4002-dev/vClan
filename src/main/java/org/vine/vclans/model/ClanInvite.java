package org.vine.vclans.model;

import java.time.Instant;
import java.util.UUID;

public class ClanInvite {

    private final UUID clanId;
    private final UUID targetUuid;
    private final UUID senderUuid;
    private final Instant createdAt;
    private final Instant expiresAt;

    public ClanInvite(UUID clanId, UUID targetUuid, UUID senderUuid, int timeoutSeconds) {
        this.clanId = clanId;
        this.targetUuid = targetUuid;
        this.senderUuid = senderUuid;
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plusSeconds(timeoutSeconds);
    }

    public UUID getClanId() {
        return clanId;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public UUID getSenderUuid() {
        return senderUuid;
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

    public long getRemainingSeconds() {
        long remaining = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }
}

