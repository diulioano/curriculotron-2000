package dev.curriculotron.shared.web;

import org.springframework.http.HttpStatus;

/**
 * Erro HTTP lançado por adaptadores web e devolvido como {@code {error:{code,message}}}. A mensagem
 * é exibida ao usuário (pt-BR) e nunca deve conter segredos, caminhos de credenciais ou detalhes
 * internos. Regras de negócio lançam exceções de domínio; cada adaptador web as traduz.
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  public ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public static ApiException conflict(String code, String message) {
    return new ApiException(HttpStatus.CONFLICT, code, message);
  }

  public static ApiException notFound(String message) {
    return new ApiException(HttpStatus.NOT_FOUND, "not_found", message);
  }

  public static ApiException badRequest(String code, String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, message);
  }
}
