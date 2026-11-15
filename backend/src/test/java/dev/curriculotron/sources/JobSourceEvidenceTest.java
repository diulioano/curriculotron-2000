package dev.curriculotron.sources;

import static org.assertj.core.api.Assertions.assertThat;

import dev.curriculotron.opportunities.domain.VacancyEvidence.Completeness;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class JobSourceEvidenceTest {
  @Test
  void shouldPreserveLegacyConstructorsWithoutClaimingCompleteDescriptions() {
    assertThat(new JobSource.Item("test", "1", null, "Java").completeness())
        .isEqualTo(Completeness.unknown);
    assertThat(new JobSource.Item("test", "1", null, "Java", "Developer", null).title())
        .isEqualTo("Developer");
    var published = LocalDate.of(2026, 11, 15);
    assertThat(
            new JobSource.Item("test", "1", null, "Java", "Developer", null, published)
                .publishedAt())
        .isEqualTo(published);
    assertThat(new ManualSource(null, "Java").fetch().items().getFirst().completeness())
        .isEqualTo(Completeness.unknown);
    assertThat(
            new ManualSource("https://example.test/1", null)
                .fetch()
                .items()
                .getFirst()
                .completeness())
        .isEqualTo(Completeness.unknown);
  }

  @Test
  void shouldMarkTruncatedManualTextAndKeepValidSurrogatePairs() {
    var item = new ManualSource(null, "x".repeat(99_999) + "😀ending").fetch().items().getFirst();
    assertThat(item.text()).hasSize(99_999);
    assertThat(item.completeness()).isEqualTo(Completeness.partial);
    assertThat(item.completenessReason()).isEqualTo("locally_truncated");
  }
}
