package dev.curriculotron.applications;

import dev.curriculotron.audit.application.ActivityLogService;
import dev.curriculotron.audit.domain.ActivityEntry;
import dev.curriculotron.opportunities.application.OpportunityService;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.Ids;
import dev.curriculotron.shared.persistence.WriteExecutor;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ApplicationService {
  private final ApplicationStore store;
  private final OpportunityService jobs;
  private final WriteExecutor writer;
  private final AppClock clock;
  private final ActivityLogService activity;

  public ApplicationService(
      ApplicationStore store,
      OpportunityService jobs,
      WriteExecutor writer,
      AppClock clock,
      ActivityLogService activity) {
    this.store = store;
    this.jobs = jobs;
    this.writer = writer;
    this.clock = clock;
    this.activity = activity;
  }

  public List<ApplicationSummary> list() {
    return store.list();
  }

  public ApplicationDetail detail(String id) {
    return detail(store.find(id));
  }

  public List<ApplicationSummary> withNextActions() {
    return list().stream()
        .filter(application -> application.nextAction() != null)
        .filter(application ->
            application.status() != ApplicationStatus.CLOSED_REJECTED
                && application.status() != ApplicationStatus.WITHDRAWN)
        .toList();
  }

  public ApplicationDetail apply(String opportunityId) {
    return writer.write(() -> {
      var opportunity = jobs.get(opportunityId);
      if (opportunity.mergedInto() != null) throw new IllegalStateException("Vaga mesclada.");
      if (list().stream()
          .anyMatch(application -> opportunityId.equals(application.opportunityId())))
        throw new IllegalStateException("Candidatura já registrada.");

      String id = Ids.newId();
      String now = clock.now().toString();
      store.save(
          new StoredApplication(
              id,
              opportunityId,
              now,
              "manual",
              ApplicationStatus.SUBMITTED_AWAITING,
              DuePrecision.AMBIGUOUS,
              now,
              now));
      store.markApplied(opportunityId, now);
      append(
          id,
          null,
          ApplicationStatus.SUBMITTED_AWAITING,
          "Confirmação manual de candidatura.",
          now);
      activity.record(ActivityEntry.user("application.created", "application", id));
      return detail(id);
    });
  }

  public ApplicationDetail status(String id, ApplicationStatus to, String reason) {
    if (to == null || reason == null || reason.isBlank() || reason.length() > 4000) {
      throw new IllegalArgumentException("Informe uma etapa válida e o motivo.");
    }
    return writer.write(() -> {
      var application = store.findForUpdate(id);
      var from = application.status();
      String now = clock.now().toString();
      append(id, from, to, reason.trim(), now);
      application.updateStatus(to, now);
      store.save(application);
      activity.record(ActivityEntry.user("application.status", "application", id));
      return detail(id);
    });
  }

  public ApplicationDetail nextAction(String id, String text, String due, DuePrecision precision) {
    if (text == null || text.isBlank() || text.length() > 4000 || precision == null) {
      throw new IllegalArgumentException("Informe a próxima ação e uma precisão válida.");
    }
    String normalized = due == null ? null : Instant.parse(due).toString();
    if (normalized == null && precision != DuePrecision.AMBIGUOUS) {
      throw new IllegalArgumentException("Informe o prazo.");
    }
    return writer.write(() -> {
      var application = store.findForUpdate(id);
      application.updateNextAction(text.trim(), normalized, precision, clock.now().toString());
      store.save(application);
      activity.record(ActivityEntry.user("application.next_action", "application", id));
      return detail(id);
    });
  }

  private ApplicationDetail detail(ApplicationSummary summary) {
    return new ApplicationDetail(
        summary.id(),
        summary.opportunityId(),
        summary.title(),
        summary.companyName(),
        summary.status(),
        summary.nextAction(),
        summary.nextActionDueAt(),
        summary.duePrecision(),
        summary.confirmationOrigin(),
        summary.updatedAt(),
        store.history(summary.id()));
  }

  private void append(
      String id, ApplicationStatus from, ApplicationStatus to, String reason, String now) {
    store.append(new StoredStatusChange(Ids.newId(), id, from, to, "user", null, reason, now));
  }
}
