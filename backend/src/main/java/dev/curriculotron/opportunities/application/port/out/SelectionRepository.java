package dev.curriculotron.opportunities.application.port.out;

import java.time.LocalDate;
import java.util.List;

public interface SelectionRepository {
  /** Dias locais em que a vaga foi selecionada, do mais antigo ao mais recente (ordem estável). */
  List<LocalDate> selectedDays(String opportunityId);

  List<String> selected(LocalDate date);

  List<String> visibleSelected(LocalDate date, long criteriaVersion, int evaluatorVersion);

  List<String> eligible(long criteriaVersion, int evaluatorVersion, int limit);

  List<String> recommended(long criteriaVersion, int evaluatorVersion);

  List<String> secondary(long criteriaVersion, int evaluatorVersion);

  void select(LocalDate date, String opportunityId, int rank);
}
