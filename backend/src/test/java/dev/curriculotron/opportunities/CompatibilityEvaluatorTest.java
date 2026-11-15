package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;

import dev.curriculotron.opportunities.domain.CompatibilityAssessment.MentionKind;
import dev.curriculotron.opportunities.domain.CompatibilityEvaluator;
import dev.curriculotron.opportunities.domain.TechnologyMatcher;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompatibilityEvaluatorTest {
  @Test
  void shouldRespectExpectedAxesWithoutTradingMismatchForOtherPreferences() {
    var evaluator = new CompatibilityEvaluator();
    for (var posting : CompatibilityFixtures.postings()) {
      var source =
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
      var result = evaluator.evaluate(List.of(source), posting.roles(), posting.technologies());
      assertThat(result.role().status().name())
          .as(posting.id() + " role")
          .isEqualTo(posting.expectedRole());
      assertThat(result.technology().status().name())
          .as(posting.id() + " technology")
          .isEqualTo(posting.expectedTechnology());
    }
  }

  @Test
  void shouldExposeOptionalMentionsAndVersionDifferences() {
    var source =
        new VacancyEvidence(
            "e",
            "o",
            null,
            "fixture",
            "Backend Developer",
            null,
            "Diferencial: Java 21.",
            VacancyEvidence.Completeness.complete,
            null,
            1,
            null,
            null);
    var result = new TechnologyMatcher().match(List.of(source), List.of("Java 17"));
    assertThat(result.status().name()).isEqualTo("matched");
    assertThat(result.evidence().getFirst().mentionKind()).isEqualTo(MentionKind.optional);
    assertThat(result.evidence().getFirst().observedVersion()).isEqualTo("21");
    assertThat(result.evidence().getFirst().configuredVersion()).isEqualTo("17");
  }
}
