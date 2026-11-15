package dev.curriculotron.opportunities.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "opportunity_url_identity")
class UrlIdentityEntity {
  @Id
  @Column(name = "canonical_url")
  private String canonicalUrl;

  @Column(name = "opportunity_id", nullable = false)
  private String opportunityId;

  protected UrlIdentityEntity() {}

  UrlIdentityEntity(String canonicalUrl, String opportunityId) {
    this.canonicalUrl = canonicalUrl;
    this.opportunityId = opportunityId;
  }

  String opportunityId() {
    return opportunityId;
  }
}
