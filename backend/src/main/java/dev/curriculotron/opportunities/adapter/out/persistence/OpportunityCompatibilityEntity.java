package dev.curriculotron.opportunities.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "opportunity_compatibility")
class OpportunityCompatibilityEntity {
  @Id
  @Column(name = "opportunity_id")
  String opportunityId;

  @Column(name = "criteria_version")
  long criteriaVersion;

  @Column(name = "evaluator_version")
  int evaluatorVersion;

  @Column(name = "evidence_revision")
  long evidenceRevision;

  @Column(name = "assessed_at")
  String assessedAt;

  @Column(name = "role_status")
  String roleStatus;

  @Column(name = "technology_status")
  String technologyStatus;

  @Column(name = "reason_codes")
  String reasonCodes;

  @Column(name = "criteria_snapshot")
  String criteriaSnapshot;

  @Column(name = "evidence_items")
  String evidenceItems;

  @Column(name = "fit")
  String fit;

  @Column(name = "fit_score")
  int fitScore;

  @Column(name = "fit_reasons")
  String fitReasons;

  protected OpportunityCompatibilityEntity() {}
}
