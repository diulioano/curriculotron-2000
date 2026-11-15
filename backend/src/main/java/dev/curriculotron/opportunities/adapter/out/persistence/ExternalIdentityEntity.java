package dev.curriculotron.opportunities.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "opportunity_external_identity")
@IdClass(ExternalIdentityId.class)
class ExternalIdentityEntity {
  @Id
  @Column(nullable = false)
  private String source;

  @Id
  @Column(name = "external_id", nullable = false)
  private String externalId;

  @Column(name = "opportunity_id", nullable = false)
  private String opportunityId;

  protected ExternalIdentityEntity() {}

  ExternalIdentityEntity(String source, String externalId, String opportunityId) {
    this.source = source;
    this.externalId = externalId;
    this.opportunityId = opportunityId;
  }

  String opportunityId() {
    return opportunityId;
  }
}
