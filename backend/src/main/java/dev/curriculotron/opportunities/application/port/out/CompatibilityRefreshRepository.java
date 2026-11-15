package dev.curriculotron.opportunities.application.port.out;

import dev.curriculotron.opportunities.domain.CompatibilityRefresh;

public interface CompatibilityRefreshRepository {
  CompatibilityRefresh current();

  void save(CompatibilityRefresh refresh);
}
