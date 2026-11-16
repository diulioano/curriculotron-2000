package dev.curriculotron.sources;

import java.time.Instant;

public interface CriteriaStore {
  CriteriaDocument document();

  void save(CriteriaDocument document, Instant at);
}
