package dev.curriculotron.sources.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface CompanySourceJpaRepository extends JpaRepository<CompanySourceEntity, String> {
  List<CompanySourceEntity> findByTier(String tier);

  List<CompanySourceEntity> findByCareersAdapterIsNotNullOrderByNameAsc();
}
