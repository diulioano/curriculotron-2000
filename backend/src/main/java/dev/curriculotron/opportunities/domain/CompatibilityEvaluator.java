package dev.curriculotron.opportunities.domain;

import java.util.List;

public final class CompatibilityEvaluator {
  public record Result(AxisMatch role, AxisMatch technology) {
    public boolean compatible() {
      return role.status() != CompatibilityAssessment.Axis.mismatched
          && technology.status() != CompatibilityAssessment.Axis.mismatched;
    }

    public boolean confirmed() {
      return role.status() == CompatibilityAssessment.Axis.matched
          && (technology.status() == CompatibilityAssessment.Axis.matched
              || technology.status() == CompatibilityAssessment.Axis.unrestricted);
    }
  }

  private final RoleMatcher roles = new RoleMatcher();
  private final TechnologyMatcher technologies = new TechnologyMatcher();

  public Result evaluate(
      List<VacancyEvidence> evidence, List<String> targetRoles, List<String> stack) {
    return new Result(roles.match(evidence, targetRoles), technologies.match(evidence, stack));
  }
}
