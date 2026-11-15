package dev.curriculotron.audit.adapter.out.persistence;

import dev.curriculotron.audit.domain.ActivityRecord;
import dev.curriculotron.audit.domain.ActivityResult;
import dev.curriculotron.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/** {@code activity_log}: imutável também no Hibernate (nunca gera UPDATE). */
@Entity
@Immutable
@Table(name = "activity_log")
class ActivityLogEntity extends AssignedIdEntity<String> {

  @Id private String id;

  @Column(name = "task_run_id")
  private String taskRunId;

  private String author;
  private String action;

  @Column(name = "target_type")
  private String targetType;

  @Column(name = "target_id")
  private String targetId;

  @Column(name = "evidence_ref")
  private String evidenceRef;

  @Enumerated(EnumType.STRING)
  private ActivityResult result;

  private String error;
  private Instant at;

  protected ActivityLogEntity() {}

  static ActivityLogEntity from(ActivityRecord r) {
    ActivityLogEntity e = new ActivityLogEntity();
    e.id = r.id();
    e.taskRunId = r.taskRunId();
    e.author = r.author();
    e.action = r.action();
    e.targetType = r.targetType();
    e.targetId = r.targetId();
    e.evidenceRef = r.evidenceRef();
    e.result = r.result();
    e.error = r.error();
    e.at = r.at();
    return e;
  }

  ActivityRecord toDomain() {
    return new ActivityRecord(
        id, taskRunId, author, action, targetType, targetId, evidenceRef, result, error, at);
  }

  @Override
  public String getId() {
    return id;
  }
}
