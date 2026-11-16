package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.opportunities.application.CompatibilityStatus;
import java.util.List;
import java.util.Objects;

public record OpportunityListResponse(
    List<OpportunitySummary> items, String dataAsOf, CompatibilityStatus assessment) {
  public OpportunityListResponse {
    items = List.copyOf(items);
    Objects.requireNonNull(assessment);
  }
}
