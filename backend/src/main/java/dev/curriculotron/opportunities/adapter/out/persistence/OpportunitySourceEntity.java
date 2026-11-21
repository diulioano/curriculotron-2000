package dev.curriculotron.opportunities.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "opportunity_source")
class OpportunitySourceEntity {
  @Id private String id;

  @Column(name = "opportunity_id", nullable = false)
  private String opportunityId;

  @Column(nullable = false)
  private String source;

  @Column(name = "external_id")
  private String externalId;

  @Column(name = "canonical_url", nullable = false)
  private String canonicalUrl;

  @Column(name = "fetched_at", nullable = false)
  private String fetchedAt;

  @Column(name = "raw_excerpt")
  private String rawExcerpt;

  @Column(name = "created_at", nullable = false)
  private String createdAt;

  @Column(name = "updated_at", nullable = false)
  private String updatedAt;

  protected OpportunitySourceEntity() {}

  OpportunitySourceEntity(
      String id,
      String opportunityId,
      String source,
      String externalId,
      String canonicalUrl,
      String fetchedAt,
      String rawExcerpt) {
    this.id = id;
    this.opportunityId = opportunityId;
    this.source = source;
    this.externalId = externalId;
    this.canonicalUrl = canonicalUrl;
    this.fetchedAt = fetchedAt;
    this.rawExcerpt = rawExcerpt;
    this.createdAt = fetchedAt;
    this.updatedAt = fetchedAt;
  }

  String source() {
    return source;
  }

  void refresh(String at) {
    fetchedAt = at;
    updatedAt = at;
  }

  String id() {
    return id;
  }

  String canonicalUrl() {
    return canonicalUrl;
  }

  String fetchedAt() {
    return fetchedAt;
  }

  String createdAt() {
    return createdAt;
  }
}
