package dev.curriculotron.shared.web;

import dev.curriculotron.audit.application.IntegrationStatusService;
import dev.curriculotron.opportunities.adapter.in.web.OpportunitySummaries;
import dev.curriculotron.opportunities.application.OpportunityService;
import dev.curriculotron.opportunities.application.port.out.MatchCandidateRepository;
import dev.curriculotron.orchestration.application.port.out.RoutineRepository;
import dev.curriculotron.shared.AppClock;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class HomeController {
  private final OpportunityService jobs;
  private final MatchCandidateRepository candidates;
  private final IntegrationStatusService status;
  private final RoutineRepository routines;
  private final AppClock clock;
  private final OpportunitySummaries summaries;

  public HomeController(
      OpportunityService jobs,
      MatchCandidateRepository candidates,
      IntegrationStatusService status,
      RoutineRepository routines,
      AppClock clock,
      OpportunitySummaries summaries) {
    this.summaries = summaries;
    this.jobs = jobs;
    this.candidates = candidates;
    this.status = status;
    this.routines = routines;
    this.clock = clock;
  }

  @GetMapping("/api/home")
  public Map<String, Object> home() {
    var result = new LinkedHashMap<String, Object>();
    result.put("dataAsOf", status.latestSuccess().orElse(null));
    result.put(
        "integrations",
        status.findAll().stream()
            .map(s -> {
              var item = new LinkedHashMap<String, Object>();
              item.put("id", s.integration());
              item.put("state", s.state());
              item.put("lastSuccessAt", s.lastSuccessAt());
              item.put("lastAttemptAt", s.lastAttemptAt());
              item.put("detail", s.detail());
              return item;
            })
            .toList());
    var selected = jobs.list("selection", clock.localDate());
    result.put(
        "selectionToday",
        Map.of(
            "localDate",
            clock.localDate(),
            "count",
            selected.size(),
            "limit",
            10,
            "items",
            summaries.of(selected)));
    // The MVP has no application or mail tables yet; no synthetic records are exposed.
    result.put("applicationsWithNextActions", List.of());
    result.put("mailNeedingAttention", List.of());
    result.put("reviewQueueCount", candidates.open().size());
    result.put("missedRoutines", routines.unacknowledgedMissed());
    return result;
  }
}
