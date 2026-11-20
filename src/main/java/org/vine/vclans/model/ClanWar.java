package org.vine.vclans.model;

import java.time.Instant;
import java.util.UUID;

public class ClanWar {

    public enum WarState {
        ACTIVE,
        FINISHED_KILLS,
        FINISHED_TIME,
        SURRENDERED
    }

    private final UUID warId;
    private final UUID clanAId;
    private final UUID clanBId;
    private int killsA;
    private int killsB;
    private final int targetKills;
    private final Instant startedAt;
    private final Instant endsAt;
    private WarState state;
    private UUID winnerClanId;

    public ClanWar(UUID warId, UUID clanAId, UUID clanBId, int targetKills, int durationHours) {
        this.warId = warId;
        this.clanAId = clanAId;
        this.clanBId = clanBId;
        this.killsA = 0;
        this.killsB = 0;
        this.targetKills = targetKills;
        this.startedAt = Instant.now();
        this.endsAt = this.startedAt.plusSeconds((long) durationHours * 3600);
        this.state = WarState.ACTIVE;
        this.winnerClanId = null;
    }

    public ClanWar(UUID warId, UUID clanAId, UUID clanBId, int killsA, int killsB, int targetKills, Instant startedAt, Instant endsAt, WarState state, UUID winnerClanId) {
        this.warId = warId;
        this.clanAId = clanAId;
        this.clanBId = clanBId;
        this.killsA = killsA;
        this.killsB = killsB;
        this.targetKills = targetKills;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
        this.state = state;
        this.winnerClanId = winnerClanId;
    }

    public UUID getWarId() {
        return warId;
    }

    public UUID getClanAId() {
        return clanAId;
    }

    public UUID getClanBId() {
        return clanBId;
    }

    public int getKillsA() {
        return killsA;
    }

    public int getKillsB() {
        return killsB;
    }

    public int getTargetKills() {
        return targetKills;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public WarState getState() {
        return state;
    }

    public UUID getWinnerClanId() {
        return winnerClanId;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(endsAt);
    }

    public boolean addKill(UUID killerClanId) {
        if (killerClanId.equals(clanAId)) {
            killsA++;
            if (killsA >= targetKills) {
                state = WarState.FINISHED_KILLS;
                winnerClanId = clanAId;
                return true;
            }
        } else if (killerClanId.equals(clanBId)) {
            killsB++;
            if (killsB >= targetKills) {
                state = WarState.FINISHED_KILLS;
                winnerClanId = clanBId;
                return true;
            }
        }
        return false;
    }

    public void surrender(UUID surrenderingClanId) {
        this.state = WarState.SURRENDERED;
        if (surrenderingClanId.equals(clanAId)) {
            this.winnerClanId = clanBId;
        } else {
            this.winnerClanId = clanAId;
        }
    }

    public void checkTimeExpiration() {
        if (isExpired() && state == WarState.ACTIVE) {
            state = WarState.FINISHED_TIME;
            if (killsA > killsB) {
                winnerClanId = clanAId;
            } else if (killsB > killsA) {
                winnerClanId = clanBId;
            } else {
                winnerClanId = null; // Empate
            }
        }
    }
}

