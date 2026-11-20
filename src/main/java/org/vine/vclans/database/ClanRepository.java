package org.vine.vclans.database;

import org.vine.vclans.model.*;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public class ClanRepository {

    private final DatabaseManager db;

    public ClanRepository(DatabaseManager db) {
        this.db = db;
    }

    public Map<UUID, Clan> loadAllClans() {
        Map<UUID, Clan> clans = new HashMap<>();
        String sqlClans = "SELECT * FROM eclypse_clans";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlClans);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                String name = rs.getString("name");
                String tag = rs.getString("tag");
                String tagColor = rs.getString("tag_color");
                String description = rs.getString("description");
                UUID leaderUuid = UUID.fromString(rs.getString("leader_uuid"));
                boolean ff = rs.getBoolean("friendly_fire");
                boolean open = rs.getBoolean("open_join");
                boolean notif = rs.getBoolean("notifications");
                long createdEpoch = rs.getLong("created_at");

                Clan clan = new Clan(id, name, tag, tagColor, description, leaderUuid, Instant.ofEpochMilli(createdEpoch));
                clan.getSettings().setFriendlyFire(ff);
                clan.getSettings().setOpenJoin(open);
                clan.getSettings().setNotifications(notif);
                clans.put(id, clan);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Carregar Estatísticas
        String sqlStats = "SELECT * FROM eclypse_clan_stats";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlStats);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                UUID clanId = UUID.fromString(rs.getString("clan_id"));
                Clan clan = clans.get(clanId);
                if (clan != null) {
                    ClanStats stats = clan.getStats();
                    stats.setPoints(rs.getInt("points"));
                    // Carregar kills e deaths
                    int kills = rs.getInt("kills");
                    int deaths = rs.getInt("deaths");
                    for (int i = 0; i < kills; i++) stats.addKill();
                    for (int i = 0; i < deaths; i++) stats.addDeath();
                    // Duelos e Guerras
                    int dWins = rs.getInt("duel_wins");
                    int dLosses = rs.getInt("duel_losses");
                    int wWins = rs.getInt("war_wins");
                    int wLosses = rs.getInt("war_losses");
                    for (int i = 0; i < dWins; i++) stats.addDuelWin();
                    for (int i = 0; i < dLosses; i++) stats.addDuelLoss();
                    for (int i = 0; i < wWins; i++) stats.addWarWin();
                    for (int i = 0; i < wLosses; i++) stats.addWarLoss();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Carregar Membros
        String sqlMembers = "SELECT * FROM eclypse_clan_members";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlMembers);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                UUID clanId = UUID.fromString(rs.getString("clan_id"));
                Clan clan = clans.get(clanId);
                if (clan != null) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    String name = rs.getString("player_name");
                    String roleName = rs.getString("role");
                    ClanRole role;
                    try {
                        role = ClanRole.valueOf(roleName);
                    } catch (IllegalArgumentException e) {
                        role = ClanRole.MEMBER;
                    }
                    long joined = rs.getLong("joined_at");
                    long lastSeen = rs.getLong("last_seen");

                    ClanMember member = new ClanMember(playerUuid, name, role, Instant.ofEpochMilli(joined), Instant.ofEpochMilli(lastSeen));
                    clan.addMember(member);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Carregar Alianças
        String sqlAllies = "SELECT * FROM eclypse_clan_allies";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlAllies);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                UUID clanId = UUID.fromString(rs.getString("clan_id"));
                UUID allyId = UUID.fromString(rs.getString("ally_clan_id"));
                Clan clan = clans.get(clanId);
                if (clan != null) {
                    clan.getAllies().add(allyId);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return clans;
    }

    public void saveClan(Clan clan) {
        String sql = """
            INSERT INTO eclypse_clans (id, name, tag, tag_color, description, leader_uuid, friendly_fire, open_join, notifications, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                tag = excluded.tag,
                tag_color = excluded.tag_color,
                description = excluded.description,
                leader_uuid = excluded.leader_uuid,
                friendly_fire = excluded.friendly_fire,
                open_join = excluded.open_join,
                notifications = excluded.notifications;
        """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clan.getId().toString());
            ps.setString(2, clan.getName());
            ps.setString(3, clan.getTag());
            ps.setString(4, clan.getTagColor());
            ps.setString(5, clan.getDescription());
            ps.setString(6, clan.getLeaderUuid().toString());
            ps.setBoolean(7, clan.getSettings().isFriendlyFire());
            ps.setBoolean(8, clan.getSettings().isOpenJoin());
            ps.setBoolean(9, clan.getSettings().isNotifications());
            ps.setLong(10, clan.getCreatedAt().toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            // Em caso de MySQL sem ON CONFLICT
            saveClanFallback(clan);
        }

        saveStats(clan);
    }

    private void saveClanFallback(Clan clan) {
        String update = "UPDATE eclypse_clans SET name=?, tag=?, tag_color=?, description=?, leader_uuid=?, friendly_fire=?, open_join=?, notifications=? WHERE id=?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(update)) {
            ps.setString(1, clan.getName());
            ps.setString(2, clan.getTag());
            ps.setString(3, clan.getTagColor());
            ps.setString(4, clan.getDescription());
            ps.setString(5, clan.getLeaderUuid().toString());
            ps.setBoolean(6, clan.getSettings().isFriendlyFire());
            ps.setBoolean(7, clan.getSettings().isOpenJoin());
            ps.setBoolean(8, clan.getSettings().isNotifications());
            ps.setString(9, clan.getId().toString());
            int rows = ps.executeUpdate();
            if (rows == 0) {
                String insert = "INSERT INTO eclypse_clans (id, name, tag, tag_color, description, leader_uuid, friendly_fire, open_join, notifications, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ins = conn.prepareStatement(insert)) {
                    ins.setString(1, clan.getId().toString());
                    ins.setString(2, clan.getName());
                    ins.setString(3, clan.getTag());
                    ins.setString(4, clan.getTagColor());
                    ins.setString(5, clan.getDescription());
                    ins.setString(6, clan.getLeaderUuid().toString());
                    ins.setBoolean(7, clan.getSettings().isFriendlyFire());
                    ins.setBoolean(8, clan.getSettings().isOpenJoin());
                    ins.setBoolean(9, clan.getSettings().isNotifications());
                    ins.setLong(10, clan.getCreatedAt().toEpochMilli());
                    ins.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public void saveStats(Clan clan) {
        String sql = """
            INSERT INTO eclypse_clan_stats (clan_id, points, kills, deaths, duel_wins, duel_losses, war_wins, war_losses)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(clan_id) DO UPDATE SET
                points = excluded.points,
                kills = excluded.kills,
                deaths = excluded.deaths,
                duel_wins = excluded.duel_wins,
                duel_losses = excluded.duel_losses,
                war_wins = excluded.war_wins,
                war_losses = excluded.war_losses;
        """;
        ClanStats stats = clan.getStats();
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clan.getId().toString());
            ps.setInt(2, stats.getPoints());
            ps.setInt(3, stats.getKills());
            ps.setInt(4, stats.getDeaths());
            ps.setInt(5, stats.getDuelWins());
            ps.setInt(6, stats.getDuelLosses());
            ps.setInt(7, stats.getWarWins());
            ps.setInt(8, stats.getWarLosses());
            ps.executeUpdate();
        } catch (SQLException e) {
            // Fallback para MySQL
            String update = "UPDATE eclypse_clan_stats SET points=?, kills=?, deaths=?, duel_wins=?, duel_losses=?, war_wins=?, war_losses=? WHERE clan_id=?";
            try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(update)) {
                ps.setInt(1, stats.getPoints());
                ps.setInt(2, stats.getKills());
                ps.setInt(3, stats.getDeaths());
                ps.setInt(4, stats.getDuelWins());
                ps.setInt(5, stats.getDuelLosses());
                ps.setInt(6, stats.getWarWins());
                ps.setInt(7, stats.getWarLosses());
                ps.setString(8, clan.getId().toString());
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    String insert = "INSERT INTO eclypse_clan_stats (clan_id, points, kills, deaths, duel_wins, duel_losses, war_wins, war_losses) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement ins = conn.prepareStatement(insert)) {
                        ins.setString(1, clan.getId().toString());
                        ins.setInt(2, stats.getPoints());
                        ins.setInt(3, stats.getKills());
                        ins.setInt(4, stats.getDeaths());
                        ins.setInt(5, stats.getDuelWins());
                        ins.setInt(6, stats.getDuelLosses());
                        ins.setInt(7, stats.getWarWins());
                        ins.setInt(8, stats.getWarLosses());
                        ins.executeUpdate();
                    }
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void saveMember(UUID clanId, ClanMember member) {
        String sql = """
            INSERT INTO eclypse_clan_members (clan_id, player_uuid, player_name, role, joined_at, last_seen)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET
                clan_id = excluded.clan_id,
                player_name = excluded.player_name,
                role = excluded.role,
                last_seen = excluded.last_seen;
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clanId.toString());
            ps.setString(2, member.getUuid().toString());
            ps.setString(3, member.getLastKnownName());
            ps.setString(4, member.getRole().name());
            ps.setLong(5, member.getJoinedAt().toEpochMilli());
            ps.setLong(6, member.getLastSeen().toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            String update = "UPDATE eclypse_clan_members SET clan_id=?, player_name=?, role=?, last_seen=? WHERE player_uuid=?";
            try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(update)) {
                ps.setString(1, clanId.toString());
                ps.setString(2, member.getLastKnownName());
                ps.setString(3, member.getRole().name());
                ps.setLong(4, member.getLastSeen().toEpochMilli());
                ps.setString(5, member.getUuid().toString());
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    String insert = "INSERT INTO eclypse_clan_members (clan_id, player_uuid, player_name, role, joined_at, last_seen) VALUES (?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement ins = conn.prepareStatement(insert)) {
                        ins.setString(1, clanId.toString());
                        ins.setString(2, member.getUuid().toString());
                        ins.setString(3, member.getLastKnownName());
                        ins.setString(4, member.getRole().name());
                        ins.setLong(5, member.getJoinedAt().toEpochMilli());
                        ins.setLong(6, member.getLastSeen().toEpochMilli());
                        ins.executeUpdate();
                    }
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void removeMember(UUID playerUuid) {
        String sql = "DELETE FROM eclypse_clan_members WHERE player_uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteClan(UUID clanId) {
        String sql = "DELETE FROM eclypse_clans WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clanId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveAllies(UUID clanId, Set<UUID> allies) {
        try (Connection conn = db.getConnection()) {
            String delete = "DELETE FROM eclypse_clan_allies WHERE clan_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(delete)) {
                ps.setString(1, clanId.toString());
                ps.executeUpdate();
            }
            if (!allies.isEmpty()) {
                String insert = "INSERT INTO eclypse_clan_allies (clan_id, ally_clan_id) VALUES (?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insert)) {
                    for (UUID ally : allies) {
                        ps.setString(1, clanId.toString());
                        ps.setString(2, ally.toString());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<ClanWar> loadAllWars() {
        List<ClanWar> wars = new ArrayList<>();
        String sql = "SELECT * FROM eclypse_clan_wars WHERE state = 'ACTIVE'";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID warId = UUID.fromString(rs.getString("war_id"));
                UUID clanAId = UUID.fromString(rs.getString("clan_a_id"));
                UUID clanBId = UUID.fromString(rs.getString("clan_b_id"));
                int killsA = rs.getInt("kills_a");
                int killsB = rs.getInt("kills_b");
                int target = rs.getInt("target_kills");
                Instant started = Instant.ofEpochMilli(rs.getLong("started_at"));
                Instant ends = Instant.ofEpochMilli(rs.getLong("ends_at"));
                ClanWar.WarState state = ClanWar.WarState.valueOf(rs.getString("state"));
                String winnerStr = rs.getString("winner_clan_id");
                UUID winner = winnerStr != null ? UUID.fromString(winnerStr) : null;

                wars.add(new ClanWar(warId, clanAId, clanBId, killsA, killsB, target, started, ends, state, winner));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return wars;
    }

    public void saveWar(ClanWar war) {
        String sql = """
            INSERT INTO eclypse_clan_wars (war_id, clan_a_id, clan_b_id, kills_a, kills_b, target_kills, started_at, ends_at, state, winner_clan_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(war_id) DO UPDATE SET
                kills_a = excluded.kills_a,
                kills_b = excluded.kills_b,
                state = excluded.state,
                winner_clan_id = excluded.winner_clan_id;
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, war.getWarId().toString());
            ps.setString(2, war.getClanAId().toString());
            ps.setString(3, war.getClanBId().toString());
            ps.setInt(4, war.getKillsA());
            ps.setInt(5, war.getKillsB());
            ps.setInt(6, war.getTargetKills());
            ps.setLong(7, war.getStartedAt().toEpochMilli());
            ps.setLong(8, war.getEndsAt().toEpochMilli());
            ps.setString(9, war.getState().name());
            ps.setString(10, war.getWinnerClanId() != null ? war.getWinnerClanId().toString() : null);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Fallback para MySQL
            String update = "UPDATE eclypse_clan_wars SET kills_a=?, kills_b=?, state=?, winner_clan_id=? WHERE war_id=?";
            try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(update)) {
                ps.setInt(1, war.getKillsA());
                ps.setInt(2, war.getKillsB());
                ps.setString(3, war.getState().name());
                ps.setString(4, war.getWinnerClanId() != null ? war.getWinnerClanId().toString() : null);
                ps.setString(5, war.getWarId().toString());
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    String insert = "INSERT INTO eclypse_clan_wars (war_id, clan_a_id, clan_b_id, kills_a, kills_b, target_kills, started_at, ends_at, state, winner_clan_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement ins = conn.prepareStatement(insert)) {
                        ins.setString(1, war.getWarId().toString());
                        ins.setString(2, war.getClanAId().toString());
                        ins.setString(3, war.getClanBId().toString());
                        ins.setInt(4, war.getKillsA());
                        ins.setInt(5, war.getKillsB());
                        ins.setInt(6, war.getTargetKills());
                        ins.setLong(7, war.getStartedAt().toEpochMilli());
                        ins.setLong(8, war.getEndsAt().toEpochMilli());
                        ins.setString(9, war.getState().name());
                        ins.setString(10, war.getWinnerClanId() != null ? war.getWinnerClanId().toString() : null);
                        ins.executeUpdate();
                    }
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    public Map<UUID, KickNotice> loadAllKickNotices() {
        Map<UUID, KickNotice> notices = new HashMap<>();
        String sql = "SELECT * FROM eclypse_clan_kick_notices";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                String clanName = rs.getString("clan_name");
                String clanTag = rs.getString("clan_tag");
                String tagColor = rs.getString("tag_color");
                String actorName = rs.getString("actor_name");
                Instant kickedAt = Instant.ofEpochMilli(rs.getLong("kicked_at"));
                notices.put(playerUuid, new KickNotice(clanName, clanTag, tagColor, actorName, kickedAt));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return notices;
    }

    public void saveKickNotice(UUID playerUuid, KickNotice notice) {
        String sql = """
            INSERT INTO eclypse_clan_kick_notices (player_uuid, clan_name, clan_tag, tag_color, actor_name, kicked_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET
                clan_name = excluded.clan_name,
                clan_tag = excluded.clan_tag,
                tag_color = excluded.tag_color,
                actor_name = excluded.actor_name,
                kicked_at = excluded.kicked_at;
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, notice.getClanName());
            ps.setString(3, notice.getClanTag());
            ps.setString(4, notice.getTagColor());
            ps.setString(5, notice.getActorName());
            ps.setLong(6, notice.getKickedAt().toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            String update = "UPDATE eclypse_clan_kick_notices SET clan_name=?, clan_tag=?, tag_color=?, actor_name=?, kicked_at=? WHERE player_uuid=?";
            try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(update)) {
                ps.setString(1, notice.getClanName());
                ps.setString(2, notice.getClanTag());
                ps.setString(3, notice.getTagColor());
                ps.setString(4, notice.getActorName());
                ps.setLong(5, notice.getKickedAt().toEpochMilli());
                ps.setString(6, playerUuid.toString());
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    String insert = "INSERT INTO eclypse_clan_kick_notices (player_uuid, clan_name, clan_tag, tag_color, actor_name, kicked_at) VALUES (?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement ins = conn.prepareStatement(insert)) {
                        ins.setString(1, playerUuid.toString());
                        ins.setString(2, notice.getClanName());
                        ins.setString(3, notice.getClanTag());
                        ins.setString(4, notice.getTagColor());
                        ins.setString(5, notice.getActorName());
                        ins.setLong(6, notice.getKickedAt().toEpochMilli());
                        ins.executeUpdate();
                    }
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void deleteKickNotice(UUID playerUuid) {
        String sql = "DELETE FROM eclypse_clan_kick_notices WHERE player_uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

