-- Migrate a restored copy of the legacy schema; do not run against the only legacy database.
-- Connect to lamelo_agent before execution. The full table rename is a single statement so any missing/colliding table stops that step.
USE lamelo_agent;

-- Preflight: expect every listed nexus_agent_* table, and zero lamelo_agent_* targets.
SELECT TABLE_NAME
FROM information_schema.tables
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('nexus_agent_chat_dialogue', 'nexus_agent_chat_exchange', 'nexus_agent_chat_memory_summary', 'nexus_agent_chat_exchange_trace_stage', 'nexus_agent_document', 'nexus_agent_document_strategy_plan', 'nexus_agent_document_strategy_step', 'nexus_agent_document_task', 'nexus_agent_document_task_log', 'nexus_agent_document_structure_node', 'nexus_agent_document_parent_block', 'nexus_agent_document_chunk', 'nexus_agent_knowledge_scope_node', 'nexus_agent_knowledge_topic_node', 'nexus_agent_document_profile', 'nexus_agent_topic_document_relation', 'nexus_agent_knowledge_route_trace', 'nexus_agent_chat_retrieval_result', 'nexus_agent_chat_channel_execution', 'nexus_agent_chat_stage_benchmark');

SELECT TABLE_NAME
FROM information_schema.tables
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('lamelo_agent_chat_dialogue', 'lamelo_agent_chat_exchange', 'lamelo_agent_chat_memory_summary', 'lamelo_agent_chat_exchange_trace_stage', 'lamelo_agent_document', 'lamelo_agent_document_strategy_plan', 'lamelo_agent_document_strategy_step', 'lamelo_agent_document_task', 'lamelo_agent_document_task_log', 'lamelo_agent_document_structure_node', 'lamelo_agent_document_parent_block', 'lamelo_agent_document_chunk', 'lamelo_agent_knowledge_scope_node', 'lamelo_agent_knowledge_topic_node', 'lamelo_agent_document_profile', 'lamelo_agent_topic_document_relation', 'lamelo_agent_knowledge_route_trace', 'lamelo_agent_chat_retrieval_result', 'lamelo_agent_chat_channel_execution', 'lamelo_agent_chat_stage_benchmark');

RENAME TABLE
    `nexus_agent_chat_dialogue` TO `lamelo_agent_chat_dialogue`,
    `nexus_agent_chat_exchange` TO `lamelo_agent_chat_exchange`,
    `nexus_agent_chat_memory_summary` TO `lamelo_agent_chat_memory_summary`,
    `nexus_agent_chat_exchange_trace_stage` TO `lamelo_agent_chat_exchange_trace_stage`,
    `nexus_agent_document` TO `lamelo_agent_document`,
    `nexus_agent_document_strategy_plan` TO `lamelo_agent_document_strategy_plan`,
    `nexus_agent_document_strategy_step` TO `lamelo_agent_document_strategy_step`,
    `nexus_agent_document_task` TO `lamelo_agent_document_task`,
    `nexus_agent_document_task_log` TO `lamelo_agent_document_task_log`,
    `nexus_agent_document_structure_node` TO `lamelo_agent_document_structure_node`,
    `nexus_agent_document_parent_block` TO `lamelo_agent_document_parent_block`,
    `nexus_agent_document_chunk` TO `lamelo_agent_document_chunk`,
    `nexus_agent_knowledge_scope_node` TO `lamelo_agent_knowledge_scope_node`,
    `nexus_agent_knowledge_topic_node` TO `lamelo_agent_knowledge_topic_node`,
    `nexus_agent_document_profile` TO `lamelo_agent_document_profile`,
    `nexus_agent_topic_document_relation` TO `lamelo_agent_topic_document_relation`,
    `nexus_agent_knowledge_route_trace` TO `lamelo_agent_knowledge_route_trace`,
    `nexus_agent_chat_retrieval_result` TO `lamelo_agent_chat_retrieval_result`,
    `nexus_agent_chat_channel_execution` TO `lamelo_agent_chat_channel_execution`,
    `nexus_agent_chat_stage_benchmark` TO `lamelo_agent_chat_stage_benchmark`;

ALTER TABLE `lamelo_agent_document`
    RENAME INDEX `idx_object_name` TO `idx_lamelo_agent_object_name`,
    RENAME INDEX `idx_parse_status` TO `idx_lamelo_agent_parse_status`,
    RENAME INDEX `idx_strategy_status` TO `idx_lamelo_agent_strategy_status`,
    RENAME INDEX `idx_index_status` TO `idx_lamelo_agent_index_status`,
    RENAME INDEX `idx_knowledge_scope_code` TO `idx_lamelo_agent_knowledge_scope_code`,
    RENAME INDEX `idx_current_plan_id` TO `idx_lamelo_agent_current_plan_id`;

ALTER TABLE `lamelo_agent_document_strategy_plan`
    RENAME INDEX `uk_document_version` TO `uk_lamelo_agent_document_version`,
    RENAME INDEX `idx_plan_status` TO `idx_lamelo_agent_plan_status`;

ALTER TABLE `lamelo_agent_document_strategy_step`
    RENAME INDEX `uk_plan_pipeline_step_no` TO `uk_lamelo_agent_plan_pipeline_step_no`,
    RENAME INDEX `idx_document_id` TO `idx_lamelo_agent_document_id`,
    RENAME INDEX `idx_strategy_type` TO `idx_lamelo_agent_strategy_type`,
    RENAME INDEX `idx_pipeline_type` TO `idx_lamelo_agent_pipeline_type`;

ALTER TABLE `lamelo_agent_document_task`
    RENAME INDEX `idx_document_task` TO `idx_lamelo_agent_document_task`,
    RENAME INDEX `idx_task_status` TO `idx_lamelo_agent_task_status`,
    RENAME INDEX `idx_plan_id` TO `idx_lamelo_agent_plan_id`;

ALTER TABLE `lamelo_agent_document_task_log`
    RENAME INDEX `idx_task_id` TO `idx_lamelo_agent_task_id`,
    RENAME INDEX `idx_document_id` TO `idx_lamelo_agent_document_id`,
    RENAME INDEX `idx_stage_type` TO `idx_lamelo_agent_stage_type`;

ALTER TABLE `lamelo_agent_document_structure_node`
    RENAME INDEX `uk_parse_task_node_no` TO `uk_lamelo_agent_parse_task_node_no`,
    RENAME INDEX `idx_document_id` TO `idx_lamelo_agent_document_id`,
    RENAME INDEX `idx_parse_task_id` TO `idx_lamelo_agent_parse_task_id`,
    RENAME INDEX `idx_parent_node_id` TO `idx_lamelo_agent_parent_node_id`,
    RENAME INDEX `idx_node_type` TO `idx_lamelo_agent_node_type`;

ALTER TABLE `lamelo_agent_document_parent_block`
    RENAME INDEX `uk_task_parent_no` TO `uk_lamelo_agent_task_parent_no`,
    RENAME INDEX `idx_document_id` TO `idx_lamelo_agent_document_id`,
    RENAME INDEX `idx_task_id` TO `idx_lamelo_agent_task_id`;

ALTER TABLE `lamelo_agent_document_chunk`
    RENAME INDEX `uk_task_chunk_no` TO `uk_lamelo_agent_task_chunk_no`,
    RENAME INDEX `idx_document_id` TO `idx_lamelo_agent_document_id`,
    RENAME INDEX `idx_parent_block_id` TO `idx_lamelo_agent_parent_block_id`,
    RENAME INDEX `idx_vector_status` TO `idx_lamelo_agent_vector_status`;

ALTER TABLE `lamelo_agent_knowledge_scope_node`
    RENAME INDEX `uk_scope_code` TO `uk_lamelo_agent_scope_code`,
    RENAME INDEX `idx_parent_scope_code` TO `idx_lamelo_agent_parent_scope_code`,
    RENAME INDEX `idx_status` TO `idx_lamelo_agent_status`;

ALTER TABLE `lamelo_agent_knowledge_topic_node`
    RENAME INDEX `uk_topic_code` TO `uk_lamelo_agent_topic_code`,
    RENAME INDEX `idx_scope_code` TO `idx_lamelo_agent_scope_code`,
    RENAME INDEX `idx_status` TO `idx_lamelo_agent_status`;

ALTER TABLE `lamelo_agent_document_profile`
    RENAME INDEX `uk_document_id` TO `uk_lamelo_agent_document_id`,
    RENAME INDEX `idx_profile_status` TO `idx_lamelo_agent_profile_status`,
    RENAME INDEX `idx_document_type` TO `idx_lamelo_agent_document_type`,
    RENAME INDEX `idx_status` TO `idx_lamelo_agent_status`;

ALTER TABLE `lamelo_agent_topic_document_relation`
    RENAME INDEX `uk_topic_document` TO `uk_lamelo_agent_topic_document`,
    RENAME INDEX `idx_document_id` TO `idx_lamelo_agent_document_id`,
    RENAME INDEX `idx_topic_code` TO `idx_lamelo_agent_topic_code`,
    RENAME INDEX `idx_status` TO `idx_lamelo_agent_status`;

ALTER TABLE `lamelo_agent_knowledge_route_trace`
    RENAME INDEX `idx_conversation_exchange` TO `idx_lamelo_agent_conversation_exchange`,
    RENAME INDEX `idx_selected_document_id` TO `idx_lamelo_agent_selected_document_id`,
    RENAME INDEX `idx_route_status` TO `idx_lamelo_agent_route_status`,
    RENAME INDEX `idx_create_time` TO `idx_lamelo_agent_create_time`;

ALTER TABLE `lamelo_agent_chat_retrieval_result`
    RENAME INDEX `idx_retrieval_result_exchange` TO `idx_lamelo_agent_retrieval_result_exchange`,
    RENAME INDEX `idx_retrieval_result_trace` TO `idx_lamelo_agent_retrieval_result_trace`,
    RENAME INDEX `idx_retrieval_result_sub_question` TO `idx_lamelo_agent_retrieval_result_sub_question`,
    RENAME INDEX `idx_retrieval_result_channel` TO `idx_lamelo_agent_retrieval_result_channel`,
    RENAME INDEX `idx_retrieval_result_document` TO `idx_lamelo_agent_retrieval_result_document`;

ALTER TABLE `lamelo_agent_chat_channel_execution`
    RENAME INDEX `idx_channel_exec_exchange` TO `idx_lamelo_agent_channel_exec_exchange`,
    RENAME INDEX `idx_channel_exec_trace` TO `idx_lamelo_agent_channel_exec_trace`,
    RENAME INDEX `idx_channel_exec_channel` TO `idx_lamelo_agent_channel_exec_channel`;

ALTER TABLE `lamelo_agent_chat_stage_benchmark`
    RENAME INDEX `uk_stage_benchmark_code_mode` TO `uk_lamelo_agent_stage_benchmark_code_mode`;

-- Verify that no product tables retain the legacy prefix.
SELECT TABLE_NAME
FROM information_schema.tables
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'nexus_agent\_%';

-- Verify renamed tables and indexes before switching the application.
SELECT TABLE_NAME, INDEX_NAME
FROM information_schema.statistics
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'lamelo_agent\_%'
ORDER BY TABLE_NAME, INDEX_NAME;
