package dev.curriculotron.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.curriculotron.orchestration.application.TaskRunService;
import dev.curriculotron.orchestration.domain.NewTaskRun;
import dev.curriculotron.orchestration.domain.PauseReason;
import dev.curriculotron.orchestration.domain.TaskRunState;
import dev.curriculotron.orchestration.domain.TaskRunTrigger;
import dev.curriculotron.support.AppTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T021 — ciclo de vida e idempotência de TaskRun. */
class TaskRunServiceTest extends AppTestBase {

  @Autowired TaskRunService runs;

  @Test
  void mesmaChaveNaoDuplica() {
    var a =
        runs.createOrGet(
            NewTaskRun.system("search", TaskRunTrigger.routine, "routine:r1:2026-11-14"));
    var b =
        runs.createOrGet(
            NewTaskRun.system("search", TaskRunTrigger.routine, "routine:r1:2026-11-14"));

    assertThat(a.created()).isTrue();
    assertThat(b.created()).isFalse();
    assertThat(b.run().id()).isEqualTo(a.run().id());
    assertThat(a.run().state()).isEqualTo(TaskRunState.queued);
  }

  @Test
  void cicloCompletoComPausaERetomadaMantendoCheckpoint() {
    String id =
        runs.createOrGet(NewTaskRun.system("search", TaskRunTrigger.manual, "manual:1")).run().id();
    runs.start(id);
    runs.saveCheckpoint(id, Map.of("fetched", List.of("gupy")));
    var paused = runs.pause(id, PauseReason.provider_limit, "limite do Codex");
    assertThat(paused.state()).isEqualTo(TaskRunState.paused);
    assertThat(paused.pauseReason()).isEqualTo(PauseReason.provider_limit);

    var resumed = runs.resume(id);
    assertThat(resumed.state()).isEqualTo(TaskRunState.running);
    assertThat(resumed.pauseReason()).isNull();
    assertThat(resumed.checkpoint()).containsKey("fetched");

    runs.saveCheckpoint(id, Map.of("ranked", 3));
    var done = runs.complete(id);
    assertThat(done.state()).isEqualTo(TaskRunState.completed);
    assertThat(done.finishedAt()).isNotNull();
    assertThat(done.startedAt()).isNotNull();
    assertThat(done.checkpoint()).containsKeys("fetched", "ranked");
  }

  @Test
  void transicoesInvalidasSaoRecusadas() {
    String id =
        runs.createOrGet(NewTaskRun.system("search", TaskRunTrigger.manual, "manual:2")).run().id();
    runs.start(id);
    runs.complete(id);

    assertThatThrownBy(() -> runs.start(id)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> runs.pause(id, PauseReason.needs_person, null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void pausaExigeMotivo() {
    String id =
        runs.createOrGet(NewTaskRun.system("search", TaskRunTrigger.manual, "manual:3")).run().id();
    assertThatThrownBy(() -> runs.pause(id, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void registraFontesConsultadasEFalhas() {
    String id =
        runs.createOrGet(NewTaskRun.system("search", TaskRunTrigger.manual, "manual:4")).run().id();
    runs.recordSources(id, List.of("gupy", "greenhouse"), List.of("greenhouse"));
    var r = runs.find(id).orElseThrow();
    assertThat(r.sourcesConsulted()).containsExactly("gupy", "greenhouse");
    assertThat(r.sourcesFailed()).containsExactly("greenhouse");
  }
}
