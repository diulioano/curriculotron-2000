package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.audit.application.ActivityLogService;
import dev.curriculotron.audit.domain.ActivityEntry;
import dev.curriculotron.opportunities.application.OpportunityService;
import dev.curriculotron.opportunities.application.port.out.MatchCandidateRepository;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.shared.web.ApiException;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dedupe-candidates")
public class DedupeController {
  private final MatchCandidateRepository candidates;
  private final OpportunityService jobs;
  private final WriteExecutor writer;
  private final AppClock clock;
  private final ActivityLogService activity;

  public DedupeController(
      MatchCandidateRepository candidates,
      OpportunityService jobs,
      WriteExecutor writer,
      AppClock clock,
      ActivityLogService activity) {
    this.candidates = candidates;
    this.jobs = jobs;
    this.writer = writer;
    this.clock = clock;
    this.activity = activity;
  }

  @GetMapping
  public Object list() {
    var result = new LinkedHashMap<String, Object>();
    result.put(
        "items",
        candidates.open().stream()
            .map(c ->
                Map.of(
                    "id",
                    c.id(),
                    "opportunityA",
                    jobs.get(c.opportunityA()),
                    "opportunityB",
                    jobs.get(c.opportunityB()),
                    "signal",
                    c.signal()))
            .toList());
    result.put("dataAsOf", null);
    return result;
  }

  @PostMapping("/{id}/{action}")
  public void resolve(@PathVariable String id, @PathVariable String action) {
    String[] parts = id.split(":", -1);
    if (parts.length != 2 || !List.of("merge", "reject").contains(action))
      throw ApiException.badRequest("invalid_action", "Decisão inválida.");
    writer.run(() -> {
      boolean changed =
          action.equals("merge")
              ? candidates.merge(parts[0], parts[1], clock.now().toString())
              : candidates.reject(parts[0], parts[1], clock.now().toString());
      if (!changed)
        throw ApiException.conflict(
            "candidate_unavailable", "Duplicata já resolvida ou vinculada a candidatura.");
      activity.record(ActivityEntry.user("dedupe." + action, "dedupe_candidate", id));
    });
  }
}
