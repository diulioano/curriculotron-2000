package dev.curriculotron.applications;

import dev.curriculotron.opportunities.application.OpportunityNotFoundException;
import dev.curriculotron.shared.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import org.springframework.web.bind.annotation.*;

@RestController
public class ApplicationController {
  private final ApplicationService service;

  public ApplicationController(ApplicationService service) {
    this.service = service;
  }

  @GetMapping("/api/applications")
  public ApplicationListResponse list() {
    var items = service.list();
    return new ApplicationListResponse(
        items,
        items.stream().map(ApplicationSummary::updatedAt).max(String::compareTo).orElse(null));
  }

  @GetMapping("/api/applications/{id}")
  public ApplicationDetail detail(@PathVariable String id) {
    return call(() -> service.detail(id));
  }

  @PostMapping("/api/opportunities/{id}/applied")
  public ApplicationDetail apply(@PathVariable String id) {
    return call(() -> service.apply(id));
  }

  public record StatusRequest(
      @NotNull ApplicationStatus toStatus, @NotBlank @Size(max = 4000) String reason) {}

  @PostMapping("/api/applications/{id}/status")
  public ApplicationDetail status(
      @PathVariable String id, @Valid @RequestBody StatusRequest request) {
    return call(() -> service.status(id, request.toStatus(), request.reason()));
  }

  public record ActionRequest(
      @NotBlank @Size(max = 4000) String text, String dueAt, @NotNull DuePrecision duePrecision) {}

  @PostMapping("/api/applications/{id}/next-action")
  public ApplicationDetail action(
      @PathVariable String id, @Valid @RequestBody ActionRequest request) {
    return call(
        () -> service.nextAction(id, request.text(), request.dueAt(), request.duePrecision()));
  }

  private <T> T call(Supplier<T> operation) {
    try {
      return operation.get();
    } catch (NoSuchElementException | OpportunityNotFoundException e) {
      throw ApiException.notFound("Registro não encontrado.");
    } catch (IllegalStateException e) {
      throw ApiException.conflict(
          "application_exists", "Candidatura já registrada ou vaga mesclada.");
    } catch (IllegalArgumentException | java.time.DateTimeException e) {
      throw ApiException.badRequest(
          "invalid_application", "Confira a etapa, o motivo e o prazo informados.");
    }
  }
}
