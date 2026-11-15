package dev.curriculotron.salary.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.curriculotron.orchestration.application.TaskRunService;
import dev.curriculotron.orchestration.domain.*;
import dev.curriculotron.shared.Ids;
import dev.curriculotron.support.AppTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SalaryRecoveryTest extends AppTestBase {
  @Autowired SalaryRecovery recovery;
  @Autowired TaskRunService tasks;

  @Test
  void restartFailsQueuedAndRunningSalaryTasksWithoutAffectingOtherWork() {
    var queued =
        tasks.createOrGet(NewTaskRun.system("salary", TaskRunTrigger.manual, Ids.newId())).run();
    var running =
        tasks.createOrGet(NewTaskRun.system("salary", TaskRunTrigger.manual, Ids.newId())).run();
    var search =
        tasks.createOrGet(NewTaskRun.system("search", TaskRunTrigger.manual, Ids.newId())).run();
    tasks.start(running.id());
    recovery.recover();
    recovery.recover();
    assertThat(tasks.find(queued.id()).orElseThrow().state()).isEqualTo(TaskRunState.failed);
    assertThat(tasks.find(running.id()).orElseThrow().state()).isEqualTo(TaskRunState.failed);
    assertThat(tasks.find(search.id()).orElseThrow().state()).isEqualTo(TaskRunState.queued);
    assertThat(tasks.findByIdempotencyKey(queued.idempotencyKey()).orElseThrow().id())
        .isEqualTo(queued.id());
  }
}
