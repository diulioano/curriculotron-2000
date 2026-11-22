package dev.curriculotron.shared.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Tratamento global de erros: sempre {@code {error:{code,message}}}, sem stack trace e sem
 * mensagens internas (que podem conter caminhos ou segredos).
 *
 * <p>Fica por último na ordem: os handlers de exceções de domínio de cada módulo têm precedência
 * sobre o {@code Exception} genérico daqui.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiErrorHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> api(ApiException e) {
    return ResponseEntity.status(e.status()).body(ApiError.of(e.code(), e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
    String fields =
        String.join(
            ", ", e.getBindingResult().getFieldErrors().stream().map(f -> f.getField()).toList());
    return ResponseEntity.badRequest()
        .body(ApiError.of("validation_failed", "Dados inválidos: " + fields));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
    return ResponseEntity.badRequest()
        .body(ApiError.of("malformed_request", "Corpo da requisição inválido."));
  }

  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class
  })
  ResponseEntity<ApiError> badParameter(Exception e) {
    return ResponseEntity.badRequest()
        .body(ApiError.of("invalid_parameter", "Parâmetro inválido."));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException e) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(ApiError.of("method_not_allowed", "Método não suportado."));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiError> mediaType(HttpMediaTypeNotSupportedException e) {
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .body(ApiError.of("unsupported_media_type", "Use application/json."));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ApiError> uploadTooLarge(MaxUploadSizeExceededException e) {
    return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
        .body(ApiError.of("invalid_profile_document", "O arquivo excede o limite de 5 MB."));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiError> notFound(NoResourceFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiError.of("not_found", "Recurso não encontrado."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception e) {
    // Registra só o tipo; a mensagem pode conter dados sensíveis.
    log.error("Erro inesperado: {}", e.getClass().getName());
    return ResponseEntity.internalServerError()
        .body(ApiError.of("internal_error", "Erro interno. Tente novamente."));
  }
}
