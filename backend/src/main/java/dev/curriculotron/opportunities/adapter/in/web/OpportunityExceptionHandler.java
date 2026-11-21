package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.opportunities.application.InvalidCorrectionException;
import dev.curriculotron.opportunities.application.OpportunityMergedException;
import dev.curriculotron.opportunities.application.OpportunityNotFoundException;
import dev.curriculotron.shared.web.ApiError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduz exceções de domínio das vagas para HTTP, sem expor detalhes internos. */
@RestControllerAdvice(basePackageClasses = OpportunityController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class OpportunityExceptionHandler {

  @ExceptionHandler(OpportunityNotFoundException.class)
  ResponseEntity<ApiError> notFound(OpportunityNotFoundException e) {
    return error(HttpStatus.NOT_FOUND, "not_found", "Vaga não encontrada.");
  }

  @ExceptionHandler(InvalidCorrectionException.class)
  ResponseEntity<ApiError> invalidCorrection(InvalidCorrectionException e) {
    return error(HttpStatus.BAD_REQUEST, "invalid_correction", e.getMessage());
  }

  @ExceptionHandler(OpportunityMergedException.class)
  ResponseEntity<ApiError> merged(OpportunityMergedException e) {
    return error(
        HttpStatus.CONFLICT,
        "opportunity_merged",
        "Esta vaga foi fundida em outra; corrija a vaga sobrevivente (" + e.survivorId() + ").");
  }

  private static ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(ApiError.of(code, message));
  }
}
