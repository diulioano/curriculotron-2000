package dev.curriculotron.applications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "status_change")
class StoredStatusChange {
  @Id private String id;

  @Column(name = "application_id", nullable = false)
  private String applicationId;

  @jakarta.persistence.Convert(converter = ApplicationStatusConverter.class)
  @Column(name = "from_status")
  private ApplicationStatus fromStatus;

  @jakarta.persistence.Convert(converter = ApplicationStatusConverter.class)
  @Column(name = "to_status", nullable = false)
  private ApplicationStatus toStatus;

  @Column(nullable = false)
  private String author;

  @Column(name = "evidence_id")
  private String evidenceId;

  @Column(nullable = false)
  private String reason;

  @Column(name = "at", nullable = false)
  private String at;

  protected StoredStatusChange() {}

  StoredStatusChange(
      String id,
      String applicationId,
      ApplicationStatus fromStatus,
      ApplicationStatus toStatus,
      String author,
      String evidenceId,
      String reason,
      String at) {
    this.id = id;
    this.applicationId = applicationId;
    this.fromStatus = fromStatus;
    this.toStatus = toStatus;
    this.author = author;
    this.evidenceId = evidenceId;
    this.reason = reason;
    this.at = at;
  }
}
