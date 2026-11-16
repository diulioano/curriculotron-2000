package dev.curriculotron.opportunities.application;

import dev.curriculotron.audit.application.*;
import dev.curriculotron.audit.domain.ActivityEntry;
import dev.curriculotron.opportunities.application.port.out.*;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.shared.*;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.sources.ManualSource;
import dev.curriculotron.sources.SearchCriteriaRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpportunityService {
  private final OpportunityRepository jobs;
  private final SelectionRepository selections;
  private final SearchCriteriaRepository criteria;
  private final WriteExecutor writer;
  private final AppClock clock;
  private final ActivityLogService activity;
  private final IntegrationStatusService status;
  private final OpportunityIngestionService ingestion;

  public OpportunityService(
      OpportunityRepository jobs,
      SelectionRepository selections,
      SearchCriteriaRepository criteria,
      WriteExecutor writer,
      AppClock clock,
      ActivityLogService activity,
      IntegrationStatusService status,
      OpportunityIngestionService ingestion) {
    this.jobs = jobs;
    this.selections = selections;
    this.criteria = criteria;
    this.writer = writer;
    this.clock = clock;
    this.activity = activity;
    this.status = status;
    this.ingestion = ingestion;
  }

  public Opportunity get(String id) {
    return jobs.find(id)
        .orElseThrow(() -> new java.util.NoSuchElementException("Vaga não encontrada"));
  }

  @Transactional(readOnly = true)
  public List<Opportunity> list(String kind, LocalDate date) {
    if ("discarded".equals(kind)) return jobs.discarded();
    if (!"selection".equals(kind) && !"secondary".equals(kind))
      throw new IllegalArgumentException("Lista inválida");
    long version = criteria.current().version();
    List<String> ids =
        "selection".equals(kind)
            ? selections.visibleSelected(date, version, CompatibilityAssessment.EVALUATOR_VERSION)
            : selections.secondary(version, CompatibilityAssessment.EVALUATOR_VERSION);
    return ids.stream()
        .map(this::get)
        .filter(job -> job.mergedInto() == null)
        .filter(job -> job.userDecision().equals("none") && !job.availability().equals("closed"))
        .toList();
  }

  @Transactional(readOnly = true)
  public int consumedCount(LocalDate date) {
    return selections.selected(date).size();
  }

  public Opportunity manual(String url, String text) {
    var item = new ManualSource(url, text).fetch().items().getFirst();
    String id = ingestion.ingest(item, OpportunityIngestionService.UNCONFIRMED_COMPANY);
    writer.run(() -> {
      activity.record(ActivityEntry.user("opportunity.manual", "opportunity", id));
      status.recordSuccess("source:manual", "Entrada manual; detalhes não verificados.");
    });
    return get(id);
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
