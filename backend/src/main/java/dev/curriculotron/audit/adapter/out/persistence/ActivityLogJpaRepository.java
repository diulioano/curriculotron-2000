package dev.curriculotron.audit.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.repository.Repository;

/** Sem save genérico de atualização nem delete: só o que o append-only permite. */
interface ActivityLogJpaRepository extends Repository<ActivityLogEntity, String> {

  ActivityLogEntity save(ActivityLogEntity entity);

  List<ActivityLogEntity> findByAtGreaterThanEqualOrderByAtDescIdDesc(Instant since, Limit limit);

  List<ActivityLogEntity> findByTaskRunIdOrderByAtAscIdAsc(String taskRunId);
}
