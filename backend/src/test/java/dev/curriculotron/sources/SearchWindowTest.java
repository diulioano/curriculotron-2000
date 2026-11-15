package dev.curriculotron.sources;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SearchWindowTest {
  @Test
  void defaultsToThirtyDaysAndKeepsUnknownDatesExplicitlyEligible() {
    var today = LocalDate.of(2026, 11, 14);
    var window = SearchWindow.resolve(null, null, today);
    assertThat(window.publishedFrom()).isEqualTo(today.minusDays(30));
    assertThat(window.includes(today.minusDays(30))).isTrue();
    assertThat(window.includes(today.minusDays(31))).isFalse();
    assertThat(window.includes(today.plusDays(1))).isFalse();
    assertThat(window.includes(null)).isTrue();
  }

  @Test
  void customRangeIsInclusiveAndRejectsReversedDates() {
    var from = LocalDate.of(2026, 10, 1);
    var to = LocalDate.of(2026, 10, 7);
    var window = SearchWindow.resolve(from, to, to);
    assertThat(window.includes(from)).isTrue();
    assertThat(window.includes(to)).isTrue();
    assertThat(window.includes(to.plusDays(1))).isFalse();
    assertThatThrownBy(() -> new SearchWindow(to, from))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void missingOrInvalidPublicationIsNeverReplacedWithToday() {
    assertThat(PublicationDates.parse("")).isNull();
    assertThat(PublicationDates.parse("invalid")).isNull();
    assertThat(PublicationDates.parse("2026-11-12T10:00:00Z"))
        .isEqualTo(LocalDate.of(2026, 11, 12));
  }
}
