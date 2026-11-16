package dev.curriculotron.opportunities.application;

import dev.curriculotron.opportunities.application.port.out.EvidenceRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityRepository;
import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import dev.curriculotron.sources.ConfiguredSourceRepository;
import dev.curriculotron.sources.SearchCriteriaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssessmentInputService {
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
  private final SearchCriteriaRepository criteria;
  private final ConfiguredSourceRepository boards;

  public AssessmentInputService(
      OpportunityRepository jobs,
      EvidenceRepository evidence,
      SearchCriteriaRepository criteria,
      ConfiguredSourceRepository boards) {
    this.jobs = jobs;
    this.evidence = evidence;
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
              job, evidence.forOpportunity(id), evidence.revision(id), criteria.current(), junior);
        });
  }
}
