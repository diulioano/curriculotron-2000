package dev.curriculotron.salary.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "salary_lookup_attempt")
class SalaryLookupAttemptEntity {
  @Id private String id;

  @Column(name = "profile_key", nullable = false)
  private String profileKey;

  @Column(name = "task_run_id", nullable = false)
  private String taskRunId;

  @Column(name = "requested_at", nullable = false)
  private String requestedAt;

  @Column(nullable = false)
  private String state;

  @Column(name = "completed_at")
  private String completedAt;

  @Column(name = "estimate_id")
  private String estimateId;

  protected SalaryLookupAttemptEntity() {}

  SalaryLookupAttemptEntity(String id, String profileKey, String taskRunId, String requestedAt) {
    this.id = id;
    this.profileKey = profileKey;
    this.taskRunId = taskRunId;
    this.requestedAt = requestedAt;
    this.state = "running";
  }
}
