package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.opportunities.domain.CorrectableOpportunityField;
import dev.curriculotron.opportunities.domain.OpportunityCorrectionEntry;
import dev.curriculotron.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/** {@code opportunity_correction}: append-only, imutável também no Hibernate. */
@Entity
@Immutable
@Table(name = "opportunity_correction")
class OpportunityCorrectionEntity extends AssignedIdEntity<String> {
  @Id private String id;

  @Column(name = "opportunity_id", nullable = false)
  private String opportunityId;

  @Enumerated(EnumType.STRING)
  @Column(name = "field_name", nullable = false)
  private CorrectableOpportunityField fieldName;

  @Column(name = "previous_value")
  private String previousValue;

  @Column(name = "corrected_value")
  private String correctedValue;

  private String reason;

  @Column(nullable = false)
  private String author;

  @Column(name = "corrected_at", nullable = false)
  private Instant correctedAt;

  protected OpportunityCorrectionEntity() {}

  static OpportunityCorrectionEntity from(OpportunityCorrectionEntry entry) {
    var entity = new OpportunityCorrectionEntity();
    entity.id = entry.id();
    entity.opportunityId = entry.opportunityId();
    entity.fieldName = entry.fieldName();
    entity.previousValue = entry.previousValue();
    entity.correctedValue = entry.correctedValue();
    entity.reason = entry.reason();
    entity.author = entry.author();
    entity.correctedAt = entry.correctedAt();
    return entity;
  }

  OpportunityCorrectionEntry toDomain() {
    return new OpportunityCorrectionEntry(
        id, opportunityId, fieldName, previousValue, correctedValue, reason, author, correctedAt);
  }

  @Override
  public String getId() {
    return id;
  }
}
