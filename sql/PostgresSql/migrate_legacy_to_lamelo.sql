-- Run only inside lamelo_agent_pgvector after restoring a backup of nexus_agent_pgvector.
-- Keep the original database unchanged until application retrieval has been verified.
BEGIN;

DO $$
BEGIN
    IF to_regclass('public.nexus_agent_document_embedding') IS NULL THEN
        RAISE EXCEPTION 'Missing legacy table public.nexus_agent_document_embedding';
    END IF;
    IF to_regclass('public.lamelo_agent_document_embedding') IS NOT NULL THEN
        RAISE EXCEPTION 'Target table public.lamelo_agent_document_embedding already exists';
    END IF;
    IF to_regclass('public.nexus_agent_document_embedding_pkey') IS NULL THEN
        RAISE EXCEPTION 'Missing legacy index or primary-key index public.nexus_agent_document_embedding_pkey';
    END IF;
    IF to_regclass('public.idx_nexus_agent_document_embedding_document_id') IS NULL THEN
        RAISE EXCEPTION 'Missing legacy index or primary-key index public.idx_nexus_agent_document_embedding_document_id';
    END IF;
    IF to_regclass('public.idx_nexus_agent_document_embedding_task_id') IS NULL THEN
        RAISE EXCEPTION 'Missing legacy index or primary-key index public.idx_nexus_agent_document_embedding_task_id';
    END IF;
    IF to_regclass('public.idx_nexus_agent_document_embedding_plan_id') IS NULL THEN
        RAISE EXCEPTION 'Missing legacy index or primary-key index public.idx_nexus_agent_document_embedding_plan_id';
    END IF;
    IF to_regclass('public.idx_nexus_agent_document_embedding_parent_block_id') IS NULL THEN
        RAISE EXCEPTION 'Missing legacy index or primary-key index public.idx_nexus_agent_document_embedding_parent_block_id';
    END IF;
    IF to_regclass('public.idx_nexus_agent_document_embedding_status') IS NULL THEN
        RAISE EXCEPTION 'Missing legacy index or primary-key index public.idx_nexus_agent_document_embedding_status';
    END IF;
    IF to_regclass('public.lamelo_agent_document_embedding_pkey') IS NOT NULL THEN
        RAISE EXCEPTION 'Target index public.lamelo_agent_document_embedding_pkey already exists';
    END IF;
    IF to_regclass('public.idx_lamelo_agent_document_embedding_document_id') IS NOT NULL THEN
        RAISE EXCEPTION 'Target index public.idx_lamelo_agent_document_embedding_document_id already exists';
    END IF;
    IF to_regclass('public.idx_lamelo_agent_document_embedding_task_id') IS NOT NULL THEN
        RAISE EXCEPTION 'Target index public.idx_lamelo_agent_document_embedding_task_id already exists';
    END IF;
    IF to_regclass('public.idx_lamelo_agent_document_embedding_plan_id') IS NOT NULL THEN
        RAISE EXCEPTION 'Target index public.idx_lamelo_agent_document_embedding_plan_id already exists';
    END IF;
    IF to_regclass('public.idx_lamelo_agent_document_embedding_parent_block_id') IS NOT NULL THEN
        RAISE EXCEPTION 'Target index public.idx_lamelo_agent_document_embedding_parent_block_id already exists';
    END IF;
    IF to_regclass('public.idx_lamelo_agent_document_embedding_status') IS NOT NULL THEN
        RAISE EXCEPTION 'Target index public.idx_lamelo_agent_document_embedding_status already exists';
    END IF;
END $$;

ALTER TABLE public.nexus_agent_document_embedding RENAME TO lamelo_agent_document_embedding;
ALTER TABLE public.lamelo_agent_document_embedding
    RENAME CONSTRAINT nexus_agent_document_embedding_pkey TO lamelo_agent_document_embedding_pkey;
ALTER INDEX public.idx_nexus_agent_document_embedding_document_id RENAME TO idx_lamelo_agent_document_embedding_document_id;
ALTER INDEX public.idx_nexus_agent_document_embedding_task_id RENAME TO idx_lamelo_agent_document_embedding_task_id;
ALTER INDEX public.idx_nexus_agent_document_embedding_plan_id RENAME TO idx_lamelo_agent_document_embedding_plan_id;
ALTER INDEX public.idx_nexus_agent_document_embedding_parent_block_id RENAME TO idx_lamelo_agent_document_embedding_parent_block_id;
ALTER INDEX public.idx_nexus_agent_document_embedding_status RENAME TO idx_lamelo_agent_document_embedding_status;

COMMIT;

-- Verify table, indexes, and retained row count before switching the application.
SELECT COUNT(*) AS embedding_rows FROM public.lamelo_agent_document_embedding;
SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'lamelo_agent_document_embedding' ORDER BY indexname;
