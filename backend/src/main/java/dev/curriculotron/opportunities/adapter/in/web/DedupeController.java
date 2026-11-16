package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.audit.application.ActivityLogService;
import dev.curriculotron.audit.domain.ActivityEntry;
import dev.curriculotron.opportunities.application.AssessOpportunityService;
import dev.curriculotron.opportunities.application.OpportunityService;
import dev.curriculotron.opportunities.application.port.out.MatchCandidateRepository;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.shared.web.ApiException;
import dev.curriculotron.shared.web.ApiListResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dedupe-candidates")
public class DedupeController {
  private static final Logger log = LoggerFactory.getLogger(DedupeController.class);

  private final MatchCandidateRepository candidates;
  private final OpportunityService jobs;
  private final AssessOpportunityService assessor;
  private final WriteExecutor writer;
  private final AppClock clock;
  private final ActivityLogService activity;
  private final OpportunitySummaries summaries;

  public record DedupeCandidateSummary(
      String id, OpportunitySummary opportunityA, OpportunitySummary opportunityB, String signal) {}

  public DedupeController(
      MatchCandidateRepository candidates,
      OpportunityService jobs,
      AssessOpportunityService assessor,
      WriteExecutor writer,
      AppClock clock,
      ActivityLogService activity,
      OpportunitySummaries summaries) {
    this.candidates = candidates;
    this.jobs = jobs;
    this.assessor = assessor;
    this.writer = writer;
    this.clock = clock;
    this.activity = activity;
    this.summaries = summaries;
  }

  @GetMapping
  public ApiListResponse<DedupeCandidateSummary> list() {
    List<DedupeCandidateSummary> items =
        candidates.open().stream()
            .map(candidate ->
                new DedupeCandidateSummary(
                    candidate.id(),
                    summaries.of(jobs.get(candidate.opportunityA())),
                    summaries.of(jobs.get(candidate.opportunityB())),
                    candidate.signal()))
            .toList();
    return new ApiListResponse<>(items, null);
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
    if ("merge".equals(action) && !assessor.assessAndSave(parts[0])) {
      log.warn("Avaliação não publicada após mesclagem survivorId={}", parts[0]);
    }
  }
}
