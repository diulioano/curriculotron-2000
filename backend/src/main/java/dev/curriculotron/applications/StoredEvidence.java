package dev.curriculotron.applications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "evidence")
class StoredEvidence {
  @Id private String id;

  @Column(nullable = false)
  private String kind;

  @Column(nullable = false)
  private String ref;

  private String quote;

  @Column(name = "captured_at", nullable = false)
  private String capturedAt;

  protected StoredEvidence() {}
}
