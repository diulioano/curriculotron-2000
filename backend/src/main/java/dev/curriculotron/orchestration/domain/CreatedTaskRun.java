package dev.curriculotron.orchestration.domain;

/** Resultado de criar-ou-obter: {@code created=false} quando a chave idempotente já existia. */
public record CreatedTaskRun(TaskRun run, boolean created) {}
