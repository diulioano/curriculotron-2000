package dev.curriculotron.opportunities.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "opportunity_evidence")
class OpportunityEvidenceEntity {
  @Id
  @Column(name = "id")
  String id;

  @Column(name = "opportunity_id")
  String opportunityId;

  @Column(name = "source_id")
  String sourceId;

  @Column(name = "source_kind")
  String sourceKind;

  @Column(name = "title")
  String title;

  @Column(name = "location")
  String location;

  @Column(name = "description_text")
  String descriptionText;

  @Column(name = "description_completeness")
  String descriptionCompleteness;

  @Column(name = "completeness_reason")
  String completenessReason;

  @Column(name = "content_revision")
  long contentRevision;

  @Column(name = "fetched_at")
  String fetchedAt;

  protected OpportunityEvidenceEntity() {}
}
