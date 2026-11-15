package dev.curriculotron.orchestration.adapter.out.persistence;

import dev.curriculotron.orchestration.domain.PauseReason;
import dev.curriculotron.orchestration.domain.TaskRun;
import dev.curriculotron.orchestration.domain.TaskRunState;
import dev.curriculotron.orchestration.domain.TaskRunTrigger;
import dev.curriculotron.shared.persistence.AssignedIdEntity;
import dev.curriculotron.shared.persistence.JsonObjectConverter;
import dev.curriculotron.shared.persistence.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "task_run")
class TaskRunEntity extends AssignedIdEntity<String> {

  @Id private String id;

  @Column(name = "bot_id")
  private String botId;

  @Column(name = "bot_config_id")
  private String botConfigId;

  private String kind;

  @Enumerated(EnumType.STRING)
  @Column(name = "trigger_kind")
  private TaskRunTrigger trigger;

  @Column(name = "idempotency_key", updatable = false)
  private String idempotencyKey;

  @Enumerated(EnumType.STRING)
  private TaskRunState state;

  @Enumerated(EnumType.STRING)
  @Column(name = "pause_reason")
  private PauseReason pauseReason;

  @Convert(converter = JsonObjectConverter.class)
  private Map<String, Object> checkpoint;

  @Convert(converter = StringListJsonConverter.class)
  @Column(name = "sources_consulted")
  private List<String> sourcesConsulted;

  @Convert(converter = StringListJsonConverter.class)
  @Column(name = "sources_failed")
  private List<String> sourcesFailed;

  @Column(name = "error_summary")
  private String errorSummary;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  protected TaskRunEntity() {}

  static TaskRunEntity create(String id) {
    TaskRunEntity e = new TaskRunEntity();
    e.id = id;
    return e;
  }

  void apply(TaskRun r, Instant now) {
    botId = r.botId();
    botConfigId = r.botConfigId();
    kind = r.kind();
    trigger = r.trigger();
    idempotencyKey = r.idempotencyKey();
    state = r.state();
    pauseReason = r.pauseReason();
    checkpoint = r.checkpoint();
    sourcesConsulted = r.sourcesConsulted();
    sourcesFailed = r.sourcesFailed();
    errorSummary = r.errorSummary();
    startedAt = r.startedAt();
    finishedAt = r.finishedAt();
    createdAt = r.createdAt();
    updatedAt = now;
  }

  TaskRun toDomain() {
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
        sourcesConsulted,
        sourcesFailed,
        errorSummary,
        startedAt,
        finishedAt,
        createdAt);
  }

  @Override
  public String getId() {
    return id;
  }
}
