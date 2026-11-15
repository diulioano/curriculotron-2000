package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;

import dev.curriculotron.opportunities.application.AssessOpportunityService;
import dev.curriculotron.opportunities.application.AssessmentInputService;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Axis;
import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.opportunities.domain.RuleBasedRanker;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.sources.SearchCriteriaRepository;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AssessOpportunityServiceTest {
  private final AssessOpportunityService service =
      new AssessOpportunityService(null, null, null, new AppClock());

  @Test
  void shouldPreserveJuniorExceptionWithoutFabricatingConfiguredRole() {
    var result =
        service.calculate(
            input(
                "Backend Developer Java Júnior", "SAP", Set.of("Java"), "Justificativa aprovada"));
    assertThat(result.roleStatus()).isEqualTo(Axis.matched);
    assertThat(result.fit()).isEqualTo("strong");
    assertThat(result.evidenceItems())
        .anyMatch(e -> e.quote().contains("Backend Developer Java Júnior"));
    assertThat(
            service
                .calculate(input("Backend Developer Java Júnior", "SAP", Set.of("Java"), null))
                .fit())
        .isEqualTo("weak");
    assertThat(
            service.calculate(input("Service Designer", "SAP", Set.of("Java"), null)).roleStatus())
        .isEqualTo(Axis.mismatched);
  }

  @Test
  void shouldExplainUnrestrictedTechnologyWithoutClaimingAnExplicitMatch() {
    var result = service.calculate(input("Backend Developer", "X", Set.of(), null));
    assertThat(result.technologyStatus()).isEqualTo(Axis.unrestricted);
    assertThat(result.fitReasons()).contains("Critérios sem restrição tecnológica.");
    assertThat(result.fitReasons()).noneMatch(reason -> reason.startsWith("Tecnologia explícita"));
  }

  private AssessmentInputService.Input input(
      String title, String company, Set<String> stack, String junior) {
    var job =
        new Opportunity(
            "o", title, company, null, "remote", "unknown", null, List.of(), List.of(), List.of(),
            "none", "open", null);
    var evidence =
        new VacancyEvidence(
            "e",
            "o",
            null,
            "fixture",
            title,
            null,
            "Requirements: Java",
            VacancyEvidence.Completeness.complete,
            null,
            1,
            null,
            null);
    var criteria =
        new RuleBasedRanker.Criteria(
            Set.of("Backend Developer Pleno"), stack, Set.of(), Set.of("SAP"), true, true);
    return new AssessmentInputService.Input(
        job,
        List.of(evidence),
        1,
        new SearchCriteriaRepository.Settings(
            criteria, new BigDecimal("6000"), new BigDecimal("9000"), LocalTime.of(7, 30), 1),
        junior);
  }
}
