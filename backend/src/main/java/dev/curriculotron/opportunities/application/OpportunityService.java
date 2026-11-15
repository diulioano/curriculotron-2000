package dev.curriculotron.opportunities.application;

import dev.curriculotron.audit.application.*;
import dev.curriculotron.audit.domain.ActivityEntry;
import dev.curriculotron.opportunities.application.port.out.*;
import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.shared.*;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.sources.ManualSource;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OpportunityService {
  private final OpportunityRepository jobs;
  private final SelectionRepository selections;
  private final DedupeService dedupe;
  private final WriteExecutor writer;
  private final AppClock clock;
  private final ActivityLogService activity;
  private final IntegrationStatusService status;

  public OpportunityService(
      OpportunityRepository jobs,
      SelectionRepository selections,
      DedupeService dedupe,
      WriteExecutor writer,
      AppClock clock,
      ActivityLogService activity,
      IntegrationStatusService status) {
    this.jobs = jobs;
    this.selections = selections;
    this.dedupe = dedupe;
    this.writer = writer;
    this.clock = clock;
    this.activity = activity;
    this.status = status;
  }

  public Opportunity get(String id) {
    return jobs.find(id)
        .orElseThrow(() -> new java.util.NoSuchElementException("Vaga não encontrada"));
  }

  public List<Opportunity> list(String kind, LocalDate date) {
    if ("discarded".equals(kind)) return jobs.discarded();
    List<String> ids =
        switch (kind) {
          case "selection" -> selections.selected(date);
          case "secondary" -> selections.secondary();
          default -> throw new IllegalArgumentException("Lista inválida");
        };
    return ids.stream()
        .map(this::get)
        .filter(job -> job.mergedInto() == null)
        .filter(job -> job.userDecision().equals("none") && !job.availability().equals("closed"))
        .toList();
  }

  public Opportunity manual(String url, String text) {
    var item = new ManualSource(url, text).fetch().items().getFirst();
    return writer.write(() -> {
      String id =
          item.url() == null
              ? jobs.createManual(item.text(), clock.now().toString())
              : dedupe.ingest(
                  "manual",
                  null,
                  item.url(),
                  item.text(),
                  () -> jobs.createManual(item.text(), clock.now().toString()));
      activity.record(ActivityEntry.user("opportunity.manual", "opportunity", id));
      status.recordSuccess("source:manual", "Entrada manual; detalhes não verificados.");
      return get(id);
    });
  }

  public void decide(String id, String decision) {
    writer.run(() -> {
      get(id);
      if (!jobs.setDecision(id, decision, clock.now().toString()))
        throw new IllegalStateException("Vaga vinculada a candidatura não pode ser alterada aqui.");
      activity.record(ActivityEntry.user("opportunity." + decision, "opportunity", id));
    });
  }
}
