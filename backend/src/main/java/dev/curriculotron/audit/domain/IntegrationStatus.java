package dev.curriculotron.audit.domain;

import java.time.Instant;

/**
 * Estado real de uma integração (FR-017). Invariante: uma tentativa malsucedida atualiza só {@code
 * lastAttemptAt}, nunca {@code lastSuccessAt}.
 */
public record IntegrationStatus(
    String integration,
    Instant lastAttemptAt,
    Instant lastSuccessAt,
    IntegrationState state,
    String detail,
    Instant trackingStartedAt) {

  public static String source(String sourceId) {
    return "source:" + sourceId;
  }

  /** Integração ainda sem registro. */
  public static IntegrationStatus untracked(String integration) {
    return new IntegrationStatus(integration, null, null, null, null, null);
  }

  /** Tentativa bem-sucedida: atualiza tentativa e sucesso. */
  public IntegrationStatus succeeded(Instant at, String detail) {
    return new IntegrationStatus(
        integration, at, at, IntegrationState.ok, detail, trackingStartedAt);
  }

  /** Tentativa malsucedida: atualiza só a tentativa, o estado e o detalhe. */
  public IntegrationStatus failed(Instant at, IntegrationState newState, String detail) {
    requireFailureState(newState);
    return new IntegrationStatus(
        integration, at, lastSuccessAt, newState, detail, trackingStartedAt);
  }

  /** Pausa/desconexão sem tentativa (ex.: usuário revogou o Gmail). */
  public IntegrationStatus withState(IntegrationState newState, String detail) {
    return new IntegrationStatus(
        integration, lastAttemptAt, lastSuccessAt, newState, detail, trackingStartedAt);
  }

  public IntegrationStatus trackingSince(Instant at) {
    return new IntegrationStatus(integration, lastAttemptAt, lastSuccessAt, state, detail, at);
  }

  public static void requireFailureState(IntegrationState state) {
    if (state == null || state == IntegrationState.ok) {
      throw new IllegalArgumentException("Falha não pode marcar estado ok");
    }
  }
}
