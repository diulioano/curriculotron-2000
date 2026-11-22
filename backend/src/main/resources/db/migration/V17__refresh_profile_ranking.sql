UPDATE compatibility_refresh
SET evaluator_version = 2,
    state = 'pending',
    run_id = NULL,
    started_at = NULL,
    finished_at = NULL,
    error_code = NULL,
    assessed_count = 0,
    remaining_count = (SELECT count(*) FROM opportunity WHERE merged_into IS NULL)
WHERE id = 1 AND evaluator_version < 2;
