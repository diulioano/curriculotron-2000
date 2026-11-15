package dev.curriculotron.opportunities.application.port.out;

import java.util.List;

public interface OpportunitySourceRepository {
  record Source(String source, String url, String fetchedAt) {}

  List<Source> forOpportunity(String id);
}
