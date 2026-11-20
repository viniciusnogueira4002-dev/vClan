package org.vine.vclans.model;

public class ClanStats {

    private int points;
    private int kills;
    private int deaths;
    private int duelWins;
    private int duelLosses;
    private int warWins;
    private int warLosses;

    public ClanStats() {
        this.points = 0;
        this.kills = 0;
        this.deaths = 0;
        this.duelWins = 0;
        this.duelLosses = 0;
        this.warWins = 0;
        this.warLosses = 0;
    }

    public ClanStats(int points, int kills, int deaths, int duelWins, int duelLosses, int warWins, int warLosses) {
        this.points = points;
        this.kills = kills;
        this.deaths = deaths;
        this.duelWins = duelWins;
        this.duelLosses = duelLosses;
        this.warWins = warWins;
        this.warLosses = warLosses;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = Math.max(0, points);
    }

    public void addPoints(int amount) {
        this.points = Math.max(0, this.points + amount);
    }

    public void removePoints(int amount) {
        this.points = Math.max(0, this.points - amount);
    }

    public int getKills() {
        return kills;
    }

    public void addKill() {
        this.kills++;
    }

    public void setKills(int kills) {
        this.kills = Math.max(0, kills);
    }

    public int getDeaths() {
        return deaths;
    }

    public void addDeath() {
        this.deaths++;
    }

    public void setDeaths(int deaths) {
        this.deaths = Math.max(0, deaths);
    }

    public int getDuelWins() {
        return duelWins;
    }

    public void addDuelWin() {
        this.duelWins++;
    }

    public void setDuelWins(int duelWins) {
        this.duelWins = Math.max(0, duelWins);
    }

    public int getDuelLosses() {
        return duelLosses;
    }

    public void addDuelLoss() {
        this.duelLosses++;
    }

    public void setDuelLosses(int duelLosses) {
        this.duelLosses = Math.max(0, duelLosses);
    }

    public int getWarWins() {
        return warWins;
    }

    public void addWarWin() {
        this.warWins++;
    }

    public void setWarWins(int warWins) {
        this.warWins = Math.max(0, warWins);
    }

    public int getWarLosses() {
        return warLosses;
    }

    public void addWarLoss() {
        this.warLosses++;
    }

    public void setWarLosses(int warLosses) {
        this.warLosses = Math.max(0, warLosses);
    }

    public double getKdr() {
        if (deaths == 0) return kills;
        return (double) Math.round(((double) kills / (double) deaths) * 100.0) / 100.0;
    }

    public String getFormattedKdr() {
        return String.format(java.util.Locale.US, "%.2f", getKdr());
    }
}

