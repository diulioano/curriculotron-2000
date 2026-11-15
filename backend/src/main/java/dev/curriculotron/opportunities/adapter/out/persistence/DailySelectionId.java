package dev.curriculotron.opportunities.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
class DailySelectionId implements Serializable {
  @Column(name = "local_date", nullable = false)
  private String localDate;

  @Column(name = "opportunity_id", nullable = false)
  private String opportunityId;

  protected DailySelectionId() {}

  DailySelectionId(String localDate, String opportunityId) {
    this.localDate = localDate;
    this.opportunityId = opportunityId;
  }

  String opportunityId() {
    return opportunityId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof DailySelectionId id)) return false;
    return Objects.equals(localDate, id.localDate)
        && Objects.equals(opportunityId, id.opportunityId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(localDate, opportunityId);
  }
}
