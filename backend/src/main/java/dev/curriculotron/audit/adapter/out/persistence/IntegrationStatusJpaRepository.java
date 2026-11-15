package dev.curriculotron.audit.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface IntegrationStatusJpaRepository extends JpaRepository<IntegrationStatusEntity, String> {

  List<IntegrationStatusEntity> findAllByOrderByIntegrationAsc();

  Optional<IntegrationStatusEntity> findFirstByLastSuccessAtNotNullOrderByLastSuccessAtDesc();
}
