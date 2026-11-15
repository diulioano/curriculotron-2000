package dev.curriculotron.sources;

import dev.curriculotron.audit.application.ActivityLogService;
import dev.curriculotron.audit.domain.ActivityRecord;
import dev.curriculotron.orchestration.application.TaskRunService;
import dev.curriculotron.orchestration.domain.TaskRun;
import dev.curriculotron.orchestration.domain.TaskRunTrigger;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.Ids;
import dev.curriculotron.shared.web.ApiException;
import dev.curriculotron.shared.web.ApiListResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class SearchController {
  private final SearchService search;
  private final TaskRunService tasks;
  private final ActivityLogService activity;
  private final AppClock clock;

  public SearchController(
      SearchService search, TaskRunService tasks, ActivityLogService activity, AppClock clock) {
    this.search = search;
    this.tasks = tasks;
    this.activity = activity;
    this.clock = clock;
  }

  public record SearchRequest(java.time.LocalDate publishedFrom, java.time.LocalDate publishedTo) {}

  public record SearchAccepted(String taskRunId) {}

  @PostMapping("/api/searches")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public SearchAccepted search(
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @RequestBody(required = false) SearchRequest request) {
    SearchWindow window;
    try {
      window =
          SearchWindow.resolve(
              request == null ? null : request.publishedFrom(),
              request == null ? null : request.publishedTo(),
              clock.localDate());
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest(
          "invalid_search_window", "A data inicial deve ser anterior ou igual à final.");
    }
    return new SearchAccepted(
        search.submit(
            "search:" + (key == null ? Ids.newId() : key), TaskRunTrigger.manual, window));
  }

  @GetMapping("/api/task-runs")
  public ApiListResponse<TaskRun> tasks() {
    return new ApiListResponse<>(tasks.recent(100), null);
  }

  @GetMapping("/api/task-runs/{id}")
  public TaskRun task(@PathVariable String id) {
    return tasks.find(id).orElseThrow(() -> ApiException.notFound("Tarefa não encontrada."));
  }

  @GetMapping("/api/activity")
  public ApiListResponse<ActivityRecord> activity() {
    return new ApiListResponse<>(activity.since(java.time.Instant.EPOCH, 100), null);
  }
}
