package dev.curriculotron.salary.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SalaryEstimateJpaRepository extends JpaRepository<SalaryEstimateEntity, String> {
  List<SalaryEstimateEntity> findByProfileKey(String profileKey);
}
