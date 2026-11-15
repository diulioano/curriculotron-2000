package dev.curriculotron.salary.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "salary_refresh_request")
@IdClass(SalaryRefreshRequestId.class)
class SalaryRefreshRequestEntity {
  @Id
  @Column(name = "opportunity_id", nullable = false)
  private String opportunityId;

  @Id
  @Column(name = "request_key", nullable = false)
  private String requestKey;

  @Column(name = "task_run_id", nullable = false)
  private String taskRunId;

  protected SalaryRefreshRequestEntity() {}

  SalaryRefreshRequestEntity(String opportunityId, String requestKey, String taskRunId) {
    this.opportunityId = opportunityId;
    this.requestKey = requestKey;
    this.taskRunId = taskRunId;
  }

  String taskRunId() {
    return taskRunId;
  }
}
