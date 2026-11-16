package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.opportunities.application.port.out.CompatibilityRepository;
import dev.curriculotron.opportunities.application.port.out.EvidenceRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunitySourceRepository;
import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.salary.application.SalaryEstimateService;
import dev.curriculotron.sources.PublicationStore;
import dev.curriculotron.sources.SearchCriteriaRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Maps the opportunity domain model to the stable list DTO consumed by the panel. */
@Component
public class OpportunitySummaries {
  private final OpportunitySourceRepository sources;
  private final SalaryEstimateService salary;
  private final PublicationStore publications;
  private final CompatibilityRepository assessments;
  private final EvidenceRepository evidence;
  private final SearchCriteriaRepository criteria;

  public OpportunitySummaries(
      OpportunitySourceRepository sources,
      SalaryEstimateService salary,
      PublicationStore publications,
      CompatibilityRepository assessments,
      EvidenceRepository evidence,
      SearchCriteriaRepository criteria) {
    this.sources = sources;
    this.salary = salary;
    this.publications = publications;
    this.assessments = assessments;
    this.evidence = evidence;
    this.criteria = criteria;
  }

  @Transactional(readOnly = true)
  public List<OpportunitySummary> of(List<Opportunity> items) {
    long criteriaVersion = criteria.current().version();
    return items.stream().map(item -> of(item, criteriaVersion)).toList();
  }

  @Transactional(readOnly = true)
  public OpportunitySummary of(Opportunity item) {
    return of(item, criteria.current().version());
  }

  private OpportunitySummary of(Opportunity item, long criteriaVersion) {
    List<OpportunitySourceRepository.Source> found = sources.forOpportunity(item.id());
    CompatibilitySummary compatibility = compatibility(item.id(), criteriaVersion);
    List<String> fitReasons = compatibility.current() ? item.fitReasons() : List.of();
    return new OpportunitySummary(
        item.id(),
        item.title(),
        item.companyName(),
        item.location(),
        item.workMode(),
        item.contractType(),
        item.salary(),
        fitReasons,
        item.userDecision(),
        item.availability(),
        null,
        publications.find(item.id()),
        found.isEmpty() ? null : found.getLast(),
        null,
        salary.reference(item),
        salary.glassdoorUrl(item),
        compatibility);
  }

  private CompatibilitySummary compatibility(String opportunityId, long criteriaVersion) {
    return assessments
        .find(opportunityId)
        .map(assessment ->
            new CompatibilitySummary(
                assessment.criteriaVersion(),
                assessment.evaluatorVersion(),
                assessment.assessedAt().toString(),
                assessment.current(criteriaVersion, evidence.revision(opportunityId)),
                assessment.roleStatus(),
                assessment.technologyStatus(),
                assessment.reasonCodes()))
        .orElseGet(CompatibilitySummary::notAssessed);
  }
}
