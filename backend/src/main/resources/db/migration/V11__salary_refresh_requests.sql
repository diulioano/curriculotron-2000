CREATE TABLE salary_refresh_request (
 opportunity_id TEXT NOT NULL REFERENCES opportunity(id) ON DELETE CASCADE,
 request_key TEXT NOT NULL,
 task_run_id TEXT NOT NULL REFERENCES task_run(id),
 PRIMARY KEY(opportunity_id, request_key)
);
