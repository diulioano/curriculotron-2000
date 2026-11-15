package dev.curriculotron.salary.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SalaryLookupAttemptJpaRepository
    extends JpaRepository<SalaryLookupAttemptEntity, String> {
  boolean existsByTaskRunIdAndProfileKey(String taskRunId, String profileKey);

  @Modifying
  @Query(
      "update SalaryLookupAttemptEntity a set a.state = :state, a.completedAt = :completedAt, "
          + "a.estimateId = :estimateId where a.id = :id")
  int complete(
      @Param("id") String id,
      @Param("state") String state,
      @Param("completedAt") String completedAt,
      @Param("estimateId") String estimateId);

  @Modifying
  @Query(
      "update SalaryLookupAttemptEntity a set a.state = 'interrupted', a.completedAt = :at "
          + "where a.state = 'running'")
  int interruptRunning(@Param("at") String at);
}
