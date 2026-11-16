package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.curriculotron.CurriculotronApplication;
import dev.curriculotron.opportunities.application.CompatibilityRefreshRecovery;
import dev.curriculotron.opportunities.application.CompatibilityRefreshService;
import dev.curriculotron.opportunities.application.CompatibilityStatusService;
import dev.curriculotron.opportunities.application.ReassessOpportunitiesService;
import dev.curriculotron.opportunities.application.port.out.CompatibilityRefreshRepository;
import dev.curriculotron.opportunities.application.port.out.CompatibilityRepository;
import dev.curriculotron.opportunities.application.port.out.EvidenceRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityRepository;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.opportunities.domain.CompatibilityRefresh;
import dev.curriculotron.opportunities.domain.CompatibilityRefresh.State;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import dev.curriculotron.opportunities.domain.VacancyEvidence.Completeness;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.sources.CriteriaDocument;
import dev.curriculotron.sources.CriteriaService;
import dev.curriculotron.sources.SearchCriteriaRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class CompatibilityRefreshRecoveryTest {
  @AfterEach
  void clearInterruptFlag() {
    Thread.interrupted();
  }

  @Test
  void shouldTransitionRunningToFailedInterruptedAcrossRealSqliteRestartWithoutResumingWork(
      @TempDir Path home) {
    String oppId;
    String interruptedRunId;

    try (ConfigurableApplicationContext firstRun = startApp(home)) {
      var jobs = firstRun.getBean(OpportunityRepository.class);
      var evidence = firstRun.getBean(EvidenceRepository.class);
      var refresh = firstRun.getBean(CompatibilityRefreshService.class);
      var writer = firstRun.getBean(WriteExecutor.class);

      oppId =
          writer.write(
              () -> jobs.createManual("Desenvolvedor Backend Java", Instant.EPOCH.toString()));
      writer.run(() ->
          evidence.upsert(
              new VacancyEvidence(
                  "manual-" + oppId,
                  oppId,
                  null,
                  "manual",
                  "Desenvolvedor Backend Java",
                  null,
                  "Requisitos: Java 17 e Spring Boot.",
                  Completeness.complete,
                  null,
                  1,
                  Instant.EPOCH,
                  null)));

      var reservation = refresh.reserve(1);
      interruptedRunId = reservation.runId();
      assertThat(interruptedRunId).isNotBlank();
      assertThat(refresh.status().state()).isEqualTo(State.running);
    }

    try (ConfigurableApplicationContext restarted = startApp(home)) {
      var refreshRepo = restarted.getBean(CompatibilityRefreshRepository.class);
      var assessments = restarted.getBean(CompatibilityRepository.class);
      var refresh = restarted.getBean(CompatibilityRefreshService.class);
      var reassess = restarted.getBean(ReassessOpportunitiesService.class);

      var stored = refreshRepo.current();
      assertThat(stored.state()).isEqualTo(State.failed);
      assertThat(stored.errorCode()).isEqualTo("interrupted");
      assertThat(stored.runId()).isNull();
      assertThat(stored.finishedAt()).isNotNull();

      var status = refresh.status();
      assertThat(status.state()).isEqualTo(State.failed);
      assertThat(status.stale()).isTrue();
      assertThat(status.errorCode()).isEqualTo("interrupted");
      assertThat(status.assessedCount()).isZero();
      assertThat(status.remainingCount()).isEqualTo(1);

      // Recovery must not silently reassess or touch the pending opportunity.
      assertThat(assessments.find(oppId)).isEmpty();

      // Explicit retry resumes cleanly.
      var retried = reassess.reassess(1);
      assertThat(retried.state()).isEqualTo(State.ready);
      assertThat(retried.stale()).isFalse();
      assertThat(retried.assessedCount()).isEqualTo(1);
      assertThat(retried.remainingCount()).isZero();
      assertThat(assessments.find(oppId)).isPresent();
    }

    // Booting again with ready/pending states leaves state untouched.
    try (ConfigurableApplicationContext thirdRun = startApp(home)) {
      var status = thirdRun.getBean(CompatibilityRefreshService.class).status();
      assertThat(status.state()).isEqualTo(State.ready);
      assertThat(status.stale()).isFalse();
      assertThat(status.errorCode()).isNull();
    }
  }

  @Test
  void shouldOnlyFailWhenRunIdIsStillOwner(@TempDir Path home) {
    try (ConfigurableApplicationContext context = startApp(home)) {
      var refresh = context.getBean(CompatibilityRefreshService.class);
      var refreshRepo = context.getBean(CompatibilityRefreshRepository.class);
      var assessments = context.getBean(CompatibilityRepository.class);
      var criteriaRepo = context.getBean(SearchCriteriaRepository.class);
      var statusService = context.getBean(CompatibilityStatusService.class);
      var writer = context.getBean(WriteExecutor.class);
      var clock = context.getBean(AppClock.class);
      var criteria = context.getBean(CriteriaService.class);

      var staleReservation = refresh.reserve(1);
      CompatibilityRefreshRepository staleSnapshotRepo =
          new CompatibilityRefreshRepository() {
            @Override
            public CompatibilityRefresh current() {
              return new CompatibilityRefresh(
                  1,
                  CompatibilityAssessment.EVALUATOR_VERSION,
                  staleReservation.runId(),
                  State.running,
                  staleReservation.startedAt(),
                  null,
                  null,
                  0,
                  0);
            }

            @Override
            public void save(CompatibilityRefresh value) {
              refreshRepo.save(value);
            }

            @Override
            public boolean updateIfOwner(
                String expectedRunId,
                long expectedCriteriaVersion,
                int expectedEvaluatorVersion,
                CompatibilityRefresh value) {
              return refreshRepo.updateIfOwner(
                  expectedRunId, expectedCriteriaVersion, expectedEvaluatorVersion, value);
            }
          };

      // Simulate concurrent criteria update taking ownership away from the stale run.
      var base = criteria.current();
      criteria.save(
          new CriteriaDocument(
              base.version(),
              base.targetRoles(),
              List.of("Python"),
              base.remotePreferred(),
              base.hybridRegions(),
              base.cltMinMonthlyBrl(),
              base.pjMinMonthlyBrl(),
              base.acceptJuniorIfJustified(),
              base.dailySearchTimeLocal(),
              base.deadlineHighlightDays(),
              base.urgentHours(),
              base.emailDigestEnabled()));

      var freshRefreshService =
          new CompatibilityRefreshService(
              criteriaRepo, assessments, refreshRepo, statusService, writer, clock);
      var recovery = new CompatibilityRefreshRecovery(staleSnapshotRepo, freshRefreshService);

      recovery.recover();

      var status = refresh.status();
      assertThat(status.criteriaVersion()).isEqualTo(2);
      assertThat(status.state()).isEqualTo(State.pending);
      assertThat(status.errorCode()).isNull();
    }
  }

  @Test
  void shouldLeavePendingAndFailedStatesUntouchedAcrossRealSqliteRestart(@TempDir Path home) {
    try (ConfigurableApplicationContext firstRun = startApp(home)) {
      var jobs = firstRun.getBean(OpportunityRepository.class);
      var refreshRepo = firstRun.getBean(CompatibilityRefreshRepository.class);
      var criteria = firstRun.getBean(CriteriaService.class);
      var writer = firstRun.getBean(WriteExecutor.class);

      writer.write(() -> jobs.createManual("Desenvolvedor Backend Java", Instant.EPOCH.toString()));
      var base = criteria.current();
      criteria.save(
          new CriteriaDocument(
              base.version(),
              base.targetRoles(),
              List.of("Python"),
              base.remotePreferred(),
              base.hybridRegions(),
              base.cltMinMonthlyBrl(),
              base.pjMinMonthlyBrl(),
              base.acceptJuniorIfJustified(),
              base.dailySearchTimeLocal(),
              base.deadlineHighlightDays(),
              base.urgentHours(),
              base.emailDigestEnabled()));
      assertThat(refreshRepo.current().state()).isEqualTo(State.pending);
    }

    try (ConfigurableApplicationContext secondRun = startApp(home)) {
      var refreshRepo = secondRun.getBean(CompatibilityRefreshRepository.class);
      var storedPending = refreshRepo.current();
      assertThat(storedPending.state()).isEqualTo(State.pending);
      assertThat(storedPending.criteriaVersion()).isEqualTo(2);
      assertThat(storedPending.errorCode()).isNull();

      var refresh = secondRun.getBean(CompatibilityRefreshService.class);
      var reservation = refresh.reserve(2);
      assertThat(
              refresh.failIfOwner(
                  reservation.runId(),
                  2,
                  CompatibilityAssessment.EVALUATOR_VERSION,
                  "assessment_failed"))
          .isTrue();
      assertThat(refreshRepo.current().state()).isEqualTo(State.failed);
      assertThat(refreshRepo.current().errorCode()).isEqualTo("assessment_failed");
    }

    try (ConfigurableApplicationContext thirdRun = startApp(home)) {
      var storedFailed = thirdRun.getBean(CompatibilityRefreshRepository.class).current();
      assertThat(storedFailed.state()).isEqualTo(State.failed);
      assertThat(storedFailed.criteriaVersion()).isEqualTo(2);
      assertThat(storedFailed.errorCode()).isEqualTo("assessment_failed");
      assertThat(storedFailed.runId()).isNull();
    }
  }

  @Test
  void shouldLogSafelyAndPreserveInterruptSignalWhenPersistenceFails(@TempDir Path home) {
    try (ConfigurableApplicationContext context = startApp(home)) {
      var refreshRepo = context.getBean(CompatibilityRefreshRepository.class);
      var assessments = context.getBean(CompatibilityRepository.class);
      var criteriaRepo = context.getBean(SearchCriteriaRepository.class);
      var statusService = context.getBean(CompatibilityStatusService.class);
      var writer = context.getBean(WriteExecutor.class);
      var clock = context.getBean(AppClock.class);

      String activeRunId = "run-recovery-failure-1";
      AtomicInteger readCalls = new AtomicInteger();
      CompatibilityRefreshRepository flakyReadRepo =
          new CompatibilityRefreshRepository() {
            @Override
            public CompatibilityRefresh current() {
              int call = readCalls.incrementAndGet();
              if (call == 1) {
                throw new IllegalStateException("SQLITE_BUSY C:/Users/secret/db.sqlite");
              }
              if (call == 2) {
                throw new IllegalStateException(
                    "Interrupted C:/Users/secret/db.sqlite", new InterruptedException("stop"));
              }
              return new CompatibilityRefresh(
                  1,
                  CompatibilityAssessment.EVALUATOR_VERSION,
                  activeRunId,
                  State.running,
                  Instant.parse("2026-11-15T12:00:00Z"),
                  null,
                  null,
                  0,
                  1);
            }

            @Override
            public void save(CompatibilityRefresh value) {
              refreshRepo.save(value);
            }

            @Override
            public boolean updateIfOwner(
                String expectedRunId,
                long expectedCriteriaVersion,
                int expectedEvaluatorVersion,
                CompatibilityRefresh value) {
              return refreshRepo.updateIfOwner(
                  expectedRunId, expectedCriteriaVersion, expectedEvaluatorVersion, value);
            }
          };

      AtomicInteger failCalls = new AtomicInteger();
      CompatibilityRefreshService failingRefreshService =
          new CompatibilityRefreshService(
              criteriaRepo, assessments, flakyReadRepo, statusService, writer, clock) {
            @Override
            public boolean failIfOwner(
                String runId, long criteriaVersion, int evaluatorVersion, String errorCode) {
              if (failCalls.incrementAndGet() == 1) {
                throw new IllegalStateException("SQLITE_BUSY C:/Users/secret/db.sqlite");
              }
              throw new IllegalStateException(
                  "Interrupted C:/Users/secret/db.sqlite", new InterruptedException("stop"));
            }
          };

      Logger logger = (Logger) LoggerFactory.getLogger(CompatibilityRefreshRecovery.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
      try {
        var recovery = new CompatibilityRefreshRecovery(flakyReadRepo, failingRefreshService);

        // 1. Read failure without InterruptedException keeps interrupt flag clear.
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        recovery.recover();
        assertThat(Thread.interrupted()).isFalse();

        // 2. Read failure caused by InterruptedException restores the thread interrupt flag.
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        recovery.recover();
        assertThat(Thread.interrupted()).isTrue();

        // 3. Write failure without InterruptedException keeps interrupt flag clear.
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        recovery.recover();
        assertThat(Thread.interrupted()).isFalse();

        // 4. Write failure caused by InterruptedException restores the thread interrupt flag.
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        recovery.recover();
        assertThat(Thread.interrupted()).isTrue();

        assertThat(appender.list).hasSize(4);
        for (int index = 0; index < appender.list.size(); index++) {
          ILoggingEvent event = appender.list.get(index);
          assertThat(event.getThrowableProxy()).isNull();
          String message = event.getFormattedMessage();
          assertThat(message).contains("errorCode=interrupted");
          assertThat(message).contains("IllegalStateException");
          if (index >= 2) {
            assertThat(message).contains(activeRunId);
          }
          assertThat(message).doesNotContain("SQLITE_BUSY");
          assertThat(message).doesNotContain("C:/Users/secret");
          assertThat(message).doesNotContain("Interrupted");
          assertThat(message).doesNotContain("stop");
        }
      } finally {
        logger.detachAppender(appender);
      }
    }
  }

  private static ConfigurableApplicationContext startApp(Path home) {
    return new SpringApplicationBuilder(CurriculotronApplication.class)
        .run(
            "--curriculotron.home-dir=" + home,
            "--server.port=0",
            "--server.ssl.enabled=false",
            "--curriculotron.scheduler.enabled=false",
            "--curriculotron.resume-file=");
  }
}
