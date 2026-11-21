package dev.curriculotron.shared.web;

import dev.curriculotron.applications.ApplicationService;
import dev.curriculotron.applications.ApplicationSummary;
import dev.curriculotron.audit.application.IntegrationStatusService;
import dev.curriculotron.audit.domain.IntegrationState;
import dev.curriculotron.opportunities.adapter.in.web.OpportunitySummaries;
import dev.curriculotron.opportunities.adapter.in.web.OpportunitySummary;
import dev.curriculotron.opportunities.application.CompatibilityStatus;
import dev.curriculotron.opportunities.application.CompatibilityStatusService;
import dev.curriculotron.opportunities.application.OpportunityService;
import dev.curriculotron.opportunities.application.port.out.MatchCandidateRepository;
import dev.curriculotron.orchestration.application.port.out.RoutineRepository;
import dev.curriculotron.shared.AppClock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
  private final ApplicationService applications;
  private final OpportunityService jobs;
  private final MatchCandidateRepository candidates;
  private final IntegrationStatusService status;
  private final RoutineRepository routines;
  private final AppClock clock;
  private final OpportunitySummaries summaries;
  private final CompatibilityStatusService compatibility;
  private final PendingReviewItems pendingItems;

  public HomeController(
      ApplicationService applications,
      OpportunityService jobs,
      MatchCandidateRepository candidates,
      IntegrationStatusService status,
      RoutineRepository routines,
      AppClock clock,
      OpportunitySummaries summaries,
      CompatibilityStatusService compatibility,
      PendingReviewItems pendingItems) {
    this.applications = applications;
    this.jobs = jobs;
    this.candidates = candidates;
    this.status = status;
    this.routines = routines;
    this.clock = clock;
    this.summaries = summaries;
    this.compatibility = compatibility;
    this.pendingItems = pendingItems;
  }

  public record IntegrationSummary(
      String id,
      IntegrationState state,
      String lastSuccessAt,
      String lastAttemptAt,
      String detail) {}

  public record SelectionToday(
      LocalDate localDate,
      int count,
      int consumedCount,
      int limit,
      List<OpportunitySummary> items) {
    public SelectionToday {
      items = List.copyOf(items);
    }
  }

  public record MissedRoutine(String routineId, String scheduledFor) {}

  public record MailSummary(String id, String subject) {}

  public record HomeResponse(
      String dataAsOf,
      CompatibilityStatus assessment,
      List<IntegrationSummary> integrations,
      SelectionToday selectionToday,
      List<ApplicationSummary> applicationsWithNextActions,
      List<MailSummary> mailNeedingAttention,
      int reviewQueueCount,
      List<MissedRoutine> missedRoutines,
      List<PendingReviewItem> pendingReviewItems) {
    public HomeResponse {
      integrations = List.copyOf(integrations);
      applicationsWithNextActions = List.copyOf(applicationsWithNextActions);
      mailNeedingAttention = List.copyOf(mailNeedingAttention);
      missedRoutines = List.copyOf(missedRoutines);
      pendingReviewItems = List.copyOf(pendingReviewItems);
    }
  }

  @GetMapping("/api/home")
  @Transactional(readOnly = true)
  public HomeResponse home() {
    LocalDate today = clock.localDate();
    var selected = jobs.list("selection", today);
    var visible = summaries.of(selected);
    int consumedCount = jobs.consumedCount(today);
    var integrations =
        status.findAll().stream()
            .map(integration ->
                new IntegrationSummary(
                    integration.integration(),
                    integration.state(),
                    string(integration.lastSuccessAt()),
                    string(integration.lastAttemptAt()),
                    integration.detail()))
            .toList();
    var openCandidates = candidates.open();
    var missed = routines.unacknowledgedMissed();
    List<MailSummary> mail = List.of();
    return new HomeResponse(
        status.latestSuccess().map(HomeController::string).orElse(null),
        compatibility.current(),
        integrations,
        new SelectionToday(today, visible.size(), consumedCount, 10, visible),
        applications.withNextActions(),
        mail,
        openCandidates.size(),
        missed.stream()
            .map(routine -> new MissedRoutine(routine.routineId(), string(routine.scheduledFor())))
            .toList(),
        pendingItems.of(openCandidates, mail, missed));
  }

  private static String string(Instant value) {
    return value == null ? null : value.toString();
  }
}
