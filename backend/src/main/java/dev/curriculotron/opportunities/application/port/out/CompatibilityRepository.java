package dev.curriculotron.opportunities.application.port.out;

import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import java.util.List;
import java.util.Optional;

public interface CompatibilityRepository {
  Optional<CompatibilityAssessment> find(String opportunityId);

  boolean saveIfCurrent(CompatibilityAssessment assessment);

  List<String> pending(long criteriaVersion, int evaluatorVersion, String afterId, int limit);

  long pendingCount(long criteriaVersion, int evaluatorVersion);
}
