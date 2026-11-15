package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.opportunities.application.port.out.OpportunitySourceRepository;
import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.salary.application.SalaryEstimateService;
import dev.curriculotron.sources.PublicationStore;
import java.util.List;
import org.springframework.stereotype.Component;

/** Maps the opportunity domain model to the stable list DTO consumed by the panel. */
@Component
public class OpportunitySummaries {
  private final OpportunitySourceRepository sources;
  private final SalaryEstimateService salary;
  private final PublicationStore publications;

  public OpportunitySummaries(
      OpportunitySourceRepository sources,
      SalaryEstimateService salary,
      PublicationStore publications) {
    this.sources = sources;
    this.salary = salary;
    this.publications = publications;
  }

  public List<OpportunitySummary> of(List<Opportunity> items) {
    return items.stream().map(this::of).toList();
  }

  public OpportunitySummary of(Opportunity item) {
    List<OpportunitySourceRepository.Source> found = sources.forOpportunity(item.id());
    return new OpportunitySummary(
        item.id(),
        item.title(),
        item.companyName(),
        item.location(),
        item.workMode(),
        item.contractType(),
        item.salary(),
        item.fitReasons(),
        item.userDecision(),
        item.availability(),
        null,
        publications.find(item.id()),
        found.isEmpty() ? null : found.getLast(),
        null,
        salary.reference(item),
        salary.glassdoorUrl(item));
  }
}
