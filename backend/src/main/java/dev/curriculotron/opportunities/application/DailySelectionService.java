package dev.curriculotron.opportunities.application;

import dev.curriculotron.opportunities.application.port.out.SelectionRepository;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.sources.SearchCriteriaRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailySelectionService {
  private final SelectionRepository selections;
  private final SearchCriteriaRepository criteria;
  private final WriteExecutor writer;
  private final AppClock clock;

  public DailySelectionService(
      SelectionRepository selections,
      SearchCriteriaRepository criteria,
      WriteExecutor writer,
      AppClock clock) {
    this.selections = selections;
    this.criteria = criteria;
    this.writer = writer;
    this.clock = clock;
  }

  public List<String> selectToday() {
    return selectMatchingToday(id -> true);
  }

  public List<String> selectMatchingToday(java.util.function.Predicate<String> accepts) {
    return writer.write(() -> {
      var date = clock.localDate();
      int count = selections.selected(date).size();
      if (count < 10) {
        long version = criteria.current().version();
        for (String id :
            selections.eligible(
                version, CompatibilityAssessment.EVALUATOR_VERSION, Integer.MAX_VALUE)) {
          if (count >= 10) break;
          if (accepts.test(id)) selections.select(date, id, ++count);
        }
      }
      return selections.selected(date);
    });
  }

  @Transactional(readOnly = true)
  public List<String> secondary() {
    long version = criteria.current().version();
    return selections.secondary(version, CompatibilityAssessment.EVALUATOR_VERSION);
  }
}
