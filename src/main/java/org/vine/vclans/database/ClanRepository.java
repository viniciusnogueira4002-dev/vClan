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
                clan.setLastTagChangeAt(getLongSafe(rs, "last_tag_change"));
                clan.setTagStyle(getStringSafe(rs, "tag_style"));
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
                    stats.setKills(rs.getInt("kills"));
                    stats.setDeaths(rs.getInt("deaths"));
                    stats.setDuelWins(rs.getInt("duel_wins"));
                    stats.setDuelLosses(rs.getInt("duel_losses"));
                    stats.setWarWins(rs.getInt("war_wins"));
                    stats.setWarLosses(rs.getInt("war_losses"));
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
                    long donatedTp = getLongSafe(rs, "donated_tp");
                    double donatedMoney = getDoubleSafe(rs, "donated_money");
                    int warPoints = getIntSafe(rs, "war_points");

                    ClanMember member = new ClanMember(playerUuid, name, role,
                            Instant.ofEpochMilli(joined), Instant.ofEpochMilli(lastSeen),
                            donatedTp, donatedMoney, warPoints);
                    clan.addMember(member);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Carregar Economia, Upgrades e Guerra da Temporada
        String sqlEconomy = "SELECT * FROM eclypse_clan_economy";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlEconomy);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                UUID clanId = UUID.fromString(rs.getString("clan_id"));
                Clan clan = clans.get(clanId);
                if (clan != null) {
                    clan.getBank().setTp(rs.getLong("bank_tp"));
                    clan.getBank().setMoney(rs.getDouble("bank_money"));
                    clan.getUpgrades().setTpMultiplierLevel(rs.getInt("tp_mult_level"));
                    clan.getUpgrades().setMoneyMultiplierLevel(rs.getInt("money_mult_level"));
                    clan.getUpgrades().setMemberLimitLevel(rs.getInt("member_limit_level"));
                    clan.getUpgrades().setWarMultiplierLevel(rs.getInt("war_mult_level"));
                    clan.setSeasonWarPoints(rs.getInt("season_war_points"));
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
            INSERT INTO eclypse_clans (id, name, tag, tag_color, description, leader_uuid, friendly_fire, open_join, notifications, created_at, last_tag_change, tag_style)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                tag = excluded.tag,
                tag_color = excluded.tag_color,
                description = excluded.description,
                leader_uuid = excluded.leader_uuid,
                friendly_fire = excluded.friendly_fire,
                open_join = excluded.open_join,
                notifications = excluded.notifications,
                last_tag_change = excluded.last_tag_change,
                tag_style = excluded.tag_style;
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
            ps.setLong(11, clan.getLastTagChangeAt());
            ps.setString(12, clan.getTagStyle());
            ps.executeUpdate();
        } catch (SQLException e) {
            // Em caso de MySQL sem ON CONFLICT
            saveClanFallback(clan);
        }

        saveStats(clan);
        saveEconomy(clan);
    }

    private void saveClanFallback(Clan clan) {
        String update = "UPDATE eclypse_clans SET name=?, tag=?, tag_color=?, description=?, leader_uuid=?, friendly_fire=?, open_join=?, notifications=?, last_tag_change=?, tag_style=? WHERE id=?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(update)) {
            ps.setString(1, clan.getName());
            ps.setString(2, clan.getTag());
            ps.setString(3, clan.getTagColor());
            ps.setString(4, clan.getDescription());
            ps.setString(5, clan.getLeaderUuid().toString());
            ps.setBoolean(6, clan.getSettings().isFriendlyFire());
            ps.setBoolean(7, clan.getSettings().isOpenJoin());
            ps.setBoolean(8, clan.getSettings().isNotifications());
            ps.setLong(9, clan.getLastTagChangeAt());
            ps.setString(10, clan.getTagStyle());
            ps.setString(11, clan.getId().toString());
            int rows = ps.executeUpdate();
            if (rows == 0) {
                String insert = "INSERT INTO eclypse_clans (id, name, tag, tag_color, description, leader_uuid, friendly_fire, open_join, notifications, created_at, last_tag_change, tag_style) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
                    ins.setLong(11, clan.getLastTagChangeAt());
                    ins.setString(12, clan.getTagStyle());
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

    public void saveEconomy(Clan clan) {
        String sql = """
            INSERT INTO eclypse_clan_economy (clan_id, bank_tp, bank_money, tp_mult_level, money_mult_level, member_limit_level, war_mult_level, season_war_points)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(clan_id) DO UPDATE SET
                bank_tp = excluded.bank_tp,
                bank_money = excluded.bank_money,
                tp_mult_level = excluded.tp_mult_level,
                money_mult_level = excluded.money_mult_level,
                member_limit_level = excluded.member_limit_level,
                war_mult_level = excluded.war_mult_level,
                season_war_points = excluded.season_war_points;
        """;
        ClanBank bank = clan.getBank();
        ClanUpgrades upgrades = clan.getUpgrades();
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clan.getId().toString());
            ps.setLong(2, bank.getTp());
            ps.setDouble(3, bank.getMoney());
            ps.setInt(4, upgrades.getTpMultiplierLevel());
            ps.setInt(5, upgrades.getMoneyMultiplierLevel());
            ps.setInt(6, upgrades.getMemberLimitLevel());
            ps.setInt(7, upgrades.getWarMultiplierLevel());
            ps.setInt(8, clan.getSeasonWarPoints());
            ps.executeUpdate();
        } catch (SQLException e) {
            String update = "UPDATE eclypse_clan_economy SET bank_tp=?, bank_money=?, tp_mult_level=?, money_mult_level=?, member_limit_level=?, war_mult_level=?, season_war_points=? WHERE clan_id=?";
            try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(update)) {
                ps.setLong(1, bank.getTp());
                ps.setDouble(2, bank.getMoney());
                ps.setInt(3, upgrades.getTpMultiplierLevel());
                ps.setInt(4, upgrades.getMoneyMultiplierLevel());
                ps.setInt(5, upgrades.getMemberLimitLevel());
                ps.setInt(6, upgrades.getWarMultiplierLevel());
                ps.setInt(7, clan.getSeasonWarPoints());
                ps.setString(8, clan.getId().toString());
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    String insert = "INSERT INTO eclypse_clan_economy (clan_id, bank_tp, bank_money, tp_mult_level, money_mult_level, member_limit_level, war_mult_level, season_war_points) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement ins = conn.prepareStatement(insert)) {
                        ins.setString(1, clan.getId().toString());
                        ins.setLong(2, bank.getTp());
                        ins.setDouble(3, bank.getMoney());
                        ins.setInt(4, upgrades.getTpMultiplierLevel());
                        ins.setInt(5, upgrades.getMoneyMultiplierLevel());
                        ins.setInt(6, upgrades.getMemberLimitLevel());
                        ins.setInt(7, upgrades.getWarMultiplierLevel());
                        ins.setInt(8, clan.getSeasonWarPoints());
                        ins.executeUpdate();
                    }
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void resetAllSeasonWarPoints() {
        String sql = "UPDATE eclypse_clan_economy SET season_war_points = 0";
        String memberSql = "UPDATE eclypse_clan_members SET war_points = 0";
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            stmt.executeUpdate(memberSql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveMember(UUID clanId, ClanMember member) {
        String sql = """
            INSERT INTO eclypse_clan_members (clan_id, player_uuid, player_name, role, joined_at, last_seen, donated_tp, donated_money, war_points)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET
                clan_id = excluded.clan_id,
                player_name = excluded.player_name,
                role = excluded.role,
                last_seen = excluded.last_seen,
                donated_tp = excluded.donated_tp,
                donated_money = excluded.donated_money,
                war_points = excluded.war_points;
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clanId.toString());
            ps.setString(2, member.getUuid().toString());
            ps.setString(3, member.getLastKnownName());
            ps.setString(4, member.getRole().name());
            ps.setLong(5, member.getJoinedAt().toEpochMilli());
            ps.setLong(6, member.getLastSeen().toEpochMilli());
            ps.setLong(7, member.getDonatedTp());
            ps.setDouble(8, member.getDonatedMoney());
            ps.setInt(9, member.getWarPointsContributed());
            ps.executeUpdate();
        } catch (SQLException e) {
            String update = "UPDATE eclypse_clan_members SET clan_id=?, player_name=?, role=?, last_seen=?, donated_tp=?, donated_money=?, war_points=? WHERE player_uuid=?";
            try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(update)) {
                ps.setString(1, clanId.toString());
                ps.setString(2, member.getLastKnownName());
                ps.setString(3, member.getRole().name());
                ps.setLong(4, member.getLastSeen().toEpochMilli());
                ps.setLong(5, member.getDonatedTp());
                ps.setDouble(6, member.getDonatedMoney());
                ps.setInt(7, member.getWarPointsContributed());
                ps.setString(8, member.getUuid().toString());
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    String insert = "INSERT INTO eclypse_clan_members (clan_id, player_uuid, player_name, role, joined_at, last_seen, donated_tp, donated_money, war_points) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement ins = conn.prepareStatement(insert)) {
                        ins.setString(1, clanId.toString());
                        ins.setString(2, member.getUuid().toString());
                        ins.setString(3, member.getLastKnownName());
                        ins.setString(4, member.getRole().name());
                        ins.setLong(5, member.getJoinedAt().toEpochMilli());
                        ins.setLong(6, member.getLastSeen().toEpochMilli());
                        ins.setLong(7, member.getDonatedTp());
                        ins.setDouble(8, member.getDonatedMoney());
                        ins.setInt(9, member.getWarPointsContributed());
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

    private long getLongSafe(ResultSet rs, String col) {
        try { return rs.getLong(col); } catch (SQLException ignored) { return 0L; }
    }

    private double getDoubleSafe(ResultSet rs, String col) {
        try { return rs.getDouble(col); } catch (SQLException ignored) { return 0.0; }
    }

    private int getIntSafe(ResultSet rs, String col) {
        try { return rs.getInt(col); } catch (SQLException ignored) { return 0; }
    }

    private String getStringSafe(ResultSet rs, String col) {
        try {
            String value = rs.getString(col);
            return value != null ? value : "";
        } catch (SQLException ignored) {
            return "";
        }
    }
}

