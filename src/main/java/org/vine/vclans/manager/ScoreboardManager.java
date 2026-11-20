package org.vine.vclans.manager;

import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class ScoreboardManager {

    private final VClans plugin;

    public ScoreboardManager(VClans plugin) {
        this.plugin = plugin;
    }

    public void updatePlayer(Player target) {
        if (!plugin.getConfig().getBoolean("nametag.enabled", true)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("nametag.use-vanilla-teams", true)) {
            return;
        }
        if (target == null || !target.isOnline()) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            var sm = Bukkit.getScoreboardManager();
            if (sm == null) return;
            Scoreboard mainBoard = sm.getMainScoreboard();

            // 1. Aplicar na mainScoreboard
            applyTeamToBoard(mainBoard, target);

            // 2. Aplicar nos scoreboards de todos os jogadores online (caso usem scoreboard per-player)
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                Scoreboard viewerBoard = viewer.getScoreboard();
                if (viewerBoard != null && viewerBoard != mainBoard) {
                    applyTeamToBoard(viewerBoard, target);
                }
            }
        });
    }

    public void syncAllForPlayer(Player viewer) {
        if (!plugin.getConfig().getBoolean("nametag.enabled", true)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("nametag.use-vanilla-teams", true)) {
            return;
        }
        if (viewer == null || !viewer.isOnline()) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            Scoreboard viewerBoard = viewer.getScoreboard();
            for (Player target : Bukkit.getOnlinePlayers()) {
                applyTeamToBoard(viewerBoard, target);
            }
        });
    }

    private void applyTeamToBoard(Scoreboard board, Player player) {
        if (board == null || player == null) return;

        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        String prefix = "";
        if (clan != null) {
            String prefixFormat = plugin.getConfig().getString("nametag.prefix-format", "&8[{color}{tag}&8] &r")
                    .replace("{tag}", clan.getTag())
                    .replace("{color}", clan.getTagColor());
            prefix = ColorUtil.color(prefixFormat);
            if (prefix.length() > 64) {
                prefix = prefix.substring(0, 64);
            }
        }

        String teamName = "c_" + player.getName();
        if (teamName.length() > 16) {
            teamName = teamName.substring(0, 16);
        }

        try {
            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
            }
            if (!team.hasEntry(player.getName())) {
                team.addEntry(player.getName());
            }
            team.setPrefix(prefix);
        } catch (Exception ignored) {}
    }

    public void removePlayer(Player player) {
        if (!plugin.getConfig().getBoolean("nametag.use-vanilla-teams", true)) {
            return;
        }

        try {
            var sm = Bukkit.getScoreboardManager();
            if (sm == null) return;
            Scoreboard mainBoard = sm.getMainScoreboard();
            String teamName = "c_" + player.getName();
            if (teamName.length() > 16) {
                teamName = teamName.substring(0, 16);
            }

            Team team = mainBoard.getTeam(teamName);
            if (team != null) {
                team.unregister();
            }

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                Scoreboard viewerBoard = viewer.getScoreboard();
                if (viewerBoard != null && viewerBoard != mainBoard) {
                    Team vTeam = viewerBoard.getTeam(teamName);
                    if (vTeam != null) {
                        vTeam.unregister();
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public void cleanup() {
        if (!plugin.getConfig().getBoolean("nametag.use-vanilla-teams", true)) {
            return;
        }

        try {
            var sm = Bukkit.getScoreboardManager();
            if (sm == null) return;
            Scoreboard mainBoard = sm.getMainScoreboard();
            for (Team team : new java.util.ArrayList<>(mainBoard.getTeams())) {
                if (team.getName().startsWith("c_")) {
                    team.unregister();
                }
            }

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                Scoreboard viewerBoard = viewer.getScoreboard();
                if (viewerBoard != null && viewerBoard != mainBoard) {
                    for (Team team : new java.util.ArrayList<>(viewerBoard.getTeams())) {
                        if (team.getName().startsWith("c_")) {
                            team.unregister();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }
}

