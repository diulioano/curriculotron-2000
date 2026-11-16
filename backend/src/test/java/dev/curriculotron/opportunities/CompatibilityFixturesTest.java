package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class CompatibilityFixturesTest {
  @Test
  void shouldProvideCompleteAcceptanceCasesAndExplicitExpectedResults() {
    var postings = CompatibilityFixtures.postings();
    assertThat(postings.stream().filter(p -> p.completeness().equals("complete")).count())
        .isGreaterThanOrEqualTo(20);
    assertThat(postings).extracting(CompatibilityFixtures.Posting::id).doesNotHaveDuplicates();
    for (var posting : postings) {
      assertThat(posting.title()).isNotBlank();
      assertThat(posting.text()).isNotBlank();
      assertThat(posting.expectedRole()).isIn("matched", "mismatched", "unknown");
      assertThat(posting.expectedTechnology())
          .isIn("matched", "mismatched", "unknown", "unrestricted");
      assertThat(posting.completeness()).isIn("complete", "partial", "unknown");
    }
    assertThat(postings)
        .extracting(CompatibilityFixtures.Posting::id)
        .containsAll(
            Set.of(
                "java-backend",
                "javascript",
                "cpp",
                "csharp",
                "negative",
                "other-team",
                "contradiction",
                "junior",
                "python-profile",
                "partial",
                "legacy",
                "empty-stack",
                "empty-role"));
  }
}
