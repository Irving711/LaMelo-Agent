package com.lamelo.agent.ai.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformAuthMigrationTest {
    private static Path schema() {
        return Path.of("..", "..", "sql", "Mysql", "create_table_mysql.sql").toAbsolutePath().normalize();
    }

    @Test
    void accountMigrationDoesNotSeedOrReactivatePublicDefaultAdmin() throws Exception {
        String sql = Files.readString(schema());
        assertFalse(sql.contains("INSERT INTO lamelo_agent_account (username"));
        assertFalse(sql.contains("ON DUPLICATE KEY UPDATE enabled = 1"));
        assertTrue(sql.contains("SET enabled = 0"));
        assertTrue(sql.contains("password_hash = '$2a$10$J4R3O070wmA4LFufwJ.nuugjqxkW0vDt79IEr88TRjxWlrk3voLIi'"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS lamelo_agent_conversation_owner"));
    }

    @Test
    void wechatIdentityAllowsRepeatedUnbindAndRebind() throws Exception {
        String sql = Files.readString(schema());
        assertTrue(sql.contains("active_openid"));
        assertTrue(sql.contains("active_unionid"));
        assertFalse(sql.contains("UNIQUE KEY uk_lamelo_agent_wechat_identity_openid_status (openid, status)"));
    }

    @Test
    void consolidatedCreateTableScriptContainsMiniappSecurityTables() throws Exception {
        String sql = Files.readString(Path.of("..", "..", "sql", "Mysql", "create_table_mysql.sql")
            .toAbsolutePath().normalize());
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS lamelo_agent_account"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS lamelo_agent_conversation_owner"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS lamelo_agent_wechat_identity"));
        assertFalse(sql.contains("VALUES ('admin', '$2a$10$J4R3O070wmA4LFufwJ.nuugjqxkW0vDt79IEr88TRjxWlrk3voLIi'"));
    }
}
