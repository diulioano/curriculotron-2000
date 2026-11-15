package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;

import dev.curriculotron.opportunities.application.DailySelectionService;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.support.AppTestBase;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DailySelectionServiceTest extends AppTestBase {
  @Autowired DailySelectionService service;
  @Autowired WriteExecutor writer;
  @Autowired EntityManager em;
  @Autowired AppClock clock;

  @BeforeEach
  void reset() {
    clock.setClock(Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), AppClock.ZONE));
    writer.run(() -> {
      em.createNativeQuery("DELETE FROM daily_selection").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity").executeUpdate();
    });
  }

  @Test
  void selectsTenAndKeepsFiveSecondaryAcrossRepeatedSearches() {
    seed(15, "strong", "none");
    assertThat(service.selectToday()).hasSize(10);
    assertThat(service.selectToday()).hasSize(10);
    assertThat(service.secondary()).hasSize(5);
  }

  @Test
  void doesNotFillWithWeakOrPreviouslySelectedOrDecidedJobs() {
    seed(3, "strong", "none");
    seed(7, "weak", "none");
    seed(2, "strong", "discarded");
    seed(2, "strong", "applied");
    assertThat(service.selectToday()).hasSize(3);
    clock.setClock(Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), AppClock.ZONE));
    assertThat(service.selectToday()).isEmpty();
    assertThat(service.secondary()).hasSize(7);
  }

  @Test
  void concurrentSearchesCannotExceedDailyLimit() throws Exception {
    seed(25, "strong", "none");
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = pool.submit(service::selectToday);
      var second = pool.submit(service::selectToday);
      assertThat(first.get()).hasSize(10);
      assertThat(second.get()).hasSize(10);
    }
    assertThat(service.secondary()).hasSize(15);
  }

  private void seed(int count, String fit, String decision) {
    writer.run(() -> {
      for (int i = 0; i < count; i++) {
        em.createNativeQuery(
                """
                INSERT INTO opportunity(id,title,company_name,fit,fit_score,user_decision,created_at,updated_at)
                VALUES (:id,'Java pleno','Empresa',:fit,80,:decision,:now,:now)
                """)
            .setParameter("id", java.util.UUID.randomUUID().toString())
            .setParameter("fit", fit)
            .setParameter("decision", decision)
            .setParameter("now", clock.now().toString())
            .executeUpdate();
      }
    });
  }
}
