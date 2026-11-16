package dev.curriculotron.opportunities.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "compatibility_refresh")
class CompatibilityRefreshEntity {
  @Id
  @Column(name = "id")
  Integer id;

  @Column(name = "target_criteria_version")
  long targetCriteriaVersion;

  @Column(name = "evaluator_version")
  int evaluatorVersion;

  @Column(name = "run_id")
  String runId;

  @Column(name = "state")
  String state;

  @Column(name = "started_at")
  String startedAt;

  @Column(name = "finished_at")
  String finishedAt;

  @Column(name = "error_code")
  String errorCode;

  @Column(name = "assessed_count")
  long assessedCount;

  @Column(name = "remaining_count")
  long remainingCount;

  protected CompatibilityRefreshEntity() {}
}
