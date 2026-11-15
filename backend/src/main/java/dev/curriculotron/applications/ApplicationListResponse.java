package dev.curriculotron.applications;

import java.util.List;

public record ApplicationListResponse(List<ApplicationSummary> items, String dataAsOf) {
  public ApplicationListResponse {
    items = List.copyOf(items);
  }
}
