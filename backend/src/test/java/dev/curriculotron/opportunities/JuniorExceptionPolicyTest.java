package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;

import dev.curriculotron.opportunities.domain.AxisMatch;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Axis;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.EvidenceItem;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Field;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.MentionKind;
import dev.curriculotron.opportunities.domain.CompatibilityEvaluator;
import dev.curriculotron.opportunities.domain.JuniorExceptionPolicy;
import dev.curriculotron.opportunities.domain.JuniorExceptionPolicy.JustificationStatus;
import dev.curriculotron.opportunities.domain.JuniorExceptionPolicy.Outcome;
import dev.curriculotron.opportunities.domain.JuniorExceptionPolicy.SalaryStatus;
import dev.curriculotron.opportunities.domain.ObjectiveFilter;
import dev.curriculotron.opportunities.domain.RuleBasedRanker;
import dev.curriculotron.opportunities.domain.RuleBasedRanker.AnnouncedSalary;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** US006: decisão tipada da exceção júnior compartilhada entre ranking e detalhe. */
class JuniorExceptionPolicyTest {
  private static final String SAP_REASON = "carga de trabalho menor";
  private static final RuleBasedRanker.Profile PROFILE =
      new RuleBasedRanker.Profile(
          "Java", Set.of(), "pleno", Set.of("pleno"), new BigDecimal("7000"), null);
  private static final RuleBasedRanker.Criteria CRITERIA =
      new RuleBasedRanker.Criteria(
          Set.of("backend"), Set.of("Java"), Set.of(), Set.of(), true, true);

  private final RuleBasedRanker ranker = new RuleBasedRanker();

  @ParameterizedTest
  @CsvSource(
      nullValues = "null",
      value = {
        "clt, monthly, 7000, 9000, confirmed",
        "clt, monthly, 6000, 8000, range_crosses_floor",
        "clt, monthly, null, 7000, range_crosses_floor",
        "clt, monthly, 5000, 6000, below_floor",
        "clt, monthly, null, null, not_announced",
        "clt, yearly, 200000, null, not_announced",
        "clt, unknown, null, null, not_announced",
        "pj, monthly, 20000, null, floor_missing",
        "unknown, monthly, 9000, null, contract_unknown",
        "null, null, null, null, contract_unknown"
      })
  void classifiesAnnouncedSalaryAgainstOwnFloor(
      String contract, String unit, BigDecimal min, BigDecimal max, SalaryStatus expected) {
    var decision =
        JuniorExceptionPolicy.decide(
            true, PROFILE, new AnnouncedSalary(contract, unit, min, max), true, SAP_REASON);

    assertThat(decision.salary().status()).isEqualTo(expected);
    assertThat(decision.qualified()).isEqualTo(expected == SalaryStatus.confirmed);
  }

  @Test
  void keepsCompanyReasonWhenOnlySalaryFails() {
    var decision = JuniorExceptionPolicy.decide(true, PROFILE, clt("5000", null), true, SAP_REASON);

    assertThat(decision.outcome()).isEqualTo(Outcome.excluded);
    assertThat(decision.justification().status()).isEqualTo(JustificationStatus.accepted);
    assertThat(decision.justification().text()).isEqualTo(SAP_REASON);
    assertThat(decision.salary().floor()).isEqualByComparingTo("7000");
    assertThat(decision.missing())
        .containsExactly("salário anunciado confirmado acima do piso júnior");
  }

  @Test
  void distinguishesMissingJustificationFromCriteriaThatRejectJunior() {
    var missing = JuniorExceptionPolicy.decide(true, PROFILE, clt("9000", null), true, "  ");
    var rejected =
        JuniorExceptionPolicy.decide(true, PROFILE, clt("9000", null), false, SAP_REASON);

    assertThat(missing.justification().status()).isEqualTo(JustificationStatus.missing);
    assertThat(missing.justification().text()).isNull();
    assertThat(rejected.justification().status())
        .isEqualTo(JustificationStatus.not_accepted_by_criteria);
    assertThat(rejected.justification().text()).isEqualTo(SAP_REASON);
    assertThat(rejected.qualified()).isFalse();
  }

  @Test
  void workMismatchExcludesEvenWithSalaryAndReason() {
    var decision =
        JuniorExceptionPolicy.decide(false, PROFILE, clt("9000", null), true, SAP_REASON);

    assertThat(decision.qualified()).isFalse();
    assertThat(decision.missing()).containsExactly("fit de cargo e stack");
  }

  @Test
  void rankerExposesTheDecisionItUsedOnlyForJuniorOutsideTargets() {
    var salary = clt("6000", "8000");
    var juniorTarget =
        new RuleBasedRanker.Profile("Java", Set.of(), "pleno", Set.of("junior"), null, null);

    var junior = ranker.rank(input("Júnior"), CRITERIA, matched(), PROFILE, salary);

    assertThat(junior.fit()).isEqualTo("weak");
    assertThat(junior.juniorException().salary().status())
        .isEqualTo(SalaryStatus.range_crosses_floor);
    assertThat(junior.juniorException().justification().text()).isEqualTo(SAP_REASON);
    assertThat(junior.juniorException().workFit()).isTrue();
    assertThat(ranker.rank(input("pleno"), CRITERIA, matched(), PROFILE, salary).juniorException())
        .isNull();
    assertThat(ranker.rank(input(null), CRITERIA, matched(), PROFILE, salary).juniorException())
        .isNull();
    assertThat(
            ranker
                .rank(input("junior"), CRITERIA, matched(), juniorTarget, salary)
                .juniorException())
        .isNull();
    assertThat(ranker.rank(input("junior"), CRITERIA, matched()).juniorException()).isNull();
  }

  @Test
  void qualifiedDecisionMatchesRankerVerdict() {
    var result = ranker.rank(input("junior"), CRITERIA, matched(), PROFILE, clt("8000", null));

    assertThat(result.fit()).isEqualTo("strong");
    assertThat(result.juniorException().qualified()).isTrue();
    assertThat(result.reasons()).contains("Exceção júnior: " + SAP_REASON);
  }

  private static AnnouncedSalary clt(String min, String max) {
    return new AnnouncedSalary(
        "clt", "monthly", new BigDecimal(min), max == null ? null : new BigDecimal(max));
  }

  private static RuleBasedRanker.Input input(String seniority) {
    return new RuleBasedRanker.Input(
        "backend",
        "SAP",
        "remote",
        null,
        seniority,
        SAP_REASON,
        Set.of(),
        new ObjectiveFilter.Result(true, false, "Remuneração compatível com o mínimo mensal."));
  }

  private static CompatibilityEvaluator.Result matched() {
    var item =
        new EvidenceItem(
            "ev-1",
            "manual",
            null,
            null,
            Field.description,
            "requisitos",
            "Requisitos: Java",
            "Java",
            "Java",
            MentionKind.required,
            null,
            null);
    return new CompatibilityEvaluator.Result(
        new AxisMatch(Axis.matched, List.of(), List.of("role_match")),
        new AxisMatch(Axis.matched, List.of(item), List.of("technology_match")));
  }
}
