package org.vine.vclans.model;

import java.time.Instant;
import java.util.UUID;

public class ClanLogEntry {

    private final Instant timestamp;
    private final UUID actorUuid;
    private final String action;

    public ClanLogEntry(Instant timestamp, UUID actorUuid, String action) {
        this.timestamp = timestamp;
        this.actorUuid = actorUuid;
        this.action = action;
    }

    public ClanLogEntry(UUID actorUuid, String action) {
        this(Instant.now(), actorUuid, action);
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public UUID getActorUuid() {
        return actorUuid;
    }

    public String getAction() {
        return action;
    }
}

