package dev.curriculotron.opportunities.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CompatibilityRefreshJpaRepository
    extends JpaRepository<CompatibilityRefreshEntity, Integer> {
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update CompatibilityRefreshEntity r set r.targetCriteriaVersion=:version, "
          + "r.evaluatorVersion=:evaluator, r.runId=:runId, r.state=:state, "
          + "r.startedAt=:startedAt, r.finishedAt=:finishedAt, r.errorCode=:errorCode, "
          + "r.assessedCount=:assessedCount, r.remainingCount=:remainingCount "
          + "where r.id=1 and r.state='running' and r.runId=:expectedRunId "
          + "and r.targetCriteriaVersion=:expectedVersion and r.evaluatorVersion=:expectedEvaluator")
  int updateIfOwner(
      @Param("expectedRunId") String expectedRunId,
      @Param("expectedVersion") long expectedVersion,
      @Param("expectedEvaluator") int expectedEvaluator,
      @Param("version") long version,
      @Param("evaluator") int evaluator,
      @Param("runId") String runId,
      @Param("state") String state,
      @Param("startedAt") String startedAt,
      @Param("finishedAt") String finishedAt,
      @Param("errorCode") String errorCode,
      @Param("assessedCount") long assessedCount,
      @Param("remainingCount") long remainingCount);
}
