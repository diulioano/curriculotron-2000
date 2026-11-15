package dev.curriculotron.opportunities.application.port.out;

import java.time.LocalDate;
import java.util.List;

public interface SelectionRepository {
  List<String> selected(LocalDate date);

  List<String> eligible(int limit);

  List<String> secondary();

  void select(LocalDate date, String opportunityId, int rank);
}
