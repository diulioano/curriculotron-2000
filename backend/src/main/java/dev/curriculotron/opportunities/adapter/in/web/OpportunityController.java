package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.audit.application.IntegrationStatusService;
import dev.curriculotron.opportunities.application.OpportunityService;
import dev.curriculotron.opportunities.application.port.out.OpportunitySourceRepository;
import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.salary.application.SalaryEstimateService;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.web.ApiException;
import dev.curriculotron.shared.web.ApiListResponse;
import dev.curriculotron.sources.PublicationStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/opportunities")
public class OpportunityController {
  private final OpportunityService jobs;
  private final OpportunitySourceRepository sources;
  private final IntegrationStatusService status;
  private final AppClock clock;
  private final OpportunitySummaries summaries;
  private final SalaryEstimateService salary;
  private final PublicationStore publications;

  public OpportunityController(
      OpportunityService jobs,
      OpportunitySourceRepository sources,
      IntegrationStatusService status,
      AppClock clock,
      OpportunitySummaries summaries,
      SalaryEstimateService salary,
      PublicationStore publications) {
    this.jobs = jobs;
    this.summaries = summaries;
    this.sources = sources;
    this.status = status;
    this.clock = clock;
    this.salary = salary;
    this.publications = publications;
  }

  @GetMapping
  public ApiListResponse<OpportunitySummary> list(
      @RequestParam(defaultValue = "selection") String list,
      @RequestParam(required = false) LocalDate date) {
    try {
      return new ApiListResponse<>(
          summaries.of(jobs.list(list, date == null ? clock.localDate() : date)),
          status.latestSuccess().map(java.time.Instant::toString).orElse(null));
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("invalid_list", "Lista inválida.");
    }
  }

  @GetMapping("/{id}")
  public OpportunityDetailResponse detail(@PathVariable String id) {
    try {
      var job = jobs.get(id);
      var summary = summaries.of(job);
      var missing = new ArrayList<String>();
      if (job.salary() == null) missing.add("Salário a confirmar");
      if (job.workMode().equals("unknown")) missing.add("Modalidade a confirmar");
      if (job.contractType().equals("unknown")) missing.add("Tipo de contrato a confirmar");
      if (job.location() == null || job.location().isBlank())
        missing.add("Localização a confirmar");
      if (job.companyName().equals("Empresa a confirmar")) missing.add("Empresa a confirmar");
      return new OpportunityDetailResponse(
          summary.id(),
          summary.title(),
          summary.companyName(),
          summary.location(),
          summary.workMode(),
          summary.contractType(),
          summary.salary(),
          summary.fitReasons(),
          summary.userDecision(),
          summary.availability(),
          summary.deadlineAt(),
          summary.publishedAt(),
          summary.source(),
          summary.companyTier(),
          summary.salaryReference(),
          summary.glassdoorUrl(),
          job.facts(),
          job.inferred(),
          missing,
          sources.forOpportunity(id));
    } catch (NoSuchElementException e) {
      throw ApiException.notFound("Vaga não encontrada.");
    }
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
    } catch (NoSuchElementException e) {
      throw ApiException.notFound("Vaga não encontrada.");
    } catch (IllegalStateException e) {
      throw ApiException.conflict("application_exists", "Vaga vinculada a candidatura.");
    }
  }
}
