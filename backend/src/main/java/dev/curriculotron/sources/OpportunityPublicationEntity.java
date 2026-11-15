package dev.curriculotron.sources;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "opportunity")
class OpportunityPublicationEntity {
  @Id private String id;

  @Column(name = "published_at")
  private String publishedAt;

  protected OpportunityPublicationEntity() {}

  String publishedAt() {
    return publishedAt;
  }
}
