package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.opportunities.application.OpportunityCorrectionService;
import dev.curriculotron.opportunities.application.OpportunityService;
import dev.curriculotron.opportunities.application.OpportunityTimelineService;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Axis;
import dev.curriculotron.opportunities.domain.CorrectableOpportunityField;
import dev.curriculotron.opportunities.domain.SeniorityDeriver;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import java.util.ArrayList;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monta o detalhe da vaga em uma única transação de leitura, de modo que o snapshot devolvido
 * (inclusive logo após uma correção) seja consistente.
 */
@Component
public class OpportunityDetailQuery {
  private final OpportunityService jobs;
  private final OpportunitySummaries summaries;
  private final OpportunityCorrectionService corrections;
  private final OpportunityTimelineService timeline;

  public OpportunityDetailQuery(
      OpportunityService jobs,
      OpportunitySummaries summaries,
      OpportunityCorrectionService corrections,
      OpportunityTimelineService timeline) {
    this.jobs = jobs;
    this.summaries = summaries;
    this.corrections = corrections;
    this.timeline = timeline;
  }

  /**
   * @throws dev.curriculotron.opportunities.application.OpportunityNotFoundException se não existe
   */
  @Transactional(readOnly = true)
  public OpportunityDetailResponse detail(String id) {
    var job = jobs.get(id);
    var projection = summaries.detailOf(job);
    var summary = projection.summary();
    var missing = new ArrayList<String>();
    if (job.salary() == null) missing.add("Salário a confirmar");
    if (job.workMode().equals("unknown")) missing.add("Modalidade a confirmar");
    if (job.contractType().equals("unknown")) missing.add("Tipo de contrato a confirmar");
    if (job.location() == null || job.location().isBlank()) missing.add("Localização a confirmar");
    if (job.companyName().equals("Empresa a confirmar")) missing.add("Empresa a confirmar");
    addCompatibilityMissing(missing, summary.compatibility(), projection.storedEvidence());
    var descriptions =
        projection.storedEvidence().stream()
            .map(VacancyEvidence::text)
            .filter(text -> text != null && !text.isBlank())
            .toList();
    var originalTitles =
        projection.storedEvidence().stream()
            .map(VacancyEvidence::title)
            .filter(text -> text != null && !text.isBlank())
            .toList();
    return new OpportunityDetailResponse(
        summary.id(),
        summary.title(),
        summary.companyName(),
        summary.location(),
        summary.workMode(),
        summary.contractType(),
        summary.salary(),
        summary.fitReasons(),
        summary.userDecision(),
        summary.availability(),
        summary.deadlineAt(),
        summary.publishedAt(),
        summary.source(),
        summary.companyTier(),
        summary.salaryReference(),
        summary.glassdoorUrl(),
        summary.compatibility(),
        projection.compatibilityEvidence(),
        projection.compatibilityCriteria(),
        job.facts(),
        job.inferred(),
        missing,
        projection.sources(),
        summary.correctedFields(),
        corrections.history(id).stream().map(OpportunityCorrectionView::of).toList(),
        SeniorityDeriver.derive(
            job.title(),
            summary.correctedFields().contains(CorrectableOpportunityField.title),
            originalTitles,
            job.facts(),
            job.inferred(),
            descriptions),
        timeline.of(id).stream().map(OpportunityTimelineView::of).toList());
  }

  private static void addCompatibilityMissing(
      ArrayList<String> missing,
      CompatibilitySummary compatibility,
      java.util.List<VacancyEvidence> storedEvidence) {
    boolean hasText =
        storedEvidence.stream().anyMatch(ev -> ev.text() != null && !ev.text().isBlank());
    boolean hasComplete =
        storedEvidence.stream()
            .anyMatch(ev -> ev.completeness() == VacancyEvidence.Completeness.complete);
    if (!hasText) {
      missing.add("Descrição completa da vaga a confirmar");
    } else if (!hasComplete) {
      missing.add("Descrição completa da vaga a confirmar (conteúdo parcial ou resumido)");
    }
    if (compatibility.reasonCodes().contains("not_assessed")) {
      missing.add("Avaliação de compatibilidade ainda não executada");
      return;
    }
    if (compatibility.roleStatus() == Axis.unknown) {
      missing.add("Cargo compatível não confirmado na vaga");
    }
    if (compatibility.technologyStatus() == Axis.unknown) {
      missing.add("Tecnologias exigidas não confirmadas na vaga");
    }
  }
}
