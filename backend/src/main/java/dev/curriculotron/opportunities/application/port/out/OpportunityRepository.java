package dev.curriculotron.opportunities.application.port.out;

import dev.curriculotron.opportunities.domain.Opportunity;
import java.util.List;
import java.util.Optional;

public interface OpportunityRepository {
  Optional<Opportunity> find(String id);

  List<Opportunity> discarded();

  List<String> activeIds();

  String createManual(String text, String at);

  /** Persists a vacancy found in a configured source with its literal facts and objective rank. */
  String createFromSource(SourcedOpportunity opportunity, String at);

  record SourcedOpportunity(
      String title,
      String company,
      String location,
      String workMode,
      List<String> requirementFacts,
      String fit,
      int fitScore,
      List<String> fitReasons) {}

  boolean setDecision(String id, String decision, String at);
}
