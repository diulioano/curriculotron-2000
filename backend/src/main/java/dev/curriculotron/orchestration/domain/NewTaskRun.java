package dev.curriculotron.orchestration.domain;

/** Pedido de criação de {@code TaskRun}. Tarefas de sistema não têm bot. */
public record NewTaskRun(
    String kind, TaskRunTrigger trigger, String idempotencyKey, String botId, String botConfigId) {

  public static NewTaskRun system(String kind, TaskRunTrigger trigger, String idempotencyKey) {
    return new NewTaskRun(kind, trigger, idempotencyKey, null, null);
  }
}
