package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.opportunities.application.port.out.AppliedOpportunityRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class AppliedOpportunityPersistenceAdapter implements AppliedOpportunityRepository {
  private final AppliedApplicationJpaRepository applications;

  AppliedOpportunityPersistenceAdapter(AppliedApplicationJpaRepository applications) {
    this.applications = applications;
  }

  @Override
  public Optional<Applied> forOpportunity(String opportunityId) {
    return applications
        .findByOpportunityId(opportunityId)
        .map(entity -> new Applied(entity.id(), Instant.parse(entity.confirmedAt())));
  }
}
