package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;

import dev.curriculotron.opportunities.domain.RoleMatcher;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoleMatcherTest {
  @Test
  void shouldMatchLiteralUncataloguedRoleAndIgnoreOtherTeamSpecialization() {
    var matcher = new RoleMatcher();
    var literal =
        new VacancyEvidence(
            "a",
            "o",
            null,
            "fixture",
            "Analista de Dados",
            null,
            "Responsabilidades: analisar dados.",
            VacancyEvidence.Completeness.complete,
            null,
            1,
            null,
            null);
    assertThat(matcher.match(List.of(literal), List.of("Analista de Dados")).status().name())
        .isEqualTo("matched");
    var incidental =
        new VacancyEvidence(
            "b",
            "o",
            null,
            "fixture",
            "Software Engineer",
            null,
            "Our other team does backend development. Responsibilities: frontend development.",
            VacancyEvidence.Completeness.complete,
            null,
            1,
            null,
            null);
    assertThat(matcher.match(List.of(incidental), List.of("Backend Developer")).status().name())
        .isEqualTo("mismatched");
    var negated =
        new VacancyEvidence(
            "c",
            "o",
            null,
            "fixture",
            "Software Engineer",
            null,
            "Responsibilities: not backend development.",
            VacancyEvidence.Completeness.complete,
            null,
            1,
            null,
            null);
    assertThat(matcher.match(List.of(negated), List.of("Backend Developer")).status().name())
        .isEqualTo("unknown");
  }

  @Test
  void shouldUseConfiguredFunctionsWithoutTechnologyOrSeniorityGate() {
    var matcher = new RoleMatcher();
    for (var posting : CompatibilityFixtures.postings()) {
      if (posting.id().equals("legacy")) continue;
      var evidence =
          new VacancyEvidence(
              posting.id(),
              "o",
              null,
              "fixture",
              posting.title(),
              null,
              posting.text(),
              VacancyEvidence.Completeness.valueOf(posting.completeness()),
              null,
              1,
              null,
              null);
      assertThat(matcher.match(List.of(evidence), posting.roles()).status().name())
          .as(posting.id())
          .isEqualTo(posting.expectedRole());
    }
  }

  @Test
  void shouldKeepGenericSpecializationAndConflictingSourcesUnknown() {
    var matcher = new RoleMatcher();
    var generic =
        new VacancyEvidence(
            "a",
            "o",
            null,
            "fixture",
            "Software Engineer",
            null,
            "Requirements: Java",
            VacancyEvidence.Completeness.complete,
            null,
            1,
            null,
            null);
    assertThat(matcher.match(List.of(generic), List.of("Backend Developer")).status().name())
        .isEqualTo("unknown");
    assertThat(matcher.match(List.of(generic), List.of("Software Engineer")).status().name())
        .isEqualTo("matched");
    var unrelated =
        new VacancyEvidence(
            "b",
            "o",
            null,
            "fixture",
            "Service Designer",
            null,
            "Java",
            VacancyEvidence.Completeness.partial,
            null,
            1,
            null,
            null);
    assertThat(
            matcher
                .match(List.of(generic, unrelated), List.of("Software Engineer"))
                .status()
                .name())
        .isEqualTo("unknown");
  }
}
