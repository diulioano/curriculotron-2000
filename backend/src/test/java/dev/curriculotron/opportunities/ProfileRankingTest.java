package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;

import dev.curriculotron.opportunities.domain.AxisMatch;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Axis;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.EvidenceItem;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Field;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.MentionKind;
import dev.curriculotron.opportunities.domain.CompatibilityEvaluator;
import dev.curriculotron.opportunities.domain.ObjectiveFilter;
import dev.curriculotron.opportunities.domain.RuleBasedRanker;
import dev.curriculotron.opportunities.domain.RuleBasedRanker.AnnouncedSalary;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** US006: ranking por trabalho e stack com perfil, empresa só desempata, exceção júnior. */
class ProfileRankingTest {
  private static final String SAP_REASON = "carga menor e salário maior";
  private static final ObjectiveFilter.Result CONFIRMED_SALARY =
      new ObjectiveFilter.Result(true, false, "Remuneração compatível com o mínimo mensal.");
  private static final AnnouncedSalary CLT_9000 =
      new AnnouncedSalary("clt", "monthly", new BigDecimal("9000"));

  private final RuleBasedRanker ranker = new RuleBasedRanker();
  private final RuleBasedRanker.Criteria criteria =
      new RuleBasedRanker.Criteria(
          Set.of("backend"),
          Set.of("Java", "Spring Boot", "Kotlin"),
          Set.of(),
          Set.of("SAP"),
          true,
          true);
  private final RuleBasedRanker.Profile pleno =
      new RuleBasedRanker.Profile(
          "Java",
          Set.of("Spring Boot"),
          "pleno",
          Set.of("pleno"),
          new BigDecimal("8000"),
          new BigDecimal("12000"));

  @Test
  void scoresPrimaryAboveSecondaryAboveFlexibleTechnology() {
    int primary = rank("pleno", "Outra", null, "Java", CLT_9000).score();
    int secondary = rank("pleno", "Outra", null, "Spring Boot", CLT_9000).score();
    int flexible = rank("pleno", "Outra", null, "Kotlin", CLT_9000).score();

    assertThat(primary).isGreaterThan(secondary);
    assertThat(secondary).isGreaterThan(flexible);
    assertThat(rank("pleno", "Outra", null, "Kotlin", CLT_9000).fit()).isEqualTo("strong");
  }

  @Test
  void priorityCompanyDoesNotChangeScore() {
    var priority = rank("pleno", "SAP", null, "Java", CLT_9000);
    var other = rank("pleno", "Outra", null, "Java", CLT_9000);

    assertThat(priority.score()).isEqualTo(other.score());
    assertThat(priority.reasons()).anyMatch(reason -> reason.contains("desempate"));
  }

  @Test
  void juniorExceptionNeedsFitConfirmedSalaryAboveFloorAndCompanyJustification() {
    var accepted = rank("junior", "SAP", SAP_REASON, "Java", CLT_9000);
    assertThat(accepted.fit()).isEqualTo("strong");
    assertThat(accepted.reasons()).contains("Exceção júnior: " + SAP_REASON);

    assertThat(rank("junior", "SAP", SAP_REASON, "Java", null).fit()).isEqualTo("weak");
    assertThat(
            rank(
                    "junior",
                    "SAP",
                    SAP_REASON,
                    "Java",
                    new AnnouncedSalary("clt", "monthly", new BigDecimal("7999.99")))
                .fit())
        .isEqualTo("weak");
    assertThat(
            rank(
                    "junior",
                    "SAP",
                    SAP_REASON,
                    "Java",
                    new AnnouncedSalary("clt", "yearly", new BigDecimal("200000")))
                .fit())
        .isEqualTo("weak");
    assertThat(
            rank(
                    "junior",
                    "SAP",
                    SAP_REASON,
                    "Java",
                    new AnnouncedSalary("unknown", "monthly", new BigDecimal("9000")))
                .fit())
        .isEqualTo("weak");
    assertThat(
            rank(
                    "junior",
                    "SAP",
                    SAP_REASON,
                    "Java",
                    new AnnouncedSalary("pj", "monthly", new BigDecimal("11000")))
                .fit())
        .isEqualTo("weak");
    var noJustification = rank("junior", "Outra", null, "Java", CLT_9000);
    assertThat(noJustification.fit()).isEqualTo("weak");
    assertThat(noJustification.reasons())
        .anyMatch(reason -> reason.contains("justificativa júnior da empresa"));
    assertThat(rank("junior", "SAP", SAP_REASON, "Python", CLT_9000).fit()).isEqualTo("weak");
  }

  @Test
  void juniorWithoutOwnFloorIsExcluded() {
    var noFloor =
        new RuleBasedRanker.Profile("Java", Set.of(), "pleno", Set.of("pleno"), null, null);
    var result = rank(noFloor, "junior", "SAP", SAP_REASON, "Java", CLT_9000);

    assertThat(result.fit()).isEqualTo("weak");
    assertThat(result.reasons()).anyMatch(reason -> reason.contains("piso júnior"));
  }

  @Test
  void juniorFloorOverridesGeneralMinimumWhenLowerAndSalaryMeetsJuniorFloor() {
    var profileWithLowerPjFloor =
        new RuleBasedRanker.Profile(
            "Java",
            Set.of("Spring Boot"),
            "pleno",
            Set.of("pleno", "senior"),
            new BigDecimal("8000"),
            new BigDecimal("8000"));
    var generalRejected =
        new RuleBasedRanker.Input(
            "backend",
            "SAP",
            "remote",
            null,
            "junior",
            SAP_REASON,
            Set.of(),
            new ObjectiveFilter.Result(
                false, false, "Remuneração explicitamente abaixo do mínimo mensal."));

    var result =
        ranker.rank(
            generalRejected,
            criteria,
            compatibility(item("Java", MentionKind.required)),
            profileWithLowerPjFloor,
            new AnnouncedSalary("pj", "monthly", new BigDecimal("8500")));

    assertThat(result.fit()).isEqualTo("strong");
    assertThat(result.score()).isEqualTo(95);
    assertThat(result.juniorException().qualified()).isTrue();
    assertThat(result.reasons())
        .noneMatch(reason -> reason.contains("abaixo do mínimo mensal"))
        .contains("Remuneração compatível com o piso júnior do perfil.");
  }

  @Test
  void targetLevelsDecideWhichSeniorityIsNormal() {
    var juniorTarget =
        new RuleBasedRanker.Profile(
            "Java", Set.of(), "pleno", Set.of("junior", "pleno"), null, null);

    assertThat(rank(juniorTarget, "junior", "Outra", null, "Java", null).fit()).isEqualTo("strong");
    var senior = rank("senior", "Outra", null, "Java", CLT_9000);
    assertThat(senior.fit()).isEqualTo("weak");
    assertThat(senior.reasons()).anyMatch(reason -> reason.contains("fora dos níveis alvo"));
    assertThat(rank("Sênior", "Outra", null, "Java", CLT_9000).fit()).isEqualTo("weak");
    assertThat(rank(null, "Outra", null, "Java", CLT_9000).fit()).isEqualTo("strong");
  }

  @Test
  void negatedPrimaryTechnologyGetsNoBonus() {
    var result =
        ranker.rank(
            input("pleno", "Outra", null),
            criteria,
            compatibility(
                item("Java", MentionKind.required),
                item("Java", MentionKind.negated),
                item("Kotlin", MentionKind.required)),
            pleno,
            CLT_9000);

    assertThat(result.score()).isEqualTo(rank("pleno", "Outra", null, "Kotlin", CLT_9000).score());
  }

  private RuleBasedRanker.Result rank(
      String level, String company, String justification, String tech, AnnouncedSalary salary) {
    return rank(pleno, level, company, justification, tech, salary);
  }

  private RuleBasedRanker.Result rank(
      RuleBasedRanker.Profile profile,
      String level,
      String company,
      String justification,
      String tech,
      AnnouncedSalary salary) {
    boolean known = criteria.stack().contains(tech);
    var compatibility =
        known
            ? compatibility(item(tech, MentionKind.required))
            : new CompatibilityEvaluator.Result(
                new AxisMatch(Axis.matched, List.of(), List.of("role_match")),
                new AxisMatch(Axis.mismatched, List.of(), List.of("technology_mismatch")));
    return ranker.rank(
        input(level, company, justification), criteria, compatibility, profile, salary);
  }

  private static RuleBasedRanker.Input input(String level, String company, String justification) {
    return new RuleBasedRanker.Input(
        "backend", company, "remote", null, level, justification, Set.of(), CONFIRMED_SALARY);
  }

  private static CompatibilityEvaluator.Result compatibility(EvidenceItem... items) {
    return new CompatibilityEvaluator.Result(
        new AxisMatch(Axis.matched, List.of(), List.of("role_match")),
        new AxisMatch(Axis.matched, List.of(items), List.of("technology_match")));
  }

  private static EvidenceItem item(String technology, MentionKind kind) {
    return new EvidenceItem(
        "ev-1",
        "manual",
        null,
        null,
        Field.description,
        "requisitos",
        "Requisitos: " + technology,
        technology,
        technology,
        kind,
        null,
        null);
  }
}
