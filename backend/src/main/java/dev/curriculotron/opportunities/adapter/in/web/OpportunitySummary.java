package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.opportunities.application.port.out.OpportunitySourceRepository.Source;
import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.salary.application.SalaryReference;
import java.util.List;

public record OpportunitySummary(
    String id,
    String title,
    String companyName,
    String location,
    String workMode,
    String contractType,
    Opportunity.Salary salary,
    List<String> fitReasons,
    String userDecision,
    String availability,
    String deadlineAt,
    String publishedAt,
    Source source,
    String companyTier,
    SalaryReference salaryReference,
    String glassdoorUrl) {
  public OpportunitySummary {
    fitReasons = List.copyOf(fitReasons);
  }
}
