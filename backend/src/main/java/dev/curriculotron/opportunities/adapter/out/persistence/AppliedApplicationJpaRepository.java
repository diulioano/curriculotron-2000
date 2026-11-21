package dev.curriculotron.opportunities.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface AppliedApplicationJpaRepository extends JpaRepository<AppliedApplicationEntity, String> {
  Optional<AppliedApplicationEntity> findByOpportunityId(String opportunityId);
}
