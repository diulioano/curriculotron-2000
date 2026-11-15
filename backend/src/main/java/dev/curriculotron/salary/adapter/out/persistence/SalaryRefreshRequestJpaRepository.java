package dev.curriculotron.salary.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SalaryRefreshRequestJpaRepository
    extends JpaRepository<SalaryRefreshRequestEntity, SalaryRefreshRequestId> {
  Optional<SalaryRefreshRequestEntity> findByOpportunityIdAndRequestKey(
      String opportunityId, String requestKey);
}
