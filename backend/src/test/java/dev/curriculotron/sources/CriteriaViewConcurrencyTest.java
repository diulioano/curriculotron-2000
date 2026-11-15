package dev.curriculotron.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import dev.curriculotron.support.AppTestBase;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class CriteriaViewConcurrencyTest extends AppTestBase {
  @Autowired CriteriaService service;
  @MockitoSpyBean CriteriaStore store;

  @Test
  void shouldReadCriteriaAndAssessmentFromOneSnapshotWhileAnotherRequestSaves() throws Exception {
    var initial = service.current();
    var firstRead = new CountDownLatch(1);
    var continueRead = new CountDownLatch(1);
    var intercepted = new AtomicBoolean();
    doAnswer(
            invocation -> {
              var result = invocation.callRealMethod();
              if (Thread.currentThread().getName().equals("criteria-reader")
                  && intercepted.compareAndSet(false, true)) {
                firstRead.countDown();
                if (!continueRead.await(10, TimeUnit.SECONDS))
                  throw new IllegalStateException("Read barrier timed out");
              }
              return result;
            })
        .when(store)
        .document();

    try (var executor =
        Executors.newSingleThreadExecutor(
            r -> Thread.ofPlatform().name("criteria-reader").unstarted(r))) {
      var reading = executor.submit(service::view);
      try {
        assertThat(firstRead.await(10, TimeUnit.SECONDS)).isTrue();
        var changed =
            new CriteriaDocument(
                initial.version(),
                List.of("Backend Python"),
                List.of("Python"),
                initial.remotePreferred(),
                initial.hybridRegions(),
                initial.cltMinMonthlyBrl(),
                initial.pjMinMonthlyBrl(),
                initial.acceptJuniorIfJustified(),
                initial.dailySearchTimeLocal(),
                initial.deadlineHighlightDays(),
                initial.urgentHours(),
                initial.emailDigestEnabled());
        var saved = service.saveView(changed);
        assertThat(saved.criteria().version()).isEqualTo(initial.version() + 1);
        assertThat(saved.assessment().criteriaVersion()).isEqualTo(saved.criteria().version());
      } finally {
        continueRead.countDown();
      }
      var snapshot = reading.get(10, TimeUnit.SECONDS);
      assertThat(snapshot.criteria().version()).isEqualTo(initial.version());
      assertThat(snapshot.assessment().criteriaVersion()).isEqualTo(initial.version());
    }
  }
}
