-- MySQL 8.0 does not support ADD COLUMN IF NOT EXISTS. Each statement is guarded
-- so existing installations can rerun this migration safely.
SET @lease_owner_ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `lamelo_agent_document_task` ADD COLUMN `lease_owner` varchar(255) DEFAULT NULL COMMENT ''当前执行者''',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'lamelo_agent_document_task'
      AND COLUMN_NAME = 'lease_owner'
);
PREPARE lease_owner_statement FROM @lease_owner_ddl;
EXECUTE lease_owner_statement;
DEALLOCATE PREPARE lease_owner_statement;

SET @lease_until_ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `lamelo_agent_document_task` ADD COLUMN `lease_until` datetime DEFAULT NULL COMMENT ''租约截止时间''',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'lamelo_agent_document_task'
      AND COLUMN_NAME = 'lease_until'
);
PREPARE lease_until_statement FROM @lease_until_ddl;
EXECUTE lease_until_statement;
DEALLOCATE PREPARE lease_until_statement;

SET @attempt_count_ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `lamelo_agent_document_task` ADD COLUMN `attempt_count` int DEFAULT NULL COMMENT ''租约获取次数''',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'lamelo_agent_document_task'
      AND COLUMN_NAME = 'attempt_count'
);
PREPARE attempt_count_statement FROM @attempt_count_ddl;
EXECUTE attempt_count_statement;
DEALLOCATE PREPARE attempt_count_statement;
