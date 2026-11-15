package dev.curriculotron.opportunities.application;

import dev.curriculotron.opportunities.application.port.out.SelectionRepository;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DailySelectionService {
  private final SelectionRepository selections;
  private final WriteExecutor writer;
  private final AppClock clock;

  public DailySelectionService(
      SelectionRepository selections, WriteExecutor writer, AppClock clock) {
    this.selections = selections;
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
        for (String id : selections.eligible(Integer.MAX_VALUE)) {
          if (accepts.test(id) && count < 10) selections.select(date, id, ++count);
        }
      }
      return selections.selected(date);
    });
  }

  public List<String> secondary() {
    return selections.secondary();
  }
}
