package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.opportunities.application.CompatibilityConflictException;
import dev.curriculotron.opportunities.application.CompatibilityRefreshService;
import dev.curriculotron.opportunities.application.CompatibilityStatus;
import dev.curriculotron.opportunities.application.ReassessOpportunitiesService;
import dev.curriculotron.opportunities.domain.CompatibilityRefresh.State;
import dev.curriculotron.shared.web.ApiError;
import dev.curriculotron.shared.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/opportunities")
public class CompatibilityRefreshController {
  private static final Logger log = LoggerFactory.getLogger(CompatibilityRefreshController.class);

  private final ReassessOpportunitiesService reassessService;
  private final CompatibilityRefreshService refreshService;

  public CompatibilityRefreshController(
      ReassessOpportunitiesService reassessService, CompatibilityRefreshService refreshService) {
    this.reassessService = reassessService;
    this.refreshService = refreshService;
  }

  public record ReassessRequest(@NotNull @Positive Long criteriaVersion) {}

  public record ReassessResponse(
      long criteriaVersion,
      State state,
      long assessedCount,
      long remainingCount,
      Instant finishedAt) {}

  @GetMapping("/compatibility-status")
  public CompatibilityStatus status() {
    return refreshService.status();
  }

  @PostMapping("/reassess")
  public ReassessResponse reassess(@Valid @RequestBody ReassessRequest request) {
    try {
      CompatibilityStatus result = reassessService.reassess(request.criteriaVersion());
      if (result.state() != State.ready
          || result.remainingCount() != 0
          || result.criteriaVersion() != request.criteriaVersion()) {
        log.error(
            "Reavaliação terminou sem estado pronto criteriaVersion={} resultVersion={} state={} remaining={}",
            request.criteriaVersion(),
            result.criteriaVersion(),
            result.state(),
            result.remainingCount());
        throw new ApiException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "reassessment_failed",
            "Falha ao reavaliar vagas. Consulte o status e tente novamente.");
      }
      return new ReassessResponse(
          result.criteriaVersion(),
          result.state(),
          result.assessedCount(),
          result.remainingCount(),
          result.finishedAt());
    } catch (ApiException apiException) {
      throw apiException;
    } catch (CompatibilityConflictException conflict) {
      throw ApiException.conflict(conflict.code(), conflict.getMessage());
    } catch (RuntimeException failure) {
      log.error(
          "Falha ao reavaliar vagas criteriaVersion={} exception={}",
          request.criteriaVersion(),
          failure.getClass().getSimpleName());
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "reassessment_failed",
          "Falha ao reavaliar vagas. Consulte o status e tente novamente.");
    }
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
  ResponseEntity<ApiError> invalidCriteriaVersion(Exception exception) {
    return ResponseEntity.badRequest()
        .body(ApiError.of("invalid_criteria_version", "Versão de critérios inválida."));
  }
}
