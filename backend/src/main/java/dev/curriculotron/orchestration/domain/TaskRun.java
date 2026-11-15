package dev.curriculotron.orchestration.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Execução de uma tarefa (data-model.md). A chave idempotente impede execuções duplicadas da mesma
 * rotina/pedido (NFR-004); o checkpoint guarda etapas concluídas para a retomada.
 */
public record TaskRun(
    String id,
    String botId,
    String botConfigId,
    String kind,
    TaskRunTrigger trigger,
    String idempotencyKey,
    TaskRunState state,
    PauseReason pauseReason,
    Map<String, Object> checkpoint,
    List<String> sourcesConsulted,
    List<String> sourcesFailed,
    String errorSummary,
    Instant startedAt,
    Instant finishedAt,
    Instant createdAt) {

  public TaskRun {
    // Preserva a ordem de inserção e aceita valores nulos do JSON.
    checkpoint =
        checkpoint == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(checkpoint));
    sourcesConsulted = sourcesConsulted == null ? List.of() : List.copyOf(sourcesConsulted);
    sourcesFailed = sourcesFailed == null ? List.of() : List.copyOf(sourcesFailed);
  }

  public static TaskRun queued(String id, NewTaskRun req, Instant now) {
    return new TaskRun(
        id,
        req.botId(),
        req.botConfigId(),
        req.kind(),
        req.trigger(),
        req.idempotencyKey(),
        TaskRunState.queued,
        null,
        Map.of(),
        List.of(),
        List.of(),
        null,
        null,
        null,
        now);
  }

  /**
   * Muda de estado. {@code reason} só vale para {@code paused} (obrigatório nele); {@code error},
   * quando presente, substitui o resumo de erro.
   */
  public TaskRun transitionTo(TaskRunState to, PauseReason reason, String error, Instant now) {
    if (to == TaskRunState.paused && reason == null) {
      throw new IllegalArgumentException("Pausa exige motivo");
    }
    if (!state.canTransitionTo(to)) {
      throw new IllegalStateException(
          "Transição inválida de " + state + " para " + to + " em " + id);
    }
    return new TaskRun(
        id,
        botId,
        botConfigId,
        kind,
        trigger,
        idempotencyKey,
        to,
        to == TaskRunState.paused ? reason : null,
        checkpoint,
        sourcesConsulted,
        sourcesFailed,
        error != null ? error : errorSummary,
        startedAt == null && to == TaskRunState.running ? now : startedAt,
        to.isTerminal() ? now : finishedAt,
        createdAt);
  }

  /** Mescla chaves no checkpoint (etapas concluídas e ids produzidos). */
  public TaskRun withCheckpoint(Map<String, Object> patch) {
    var merged = new LinkedHashMap<>(checkpoint);
    merged.putAll(patch);
    return new TaskRun(
        id,
        botId,
        botConfigId,
        kind,
        trigger,
        idempotencyKey,
        state,
        pauseReason,
        merged,
        sourcesConsulted,
        sourcesFailed,
        errorSummary,
        startedAt,
        finishedAt,
        createdAt);
  }

  public TaskRun withSources(List<String> consulted, List<String> failed) {
    return new TaskRun(
        id,
        botId,
        botConfigId,
        kind,
        trigger,
        idempotencyKey,
        state,
        pauseReason,
        checkpoint,
        consulted,
        failed,
        errorSummary,
        startedAt,
        finishedAt,
        createdAt);
  }
}
