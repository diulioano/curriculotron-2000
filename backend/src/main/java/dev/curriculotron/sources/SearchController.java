package dev.curriculotron.sources;

import dev.curriculotron.audit.application.ActivityLogService;
import dev.curriculotron.orchestration.application.TaskRunService;
import dev.curriculotron.orchestration.domain.TaskRunTrigger;
import dev.curriculotron.shared.Ids;
import dev.curriculotron.shared.web.ApiException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class SearchController {
  private final SearchService search;
  private final TaskRunService tasks;
  private final ActivityLogService activity;

  public SearchController(SearchService search, TaskRunService tasks, ActivityLogService activity) {
    this.search = search;
    this.tasks = tasks;
    this.activity = activity;
  }

  @org.springframework.beans.factory.annotation.Autowired
  private dev.curriculotron.shared.AppClock clock;

  public record SearchRequest(java.time.LocalDate publishedFrom, java.time.LocalDate publishedTo) {}

  @PostMapping("/api/searches")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Object search(
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
    return Map.of(
        "taskRunId",
        search.submit(
            "search:" + (key == null ? Ids.newId() : key), TaskRunTrigger.manual, window));
  }

  @GetMapping("/api/task-runs")
  public Object tasks() {
    var r = new LinkedHashMap<String, Object>();
    r.put("items", tasks.recent(100));
    r.put("dataAsOf", null);
    return r;
  }

  @GetMapping("/api/task-runs/{id}")
  public Object task(@PathVariable String id) {
    return tasks.find(id).orElseThrow(() -> ApiException.notFound("Tarefa não encontrada."));
  }

  @GetMapping("/api/activity")
  public Object activity() {
    var r = new LinkedHashMap<String, Object>();
    r.put("items", activity.since(java.time.Instant.EPOCH, 100));
    r.put("dataAsOf", null);
    return r;
  }
}
