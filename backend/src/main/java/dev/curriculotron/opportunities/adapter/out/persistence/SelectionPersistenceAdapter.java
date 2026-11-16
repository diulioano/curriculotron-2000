package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.opportunities.application.port.out.SelectionRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class SelectionPersistenceAdapter implements SelectionRepository {
  private final SelectionJpaRepository selections;

  SelectionPersistenceAdapter(SelectionJpaRepository selections) {
    this.selections = selections;
  }

  @Override
  public List<String> selected(LocalDate date) {
    return selections.selected(date.toString());
  }

  @Override
  public List<String> visibleSelected(LocalDate date, long criteriaVersion, int evaluatorVersion) {
    return selections.visibleSelected(date.toString(), criteriaVersion, evaluatorVersion);
  }

  @Override
  public List<String> eligible(long criteriaVersion, int evaluatorVersion, int limit) {
    return selections.eligible(criteriaVersion, evaluatorVersion).stream().limit(limit).toList();
  }

  @Override
  public List<String> secondary(long criteriaVersion, int evaluatorVersion) {
    return selections.secondary(criteriaVersion, evaluatorVersion);
  }

  @Override
  public void select(LocalDate date, String id, int rank) {
    var opportunity = selections.findOpportunity(id).orElseThrow();
    selections.save(new DailySelectionEntity(date.toString(), id, rank, opportunity.fitReasons()));
  }
}
