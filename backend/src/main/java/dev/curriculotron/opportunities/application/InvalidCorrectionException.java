package dev.curriculotron.opportunities.application;

/** Correção recusada por violar uma regra de domínio ou por não alterar nenhum dado. */
public final class InvalidCorrectionException extends RuntimeException {
  public InvalidCorrectionException(String message) {
    super(message);
  }
}
