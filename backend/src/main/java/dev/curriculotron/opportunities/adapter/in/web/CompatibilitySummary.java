package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Axis;
import java.util.List;
import java.util.Objects;

public record CompatibilitySummary(
    Long criteriaVersion,
    Integer evaluatorVersion,
    String assessedAt,
    boolean current,
    Axis roleStatus,
    Axis technologyStatus,
    List<String> reasonCodes) {
  public CompatibilitySummary {
    Objects.requireNonNull(roleStatus);
    Objects.requireNonNull(technologyStatus);
    reasonCodes = List.copyOf(reasonCodes);
  }

  public static CompatibilitySummary notAssessed() {
    return new CompatibilitySummary(
        null, null, null, false, Axis.unknown, Axis.unknown, List.of("not_assessed"));
  }
}
