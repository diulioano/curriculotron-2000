package dev.curriculotron.opportunities.application;

import dev.curriculotron.opportunities.application.port.out.EvidenceRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityCorrectionRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityRepository;
import dev.curriculotron.opportunities.domain.CorrectableOpportunityField;
import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import dev.curriculotron.sources.ConfiguredSourceRepository;
import dev.curriculotron.sources.SearchCriteriaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssessmentInputService {
  public static final String USER_CORRECTION_KIND = "user_correction";
  private static final String USER_CORRECTION_PREFIX = "user-correction-";

  public record Input(
      Opportunity job,
      List<VacancyEvidence> evidence,
      long evidenceRevision,
      SearchCriteriaRepository.Settings settings,
      String juniorJustification) {
    public Input {
      evidence = List.copyOf(evidence);
    }
  }

  private final OpportunityRepository jobs;
  private final EvidenceRepository evidence;
  private final OpportunityCorrectionRepository corrections;
  private final SearchCriteriaRepository criteria;
  private final ConfiguredSourceRepository boards;

  public AssessmentInputService(
      OpportunityRepository jobs,
      EvidenceRepository evidence,
      OpportunityCorrectionRepository corrections,
      SearchCriteriaRepository criteria,
      ConfiguredSourceRepository boards) {
    this.jobs = jobs;
    this.evidence = evidence;
    this.corrections = corrections;
    this.criteria = criteria;
    this.boards = boards;
  }

  @Transactional(readOnly = true)
  public Optional<Input> snapshot(String id) {
    return jobs.find(id)
        .filter(job -> job.mergedInto() == null)
        .map(job -> {
          String junior = boards.juniorJustification(job.companyName()).orElse(null);
          return new Input(
              job,
              withTitleCorrection(job, evidence.forOpportunity(id)),
              evidence.revision(id),
              criteria.current(),
              junior);
        });
  }

  /**
   * Quando a pessoa corrigiu o título, a avaliação usa o título corrigido como evidência própria
   * ({@code user_correction}) e ignora, só em memória, o título bruto da fonte. A descrição e os
   * demais campos da evidência original seguem intactos, e nada é gravado em {@code
   * opportunity_evidence}.
   */
  private List<VacancyEvidence> withTitleCorrection(Opportunity job, List<VacancyEvidence> stored) {
    var correction = corrections.latestCorrection(job.id(), CorrectableOpportunityField.title);
    if (correction.isEmpty()) return stored;
    var result = new ArrayList<VacancyEvidence>(stored.size() + 1);
    for (var original : stored) {
      result.add(
          new VacancyEvidence(
              original.id(),
              original.opportunityId(),
              original.sourceId(),
              original.sourceKind(),
              null,
              original.location(),
              original.text(),
              original.completeness(),
              original.completenessReason(),
              original.contentRevision(),
              original.fetchedAt(),
              original.sourceUrl()));
    }
    result.add(
        new VacancyEvidence(
            USER_CORRECTION_PREFIX + job.id(),
            job.id(),
            null,
            USER_CORRECTION_KIND,
            job.title(),
            job.location(),
            null,
            VacancyEvidence.Completeness.unknown,
            null,
            1,
            correction.get().correctedAt(),
            null));
    return result;
  }
}
