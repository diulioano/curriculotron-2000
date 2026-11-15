package dev.curriculotron.opportunities.domain;

import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Axis;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.EvidenceItem;
import java.util.List;
import java.util.Objects;

public record AxisMatch(Axis status, List<EvidenceItem> evidence, List<String> reasons) {
  public AxisMatch {
    Objects.requireNonNull(status);
    evidence = List.copyOf(evidence);
    reasons = List.copyOf(reasons);
  }
}
