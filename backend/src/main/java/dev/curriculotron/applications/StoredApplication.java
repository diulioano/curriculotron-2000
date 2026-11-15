package dev.curriculotron.applications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "application")
class StoredApplication {
  @Id private String id;

  @Column(name = "opportunity_id", nullable = false, unique = true)
  private String opportunityId;

  @Column(name = "confirmed_at", nullable = false)
  private String confirmedAt;

  @Column(name = "confirmation_origin", nullable = false)
  private String confirmationOrigin;

  @jakarta.persistence.Convert(converter = ApplicationStatusConverter.class)
  @Column(nullable = false)
  private ApplicationStatus status;

  @Column(name = "next_action")
  private String nextAction;

  @Column(name = "next_action_due_at")
  private String nextActionDueAt;

  @Column(name = "due_precision", nullable = false)
  @jakarta.persistence.Convert(converter = DuePrecisionConverter.class)
  private DuePrecision duePrecision;

  @Column(name = "created_at", nullable = false)
  private String createdAt;

  @Column(name = "updated_at", nullable = false)
  private String updatedAt;

  protected StoredApplication() {}

  StoredApplication(
      String id,
      String opportunityId,
      String confirmedAt,
      String confirmationOrigin,
      ApplicationStatus status,
      DuePrecision duePrecision,
      String createdAt,
      String updatedAt) {
    this.id = id;
    this.opportunityId = opportunityId;
    this.confirmedAt = confirmedAt;
    this.confirmationOrigin = confirmationOrigin;
    this.status = status;
    this.duePrecision = duePrecision;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  void updateStatus(ApplicationStatus status, String updatedAt) {
    this.status = status;
    this.updatedAt = updatedAt;
  }

  ApplicationStatus status() {
    return status;
  }

  void updateNextAction(String text, String dueAt, DuePrecision precision, String updatedAt) {
    this.nextAction = text;
    this.nextActionDueAt = dueAt;
    this.duePrecision = precision;
    this.updatedAt = updatedAt;
  }
}
