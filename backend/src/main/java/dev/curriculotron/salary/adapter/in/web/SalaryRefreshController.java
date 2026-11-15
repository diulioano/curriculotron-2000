package dev.curriculotron.salary.adapter.in.web;

import dev.curriculotron.opportunities.application.OpportunityService;
import dev.curriculotron.salary.application.SalaryEstimateService;
import dev.curriculotron.salary.application.SalaryObservation;
import dev.curriculotron.salary.application.SalaryRefreshService;
import dev.curriculotron.salary.domain.*;
import dev.curriculotron.shared.Ids;
import dev.curriculotron.shared.web.ApiError;
import dev.curriculotron.shared.web.ApiException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SalaryRefreshController {
  private final SalaryEstimateService salary;
  private final OpportunityService jobs;
  private final SalaryRefreshService refreshes;

  public record ObservationList(List<SalaryObservation> items) {
    public ObservationList {
      items = List.copyOf(items);
    }
  }

  public record ImportResult(int imported) {}

  public SalaryRefreshController(
      SalaryEstimateService salary, OpportunityService jobs, SalaryRefreshService refreshes) {
    this.salary = salary;
    this.jobs = jobs;
    this.refreshes = refreshes;
  }

  @GetMapping("/salary-estimates")
  public ObservationList list() {
    return new ObservationList(salary.observations());
  }

  @PostMapping("/salary-estimates/import")
  public ResponseEntity<ImportResult> ingest(@RequestBody List<SalaryEstimate> observations) {
    if (observations.stream().anyMatch(Objects::isNull))
      throw ApiException.badRequest("invalid_input", "Referências nulas não são permitidas.");
    if (observations.size() > 100)
      throw ApiException.badRequest("invalid_input", "Importe no máximo 100 referências.");
    // Validate the whole payload before any writes; generated ids prevent replacing history.
    var validated =
        observations.stream()
            .map(e ->
                new SalaryEstimate(
                    Ids.newId(),
                    e.criteria(),
                    e.originalMin(),
                    e.originalMax(),
                    e.originalUnit(),
                    e.currency(),
                    e.sampleSize(),
                    e.source(),
                    e.sourceUrl(),
                    e.consultedAt(),
                    e.sourceUpdatedOn(),
                    e.evidence()))
            .toList();
    salary.importEstimates(validated);
    return ResponseEntity.status(HttpStatus.CREATED).body(new ImportResult(validated.size()));
  }

  @PostMapping("/opportunities/{id}/salary-estimate/refresh")
  public ResponseEntity<SalaryRefreshService.RefreshResult> refresh(
      @PathVariable String id,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    if (key == null || key.isBlank())
      throw ApiException.badRequest("invalid_input", "Informe Idempotency-Key para atualizar.");
    dev.curriculotron.opportunities.domain.Opportunity job;
    try {
      job = jobs.get(id);
    } catch (NoSuchElementException e) {
      throw ApiException.notFound("Vaga não encontrada.");
    }
    if (job.salary() != null)
      throw ApiException.conflict("salary_declared", "A vaga já informa salário.");
    var profile = SalaryEstimateService.profile(job);
    if (!profile.complete())
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "insufficient_profile",
          "Confirme cargo, senioridade, local e regime antes de atualizar.");
    return ResponseEntity.accepted().body(refreshes.refresh(job, key));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiError> invalidImport(IllegalArgumentException error) {
    return ResponseEntity.badRequest()
        .body(
            ApiError.of(
                "invalid_salary_reference",
                "Referência salarial inválida. Confira faixa, unidade e fonte."));
  }
}
