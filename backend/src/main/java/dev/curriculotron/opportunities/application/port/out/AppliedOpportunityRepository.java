package dev.curriculotron.opportunities.application.port.out;

import java.time.Instant;
import java.util.Optional;

/** Leitura da candidatura confirmada de uma vaga, usada apenas na linha do tempo. */
public interface AppliedOpportunityRepository {
  record Applied(String applicationId, Instant confirmedAt) {}

  Optional<Applied> forOpportunity(String opportunityId);
}
