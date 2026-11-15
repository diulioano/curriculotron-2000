package dev.curriculotron.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.curriculotron.opportunities.application.port.out.CompatibilityRefreshRepository;
import dev.curriculotron.opportunities.domain.CompatibilityRefresh;
import dev.curriculotron.support.AppTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CriteriaServiceTest extends AppTestBase {
  @Autowired CriteriaService service;
  @Autowired SearchCriteriaRepository settings;
  @Autowired CompatibilityRefreshRepository refresh;
  @Autowired CriteriaStore store;
  @Autowired dev.curriculotron.shared.persistence.WriteExecutor writer;
  @Autowired dev.curriculotron.shared.AppClock clock;
  @Autowired dev.curriculotron.opportunities.application.CompatibilityStatusService status;

  @Test
  void shouldRollbackCriteriaWhenInvalidationCannotBeSaved() {
    var initial = service.current();
    var failingRefresh = org.mockito.Mockito.mock(CompatibilityRefreshRepository.class);
    org.mockito.Mockito.doThrow(new IllegalStateException("storage failure"))
        .when(failingRefresh)
        .save(org.mockito.ArgumentMatchers.any());
    var failing = new CriteriaService(store, failingRefresh, writer, clock, status);
    var changed =
        new CriteriaDocument(
            initial.version(),
            List.of("Changed"),
            initial.stackKeywords(),
            initial.remotePreferred(),
            initial.hybridRegions(),
            initial.cltMinMonthlyBrl(),
            initial.pjMinMonthlyBrl(),
            initial.acceptJuniorIfJustified(),
            initial.dailySearchTimeLocal(),
            initial.deadlineHighlightDays(),
            initial.urgentHours(),
            initial.emailDigestEnabled());
    assertThatThrownBy(() -> failing.save(changed)).isInstanceOf(IllegalStateException.class);
    assertThat(service.current()).isEqualTo(initial);
  }

  @Test
  void shouldNormalizePersistAndRejectObsoleteVersionWithoutLosingCriteria() {
    var initial = service.current();
    var requested =
        new CriteriaDocument(
            initial.version(),
            List.of("Backend Developer"),
            List.of(" Python ", "python", ""),
            initial.remotePreferred(),
            initial.hybridRegions(),
            initial.cltMinMonthlyBrl(),
            initial.pjMinMonthlyBrl(),
            initial.acceptJuniorIfJustified(),
            initial.dailySearchTimeLocal(),
            initial.deadlineHighlightDays(),
            initial.urgentHours(),
            initial.emailDigestEnabled());
    var saved = service.save(requested);
    assertThat(saved.version()).isEqualTo(initial.version() + 1);
    assertThat(saved.stackKeywords()).containsExactly("Python");
    assertThat(service.current()).isEqualTo(saved);
    assertThat(settings.current().criteria().stack()).containsExactly("Python");
    assertThat(settings.current().version()).isEqualTo(saved.version());
    assertThat(refresh.current().state()).isEqualTo(CompatibilityRefresh.State.pending);
    assertThat(service.save(saved)).isEqualTo(saved);
    assertThatThrownBy(() -> service.save(requested))
        .isInstanceOf(CriteriaVersionConflictException.class);
    assertThat(service.current()).isEqualTo(saved);
  }

  @Test
  void shouldRejectZeroSalaryAndInvalidTimeBeforeAnyWrite() {
    var initial = service.current();
    assertThatThrownBy(() ->
            new CriteriaDocument(
                initial.version(),
                List.of(),
                List.of(),
                true,
                List.of(),
                java.math.BigDecimal.ZERO,
                initial.pjMinMonthlyBrl(),
                true,
                "07:30",
                7,
                48,
                false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(service.current()).isEqualTo(initial);
  }
}
