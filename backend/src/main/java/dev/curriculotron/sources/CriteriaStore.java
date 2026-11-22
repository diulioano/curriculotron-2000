package dev.curriculotron.sources;

import java.time.Instant;

public interface CriteriaStore {
  CriteriaDocument document();

  java.util.List<String> priorityCompanies();

  void replacePriorityCompanies(java.util.List<String> names, Instant at);

  void save(CriteriaDocument document, Instant at);
}
