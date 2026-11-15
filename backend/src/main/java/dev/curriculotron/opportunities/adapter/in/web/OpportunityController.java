package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.audit.application.IntegrationStatusService;
import dev.curriculotron.opportunities.application.OpportunityService;
import dev.curriculotron.opportunities.application.port.out.OpportunitySourceRepository;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.web.ApiException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/opportunities")
public class OpportunityController {
  @org.springframework.beans.factory.annotation.Autowired
  private dev.curriculotron.sources.PublicationStore publications;

  private final OpportunityService jobs;
  private final OpportunitySourceRepository sources;
  private final IntegrationStatusService status;
  private final AppClock clock;
  private final OpportunitySummaries summaries;
  private final JsonMapper json = JsonMapper.builder().build();

  public OpportunityController(
      OpportunityService jobs,
      OpportunitySourceRepository sources,
      IntegrationStatusService status,
      AppClock clock,
      OpportunitySummaries summaries) {
    this.jobs = jobs;
    this.summaries = summaries;
    this.sources = sources;
    this.status = status;
    this.clock = clock;
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(defaultValue = "selection") String list,
      @RequestParam(required = false) LocalDate date) {
    try {
      var result = new LinkedHashMap<String, Object>();
      result.put("items", summaries.of(jobs.list(list, date == null ? clock.localDate() : date)));
      result.put("dataAsOf", status.latestSuccess().orElse(null));
      return result;
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("invalid_list", "Lista inválida.");
    }
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(@PathVariable String id) {
    try {
      var job = jobs.get(id);
      Map<String, Object> result =
          json.convertValue(job, new TypeReference<LinkedHashMap<String, Object>>() {});
      result.put("sources", sources.forOpportunity(id));
      result.put("publishedAt", publications.find(id));
      var missing = new ArrayList<String>();
      if (job.salary() == null) missing.add("Salário a confirmar");
      if (job.workMode().equals("unknown")) missing.add("Modalidade a confirmar");
      if (job.contractType().equals("unknown")) missing.add("Tipo de contrato a confirmar");
      if (job.location() == null || job.location().isBlank())
        missing.add("Localização a confirmar");
      if (job.companyName().equals("Empresa a confirmar")) missing.add("Empresa a confirmar");
      result.put("missing", missing);
      return result;
    } catch (NoSuchElementException e) {
      throw ApiException.notFound("Vaga não encontrada.");
    }
  }

  public record ManualRequest(String url, String text) {}

  @PostMapping("/manual")
  public Object manual(@RequestBody ManualRequest request) {
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
