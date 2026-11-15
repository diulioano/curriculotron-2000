package dev.curriculotron.auth.adapter.in.web;

import dev.curriculotron.auth.domain.AuthException;
import dev.curriculotron.auth.domain.PasswordPolicy;
import dev.curriculotron.shared.web.ApiError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduz {@link AuthException} para HTTP. 429 leva {@code Retry-After}. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class AuthExceptionHandler {

  @ExceptionHandler(AuthException.class)
  ResponseEntity<ApiError> handle(AuthException e) {
    return switch (e.error()) {
      case SETUP_REQUIRED ->
          error(
              HttpStatus.CONFLICT,
              "setup_required",
              "Defina a senha antes de entrar (primeiro uso).");
      case ALREADY_CONFIGURED ->
          error(HttpStatus.CONFLICT, "already_configured", "A senha já foi definida.");
      case WEAK_PASSWORD ->
          error(
              HttpStatus.BAD_REQUEST,
              "weak_password",
              "A senha precisa ter entre "
                  + PasswordPolicy.MIN_LENGTH
                  + " e "
                  + PasswordPolicy.MAX_LENGTH
                  + " caracteres.");
      case INVALID_PASSWORD ->
          error(HttpStatus.UNAUTHORIZED, "invalid_password", "Senha incorreta.");
      case TOO_MANY_ATTEMPTS ->
          ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
              .header(
                  HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, e.retryAfter().toSeconds())))
              .body(
                  ApiError.of(
                      "too_many_attempts", "Muitas tentativas. Tente de novo em alguns minutos."));
    };
  }

  private static ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(ApiError.of(code, message));
  }
}
