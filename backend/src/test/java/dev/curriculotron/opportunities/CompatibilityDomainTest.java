package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Axis;
import dev.curriculotron.opportunities.domain.CompatibilityRefresh;
import dev.curriculotron.opportunities.domain.RuleBasedRanker;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CompatibilityDomainTest {
  @Test
  void shouldRejectUnrestrictedRoleAndInvalidEvidence() {
    assertThatThrownBy(() -> assessment(Axis.unrestricted, Axis.matched, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            new VacancyEvidence(
                "e",
                "o",
                null,
                "manual",
                null,
                null,
                "x".repeat(100_001),
                VacancyEvidence.Completeness.complete,
                null,
                1,
                null,
                null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            new CompatibilityRefresh(
                1, 1, null, CompatibilityRefresh.State.running, null, null, null, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            new CompatibilityRefresh(
                1, 1, null, CompatibilityRefresh.State.ready, null, null, null, 0, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldKeepUnknownSeparateFromMismatchAndPreserveReasons() {
    var reasons = new ArrayList<>(List.of("incomplete_description"));
    var pending = assessment(Axis.matched, Axis.unknown, reasons);
    reasons.clear();
    assertThat(pending.reasonCodes()).containsExactly("incomplete_description");
    assertThat(pending.compatible()).isTrue();
    assertThat(pending.confirmed()).isFalse();
    assertThat(pending.current(1, 1)).isTrue();
    assertThat(pending.current(2, 1)).isFalse();
    assertThatThrownBy(() -> assessment(Axis.matched, Axis.unrestricted, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    var unrestrictedCriteria =
        new RuleBasedRanker.Criteria(Set.of("Backend"), Set.of(), Set.of(), Set.of(), true, true);
    var unrestricted =
        new CompatibilityAssessment(
            "o",
            1,
            1,
            1,
            Instant.EPOCH,
            Axis.matched,
            Axis.unrestricted,
            List.of(),
            new CompatibilityAssessment.CriteriaSnapshot(
                unrestrictedCriteria, new BigDecimal("6000"), new BigDecimal("9000")),
            List.of(),
            "weak",
            0,
            List.of());
    assertThat(unrestricted.confirmed()).isTrue();
    assertThat(assessment(Axis.mismatched, Axis.matched, List.of()).compatible()).isFalse();
  }

  private CompatibilityAssessment assessment(Axis role, Axis tech, List<String> reasons) {
    var criteria =
        new RuleBasedRanker.Criteria(
            Set.of("Backend"), Set.of("Java"), Set.of(), Set.of(), true, true);
    return new CompatibilityAssessment(
        "o",
        1,
        CompatibilityAssessment.EVALUATOR_VERSION,
        1,
        Instant.EPOCH,
        role,
        tech,
        reasons,
        new CompatibilityAssessment.CriteriaSnapshot(
            criteria, new BigDecimal("6000"), new BigDecimal("9000")),
        List.of(),
        "weak",
        0,
        List.of());
  }
}
