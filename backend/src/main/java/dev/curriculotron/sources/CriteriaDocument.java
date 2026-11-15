package dev.curriculotron.sources;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public record CriteriaDocument(
    long version,
    List<String> targetRoles,
    List<String> stackKeywords,
    boolean remotePreferred,
    List<String> hybridRegions,
    BigDecimal cltMinMonthlyBrl,
    BigDecimal pjMinMonthlyBrl,
    boolean acceptJuniorIfJustified,
    String dailySearchTimeLocal,
    int deadlineHighlightDays,
    int urgentHours,
    boolean emailDigestEnabled) {
  public CriteriaDocument {
    if (version <= 0) throw new IllegalArgumentException("Criteria version must be positive");
    targetRoles = normalize(targetRoles);
    stackKeywords = normalize(stackKeywords);
    hybridRegions = normalize(hybridRegions);
    if (cltMinMonthlyBrl == null
        || pjMinMonthlyBrl == null
        || cltMinMonthlyBrl.signum() <= 0
        || pjMinMonthlyBrl.signum() <= 0
        || cltMinMonthlyBrl.stripTrailingZeros().scale() > 2
        || pjMinMonthlyBrl.stripTrailingZeros().scale() > 2)
      throw new IllegalArgumentException(
          "Salary minima must be positive BRL with at most two decimal places");
    cltMinMonthlyBrl = cltMinMonthlyBrl.stripTrailingZeros();
    pjMinMonthlyBrl = pjMinMonthlyBrl.stripTrailingZeros();
    if (dailySearchTimeLocal == null || !dailySearchTimeLocal.matches("[0-2][0-9]:[0-5][0-9]"))
      throw new IllegalArgumentException("Invalid local time");
    try {
      LocalTime.parse(dailySearchTimeLocal);
    } catch (java.time.DateTimeException exception) {
      throw new IllegalArgumentException("Invalid local time", exception);
    }
    if (deadlineHighlightDays < 1
        || deadlineHighlightDays > 365
        || urgentHours < 1
        || urgentHours > 8760) throw new IllegalArgumentException("Invalid highlight period");
  }

  public CriteriaDocument withVersion(long value) {
    return new CriteriaDocument(
        value,
        targetRoles,
        stackKeywords,
        remotePreferred,
        hybridRegions,
        cltMinMonthlyBrl,
        pjMinMonthlyBrl,
        acceptJuniorIfJustified,
        dailySearchTimeLocal,
        deadlineHighlightDays,
        urgentHours,
        emailDigestEnabled);
  }

  private static List<String> normalize(List<String> values) {
    if (values == null || values.size() > 100)
      throw new IllegalArgumentException("Invalid criteria list");
    var result = new LinkedHashMap<String, String>();
    for (String value : values) {
      if (value == null || value.length() > 200)
        throw new IllegalArgumentException("Invalid criterion");
      String trimmed = value.strip();
      if (!trimmed.isEmpty())
        result.putIfAbsent(
            Normalizer.normalize(trimmed, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT), trimmed);
    }
    return List.copyOf(result.values());
  }
}
