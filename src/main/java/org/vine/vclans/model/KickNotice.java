package org.vine.vclans.model;

import java.time.Instant;

public class KickNotice {

    private final String clanName;
    private final String clanTag;
    private final String tagColor;
    private final String actorName;
    private final Instant kickedAt;

    public KickNotice(String clanName, String clanTag, String tagColor, String actorName, Instant kickedAt) {
        this.clanName = clanName;
        this.clanTag = clanTag;
        this.tagColor = tagColor;
        this.actorName = actorName;
        this.kickedAt = kickedAt != null ? kickedAt : Instant.now();
    }

    public String getClanName() {
        return clanName;
    }

    public String getClanTag() {
        return clanTag;
    }

    public String getTagColor() {
        return tagColor;
    }

    public String getActorName() {
        return actorName;
    }

    public Instant getKickedAt() {
        return kickedAt;
    }
}
