package dev.curriculotron.profile.domain;

/**
 * Resultado da importação: {@code created=false} quando o mesmo arquivo já tinha sido importado.
 */
public record ImportResult(String profileVersionId, boolean created, int factCount) {}
