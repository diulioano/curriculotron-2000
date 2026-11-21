package dev.curriculotron.opportunities.application;

import dev.curriculotron.opportunities.application.port.out.EvidenceRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityCorrectionRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityRepository.SourcedOpportunity;
import dev.curriculotron.opportunities.domain.CanonicalUrl;
import dev.curriculotron.opportunities.domain.CorrectableOpportunityField;
import dev.curriculotron.opportunities.domain.JobFactExtractor;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.sources.JobSource;
import dev.curriculotron.sources.SearchCriteriaRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Single ingestion path shared by manual entry, on-demand search and routines: stores or refreshes
 * the vacancy, its evidence and then publishes an assessment checked against current versions.
 */
@Service
public final class OpportunityIngestionService {
  public static final String MANUAL_SOURCE = "manual";
  public static final String UNCONFIRMED_COMPANY = "Empresa a confirmar";
  private static final int EXCERPT_LIMIT = 4000;
  private static final Logger log = LoggerFactory.getLogger(OpportunityIngestionService.class);

  private final OpportunityRepository jobs;
  private final OpportunityCorrectionRepository corrections;
  private final DedupeService dedupe;
  private final EvidenceRepository evidence;
  private final AssessOpportunityService assessor;
  private final SearchCriteriaRepository criteria;
  private final WriteExecutor writer;
  private final AppClock clock;
  private final JobFactExtractor extractor = new JobFactExtractor();

  public OpportunityIngestionService(
      OpportunityRepository jobs,
      OpportunityCorrectionRepository corrections,
      DedupeService dedupe,
      EvidenceRepository evidence,
      AssessOpportunityService assessor,
      SearchCriteriaRepository criteria,
      WriteExecutor writer,
      AppClock clock) {
    this.jobs = jobs;
    this.corrections = corrections;
    this.dedupe = dedupe;
    this.evidence = evidence;
    this.assessor = assessor;
    this.criteria = criteria;
    this.writer = writer;
    this.clock = clock;
  }

  public String ingest(JobSource.Item item, String company) {
    return ingestResult(item, company).opportunityId();
  }

  public DedupeService.IngestResult ingestResult(JobSource.Item item, String company) {
    var current = criteria.current().criteria();
    var facts =
        extractor.extract(
            item.title(),
            company,
            item.location(),
            item.text(),
            current.stack(),
            current.hybridRegions());
    boolean manual = MANUAL_SOURCE.equals(item.source());
    var result =
        writer.write(() -> {
          String at = clock.now().toString();
          var ingested = store(item, company, facts, manual, at);
          if (ingested.sourceConflict()) return ingested;
          String stored = ingested.opportunityId();
          boolean hasDetails = item.title() != null || item.text() != null;
          if (hasDetails && !manual) refreshKeepingCorrections(stored, facts, company, at);
          String sourceId = manual ? null : sourceIdOf(item);
          if (hasDetails || evidence.forOpportunity(stored).isEmpty())
            evidence.upsert(
                new VacancyEvidence(
                    manual ? "manual-" + stored : "source-" + sourceId,
                    stored,
                    sourceId,
                    item.source(),
                    hasDetails ? facts.title() : null,
                    item.location(),
                    item.text(),
                    item.completeness(),
                    item.completenessReason(),
                    1,
                    clock.now(),
                    item.url()));
          return ingested;
        });
    if (!result.sourceConflict() && !assessor.assessAndSave(result.opportunityId()))
      log.warn(
          "Avaliação não publicada após conflitos de versão opportunityId={}",
          result.opportunityId());
    return result;
  }

  /**
   * Atualiza a vaga com os dados da coleta, mas mantém por campo o valor que a pessoa corrigiu; a
   * fonte e a evidência continuam registrando o dado bruto coletado.
   */
  private void refreshKeepingCorrections(
      String id, JobFactExtractor.Facts facts, String company, String at) {
    var corrected = corrections.correctedFields(id);
    String title = facts.title();
    String companyName = company;
    String location = facts.location();
    String workMode = facts.workMode();
    if (!corrected.isEmpty()) {
      var current = jobs.find(id).orElseThrow();
      if (corrected.contains(CorrectableOpportunityField.title)) title = current.title();
      if (corrected.contains(CorrectableOpportunityField.company_name))
        companyName = current.companyName();
      if (corrected.contains(CorrectableOpportunityField.location)) location = current.location();
      if (corrected.contains(CorrectableOpportunityField.work_mode)) workMode = current.workMode();
    }
    jobs.refreshDetails(
        id, title, companyName, location, workMode, JobFactExtractor.factList(facts), at);
  }

  private DedupeService.IngestResult store(
      JobSource.Item item,
      String company,
      JobFactExtractor.Facts facts,
      boolean manual,
      String at) {
    if (item.url() == null)
      return new DedupeService.IngestResult(jobs.createManual(item.text(), at), false);
    var initial =
        new SourcedOpportunity(
            facts.title(),
            company,
            facts.location(),
            facts.workMode(),
            JobFactExtractor.factList(facts),
            "weak",
            0,
            List.of("Avaliação pendente."));
    return dedupe.ingestResult(
        item.source(),
        item.externalId(),
        item.url(),
        excerpt(item.text()),
        () -> manual ? jobs.createManual(item.text(), at) : jobs.createFromSource(initial, at));
  }

  private String sourceIdOf(JobSource.Item item) {
    return evidence
        .sourceId(item.source(), item.externalId(), CanonicalUrl.normalize(item.url()))
        .orElseThrow(() -> new IllegalStateException("Fonte da vaga não registrada."));
  }

  private static String excerpt(String text) {
    return text == null ? null : text.substring(0, Math.min(EXCERPT_LIMIT, text.length()));
  }
}
