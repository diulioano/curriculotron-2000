package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.opportunities.application.AssessOpportunityService;
import dev.curriculotron.opportunities.application.AssessmentInputService;
import dev.curriculotron.opportunities.application.OpportunityCorrectionService;
import dev.curriculotron.opportunities.application.OpportunityService;
import dev.curriculotron.opportunities.application.OpportunityTimelineService;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Axis;
import dev.curriculotron.opportunities.domain.CorrectableOpportunityField;
import dev.curriculotron.opportunities.domain.SeniorityDeriver;
import dev.curriculotron.opportunities.domain.StudyPreparation;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import dev.curriculotron.profile.application.port.out.CareerProfileRepository;
import dev.curriculotron.salary.application.SalaryReference;
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
  private final CareerProfileRepository profiles;
  private final AssessmentInputService assessmentInputs;
  private final AssessOpportunityService assessor;
  private final StudyPreparation preparation = new StudyPreparation();

  public OpportunityDetailQuery(
      OpportunityService jobs,
      OpportunitySummaries summaries,
      OpportunityCorrectionService corrections,
      OpportunityTimelineService timeline,
      CareerProfileRepository profiles,
      AssessmentInputService assessmentInputs,
      AssessOpportunityService assessor) {
    this.jobs = jobs;
    this.summaries = summaries;
    this.corrections = corrections;
    this.timeline = timeline;
    this.profiles = profiles;
    this.assessmentInputs = assessmentInputs;
    this.assessor = assessor;
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
        timeline.of(id).stream().map(OpportunityTimelineView::of).toList(),
        preparation.plan(projection.storedEvidence(), profileTechnologies()),
        juniorException(id, summary.salaryReference()));
  }

  /**
   * Mesma decisão do ranking, recalculada com os dados atuais: o detalhe não interpreta os motivos
   * gravados nem infere o nível por conta própria.
   */
  private OpportunityDetailResponse.JuniorExceptionView juniorException(
      String id, SalaryReference reference) {
    var input = assessmentInputs.snapshot(id);
    if (input.isEmpty() || input.get().profile() == null)
      return OpportunityDetailResponse.JuniorExceptionView.UNKNOWN;
    boolean estimate = reference != null && reference.estimate() != null;
    return assessor
        .juniorException(input.get())
        .map(decision -> OpportunityDetailResponse.JuniorExceptionView.of(decision, estimate))
        .orElse(OpportunityDetailResponse.JuniorExceptionView.NOT_APPLICABLE);
  }

  /** Perfil lido agora: a preparação reflete a última edição sem esperar a reavaliação. */
  private StudyPreparation.ProfileTechnologies profileTechnologies() {
    return profiles
        .current()
        .map(stored -> {
          var profile = stored.profile();
          return new StudyPreparation.ProfileTechnologies(
              profile.version(),
              profile.primaryTechnology(),
              profile.secondaryTechnologies(),
              profile.flexibleTechnologies());
        })
        .orElse(StudyPreparation.ProfileTechnologies.NONE);
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
