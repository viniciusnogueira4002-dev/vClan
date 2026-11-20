package org.vine.vclans.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public abstract class DatabaseManager {

    public abstract Connection getConnection() throws SQLException;
    public abstract void close();

    public void initTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // Tabela de Clãs
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS eclypse_clans (
                    id VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(32) NOT NULL UNIQUE,
                    tag VARCHAR(16) NOT NULL UNIQUE,
                    tag_color VARCHAR(16) NOT NULL,
                    description VARCHAR(128),
                    leader_uuid VARCHAR(36) NOT NULL,
                    friendly_fire BOOLEAN DEFAULT 0,
                    open_join BOOLEAN DEFAULT 0,
                    notifications BOOLEAN DEFAULT 1,
                    created_at BIGINT NOT NULL
                );
            """);

            // Tabela de Estatísticas
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS eclypse_clan_stats (
                    clan_id VARCHAR(36) PRIMARY KEY,
                    points INT DEFAULT 0,
                    kills INT DEFAULT 0,
                    deaths INT DEFAULT 0,
                    duel_wins INT DEFAULT 0,
                    duel_losses INT DEFAULT 0,
                    war_wins INT DEFAULT 0,
                    war_losses INT DEFAULT 0,
                    FOREIGN KEY (clan_id) REFERENCES eclypse_clans(id) ON DELETE CASCADE
                );
            """);

            // Tabela de Membros
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS eclypse_clan_members (
                    clan_id VARCHAR(36) NOT NULL,
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(16) NOT NULL,
                    role VARCHAR(16) NOT NULL,
                    joined_at BIGINT NOT NULL,
                    last_seen BIGINT NOT NULL,
                    FOREIGN KEY (clan_id) REFERENCES eclypse_clans(id) ON DELETE CASCADE
                );
            """);

            // Tabela de Alianças
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS eclypse_clan_allies (
                    clan_id VARCHAR(36) NOT NULL,
                    ally_clan_id VARCHAR(36) NOT NULL,
                    PRIMARY KEY (clan_id, ally_clan_id),
                    FOREIGN KEY (clan_id) REFERENCES eclypse_clans(id) ON DELETE CASCADE
                );
            """);

            // Tabela de Guerras
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS eclypse_clan_wars (
                    war_id VARCHAR(36) PRIMARY KEY,
                    clan_a_id VARCHAR(36) NOT NULL,
                    clan_b_id VARCHAR(36) NOT NULL,
                    kills_a INT DEFAULT 0,
                    kills_b INT DEFAULT 0,
                    target_kills INT NOT NULL,
                    started_at BIGINT NOT NULL,
                    ends_at BIGINT NOT NULL,
                    state VARCHAR(24) NOT NULL,
                    winner_clan_id VARCHAR(36)
                );
            """);

            // Tabela de Logs de Auditoria
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS eclypse_clan_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    clan_id VARCHAR(36) NOT NULL,
                    actor_uuid VARCHAR(36),
                    action TEXT NOT NULL,
                    timestamp BIGINT NOT NULL
                );
            """);

            // Tabela de Notificações de Expulsão
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS eclypse_clan_kick_notices (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    clan_name VARCHAR(32) NOT NULL,
                    clan_tag VARCHAR(16) NOT NULL,
                    tag_color VARCHAR(16) NOT NULL,
                    actor_name VARCHAR(16) NOT NULL,
                    kicked_at BIGINT NOT NULL
                );
            """);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

