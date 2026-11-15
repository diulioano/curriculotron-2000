package dev.curriculotron.audit.domain;

import java.util.regex.Pattern;

/**
 * Ação a registrar no ActivityLog (NFR-007): quem, ação, alvo, evidência, resultado e falha — sem
 * segredos e sem raciocínio interno do modelo. Id e instante são atribuídos ao gravar.
 */
public record ActivityEntry(
    String taskRunId,
    String author,
    String action,
    String targetType,
    String targetId,
    String evidenceRef,
    ActivityResult result,
    String error) {

  private static final Pattern AUTHOR = Pattern.compile("user|system|bot:[A-Za-z0-9-]+");

  public ActivityEntry {
    if (author == null || !AUTHOR.matcher(author).matches()) {
      throw new IllegalArgumentException("author inválido: " + author);
    }
    if (action == null || action.isBlank()) {
      throw new IllegalArgumentException("action obrigatória");
    }
    if (result == null) {
      throw new IllegalArgumentException("result obrigatório");
    }
  }

  public static ActivityEntry system(String action, ActivityResult result) {
    return new ActivityEntry(null, "system", action, null, null, null, result, null);
  }

  /** Ação do sistema sobre um alvo, com detalhe de falha/omissão opcional. */
  public static ActivityEntry system(
      String action, String targetType, String targetId, ActivityResult result, String error) {
    return new ActivityEntry(null, "system", action, targetType, targetId, null, result, error);
  }

  public static ActivityEntry user(String action, String targetType, String targetId) {
    return new ActivityEntry(
        null, "user", action, targetType, targetId, null, ActivityResult.ok, null);
  }

  public static String botAuthor(String botId) {
    return "bot:" + botId;
  }
}
