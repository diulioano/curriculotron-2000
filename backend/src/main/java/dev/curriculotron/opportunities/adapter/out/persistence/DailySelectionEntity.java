package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.shared.persistence.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.List;

@Entity
@Table(name = "daily_selection")
class DailySelectionEntity {
  @EmbeddedId private DailySelectionId id;

  @Column(nullable = false)
  private int rank;

  @Convert(converter = StringListJsonConverter.class)
  @Column(nullable = false)
  private List<String> reason;

  protected DailySelectionEntity() {}

  DailySelectionEntity(String date, String opportunityId, int rank, List<String> reason) {
    this.id = new DailySelectionId(date, opportunityId);
    this.rank = rank;
    this.reason = List.copyOf(reason);
  }
}
