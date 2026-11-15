package dev.curriculotron.salary.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "opportunity_salary_link")
class OpportunitySalaryLinkEntity {
  @Id private String id;

  @Column(name = "opportunity_id", nullable = false)
  private String opportunityId;

  @Column(name = "estimate_id", nullable = false)
  private String estimateId;

  @Column(name = "applied_at", nullable = false)
  private String appliedAt;

  @Column(name = "superseded_at")
  String supersededAt;

  protected OpportunitySalaryLinkEntity() {}

  OpportunitySalaryLinkEntity(
      String id, String opportunityId, String estimateId, String appliedAt) {
    this.id = id;
    this.opportunityId = opportunityId;
    this.estimateId = estimateId;
    this.appliedAt = appliedAt;
  }

  String estimateId() {
    return estimateId;
  }
}
