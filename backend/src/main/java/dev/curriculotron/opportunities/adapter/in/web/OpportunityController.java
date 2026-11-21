package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.audit.application.IntegrationStatusService;
import dev.curriculotron.opportunities.application.CompatibilityStatusService;
import dev.curriculotron.opportunities.application.OpportunityCorrectionService;
import dev.curriculotron.opportunities.application.OpportunityService;
import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.web.ApiException;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/opportunities")
public class OpportunityController {
  private final OpportunityService jobs;
  private final IntegrationStatusService status;
  private final AppClock clock;
  private final OpportunitySummaries summaries;
  private final OpportunityDetailQuery detailQuery;
  private final CompatibilityStatusService compatibility;
  private final OpportunityCorrectionService corrections;

  public OpportunityController(
      OpportunityService jobs,
      IntegrationStatusService status,
      AppClock clock,
      OpportunitySummaries summaries,
      CompatibilityStatusService compatibility,
      OpportunityCorrectionService corrections,
      OpportunityDetailQuery detailQuery) {
    this.jobs = jobs;
    this.summaries = summaries;
    this.status = status;
    this.clock = clock;
    this.compatibility = compatibility;
    this.corrections = corrections;
    this.detailQuery = detailQuery;
  }

  @GetMapping
  @Transactional(readOnly = true)
  public OpportunityListResponse list(
      @RequestParam(defaultValue = "selection") String list,
      @RequestParam(required = false) LocalDate date) {
    var items = listByName(list, date == null ? clock.localDate() : date);
    return new OpportunityListResponse(
        summaries.of(items),
        status.latestSuccess().map(java.time.Instant::toString).orElse(null),
        compatibility.current());
  }

  private java.util.List<Opportunity> listByName(String list, LocalDate date) {
    try {
      return jobs.list(list, date);
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("invalid_list", "Lista inválida.");
    }
  }

  @GetMapping("/{id}")
  public OpportunityDetailResponse detail(@PathVariable String id) {
    return detailQuery.detail(id);
  }

  @PostMapping("/{id}/corrections")
  public OpportunityDetailResponse correct(
      @PathVariable String id, @Valid @RequestBody OpportunityCorrectionRequest request) {
    corrections.correct(id, request.toCommand());
    return detailQuery.detail(id);
  }

  /** Registro opt-in de que a pessoa abriu o anúncio original; nunca cria candidatura. */
  @PostMapping("/{id}/external-visit")
  public ResponseEntity<Void> externalVisit(@PathVariable String id) {
    jobs.recordExternalVisit(id);
    return ResponseEntity.noContent().build();
  }

  public record ManualRequest(String url, String text) {}

  @PostMapping("/manual")
  public Opportunity manual(@RequestBody ManualRequest request) {
    try {
      return jobs.manual(request.url(), request.text());
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest(
          "invalid_input", "Informe uma URL HTTP/HTTPS válida ou texto da vaga.");
    }
  }

  @PostMapping("/{id}/discard")
  public void discard(@PathVariable String id) {
    decide(id, "discarded");
  }

  @PostMapping("/{id}/restore")
  public void restore(@PathVariable String id) {
    decide(id, "none");
  }

  private void decide(String id, String decision) {
    try {
      jobs.decide(id, decision);
    } catch (IllegalStateException e) {
      throw ApiException.conflict("application_exists", "Vaga vinculada a candidatura.");
    }
  }
}
