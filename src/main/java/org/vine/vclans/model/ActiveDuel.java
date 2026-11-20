package org.vine.vclans.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ActiveDuel {

    public enum DuelState {
        COUNTDOWN,
        FIGHTING,
        FINISHED
    }

    private final Arena arena;
    private final UUID clanAId;
    private final UUID clanBId;
    private final Set<UUID> teamA;
    private final Set<UUID> teamB;
    private final Set<UUID> aliveA;
    private final Set<UUID> aliveB;
    private final int teamSize;
    private DuelState state;
    private int countdown;
    private int matchTimer;

    public ActiveDuel(Arena arena, UUID clanAId, UUID clanBId, Set<UUID> teamA, Set<UUID> teamB, int countdownSeconds, int maxDurationSeconds) {
        this.arena = arena;
        this.clanAId = clanAId;
        this.clanBId = clanBId;
        this.teamA = new HashSet<>(teamA);
        this.teamB = new HashSet<>(teamB);
        this.aliveA = new HashSet<>(teamA);
        this.aliveB = new HashSet<>(teamB);
        this.teamSize = teamA.size();
        this.state = DuelState.COUNTDOWN;
        this.countdown = countdownSeconds;
        this.matchTimer = maxDurationSeconds;
        this.arena.setInUse(true);
    }

    public Arena getArena() {
        return arena;
    }

    public UUID getClanAId() {
        return clanAId;
    }

    public UUID getClanBId() {
        return clanBId;
    }

    public Set<UUID> getTeamA() {
        return teamA;
    }

    public Set<UUID> getTeamB() {
        return teamB;
    }

    public Set<UUID> getAliveA() {
        return aliveA;
    }

    public Set<UUID> getAliveB() {
        return aliveB;
    }

    public int getTeamSize() {
        return teamSize;
    }

    public DuelState getState() {
        return state;
    }

    public void setState(DuelState state) {
        this.state = state;
    }

    public int getCountdown() {
        return countdown;
    }

    public void decrementCountdown() {
        this.countdown--;
    }

    public int getMatchTimer() {
        return matchTimer;
    }

    public void decrementMatchTimer() {
        this.matchTimer--;
    }

    public boolean isParticipant(UUID uuid) {
        return teamA.contains(uuid) || teamB.contains(uuid);
    }

    public boolean isAlive(UUID uuid) {
        return aliveA.contains(uuid) || aliveB.contains(uuid);
    }

    public boolean handleElimination(UUID uuid) {
        aliveA.remove(uuid);
        aliveB.remove(uuid);
        return isFinished();
    }

    public boolean isFinished() {
        return aliveA.isEmpty() || aliveB.isEmpty() || matchTimer <= 0;
    }

    public UUID getWinnerClanId() {
        if (aliveA.isEmpty() && !aliveB.isEmpty()) {
            return clanBId;
        } else if (aliveB.isEmpty() && !aliveA.isEmpty()) {
            return clanAId;
        } else if (aliveA.size() > aliveB.size()) {
            return clanAId;
        } else if (aliveB.size() > aliveA.size()) {
            return clanBId;
        }
        return null; // Empate
    }

    public void cleanup() {
        this.arena.setInUse(false);
        this.state = DuelState.FINISHED;
    }
}

