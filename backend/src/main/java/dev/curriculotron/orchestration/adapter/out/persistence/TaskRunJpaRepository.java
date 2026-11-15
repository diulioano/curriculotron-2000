package dev.curriculotron.orchestration.adapter.out.persistence;

import dev.curriculotron.orchestration.domain.TaskRunState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

interface TaskRunJpaRepository extends JpaRepository<TaskRunEntity, String> {

  Optional<TaskRunEntity> findByIdempotencyKey(String idempotencyKey);

  List<TaskRunEntity> findAllByOrderByCreatedAtDescIdDesc(Limit limit);

  List<TaskRunEntity> findByStateOrderByCreatedAtAsc(TaskRunState state);
}
