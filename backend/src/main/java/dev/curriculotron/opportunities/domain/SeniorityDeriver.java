package dev.curriculotron.opportunities.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deriva o nível da vaga distinguindo fato, inferência e ausência (Princípio VII).
 *
 * <ul>
 *   <li>{@code fact}: o nível aparece literalmente no título atual ou nos fatos declarados;
 *   <li>{@code inferred}: só há pista nas descrições/inferências (anos de experiência ou menção
 *       explícita de nível); sempre acompanhado do trecho que sustenta a inferência;
 *   <li>{@code absent}: nenhuma pista segura, ou pistas conflitantes;
 *   <li>{@code corrected_title}: o nível aparece no título que a pessoa corrigiu.
 * </ul>
 *
 * Termos são procurados com limites de palavra Unicode, para que "pl" não case em "aplicação" nem
 * "sr" em "sistemas".
 */
public final class SeniorityDeriver {
  private static final String BEFORE = "(?<![\\p{L}\\p{N}])";
  private static final String AFTER = "(?![\\p{L}\\p{N}])";
  private static final String LEVEL_TERMS =
      "estagio|estagiari[oa]|intern|internship|junior|jr|pleno|pl|mid(?:[- ]?level)?|senior|sr"
          + "|especialista|specialist|staff|lead|principal";
  private static final Pattern LEVEL = Pattern.compile(BEFORE + "(" + LEVEL_TERMS + ")" + AFTER);
  private static final Pattern EXPLICIT_LEVEL =
      Pattern.compile(
          BEFORE
              + "(?:nivel|perfil|senioridade|seniority|level|posicao|vaga (?:para|de))"
              + "\\s*(?:de\\s+|:\\s*|-\\s*)?("
              + LEVEL_TERMS
              + ")"
              + AFTER);
  private static final Pattern ALTERNATIVE_LEVEL =
      Pattern.compile("^\\s*(?:/|,|ou\\b|or\\b|e\\b)\\s*(" + LEVEL_TERMS + ")" + AFTER);
  private static final String NOT_COMPANY_AGE =
      "(?!\\s+de\\s+(?:mercado|historia|existencia|fundacao|atuacao no mercado))";
  private static final String YEARS = "(?:anos?|years?)";
  private static final List<Pattern> RANGES =
      List.of(
          Pattern.compile(
              BEFORE
                  + "(\\d{1,2})\\s*(?:-|–|—|a|to)\\s*(\\d{1,2})\\s*\\+?\\s*"
                  + YEARS
                  + AFTER
                  + NOT_COMPANY_AGE));
  private static final List<Pattern> SINGLES =
      List.of(
          Pattern.compile(BEFORE + "(\\d{1,2})\\s*\\+\\s*" + YEARS + AFTER + NOT_COMPANY_AGE),
          Pattern.compile(BEFORE + "(\\d{1,2})\\s*" + YEARS + "\\s+(?:ou mais|or more)" + AFTER),
          Pattern.compile(
              BEFORE
                  + "(\\d{1,2})\\s*"
                  + YEARS
                  + "\\s+(?:de|of)\\s+(?:experiencia|experience|atuacao|vivencia)"
                  + AFTER),
          Pattern.compile(
              BEFORE
                  + "experiencia\\s+(?:minima\\s+)?(?:de\\s+)?(\\d{1,2})\\s*\\+?\\s*"
                  + YEARS
                  + AFTER),
          Pattern.compile(
              BEFORE
                  + "(?:minimo|pelo menos|at least|minimum)\\s+(?:de\\s+)?(\\d{1,2})\\s*"
                  + YEARS
                  + AFTER));
  private static final Pattern PL_SQL = Pattern.compile("\\s*/?\\s*sql" + AFTER);
  private static final int REASON_SNIPPET_MAX = 60;

  private SeniorityDeriver() {}

  /**
   * @param title título atual da vaga (inclui correção da pessoa)
   * @param facts fatos literais declarados da vaga
   * @param inferred inferências já registradas para a vaga
   * @param descriptions textos de descrição das evidências coletadas
   */
  public static SeniorityAssessment derive(
      String title, List<String> facts, List<String> inferred, List<String> descriptions) {
    return derive(title, false, List.of(), facts, inferred, descriptions);
  }

  /**
   * @param titleCorrectedByPerson {@code true} quando o título atual foi corrigido pela pessoa
   * @param originalTitles títulos da evidência original coletada (nunca alterados pela correção);
   *     um nível lido do título corrigido só vira {@code corrected_title} quando a evidência
   *     original (esses títulos ou os fatos declarados) não declara o mesmo nível
   */
  public static SeniorityAssessment derive(
      String title,
      boolean titleCorrectedByPerson,
      List<String> originalTitles,
      List<String> facts,
      List<String> inferred,
      List<String> descriptions) {
    var declared = declaredLevel(title, false);
    if (declared != null
        && titleCorrectedByPerson
        && !declaredByOriginalEvidence(declared, originalTitles, facts)) {
      return new SeniorityAssessment(declared, SeniorityAssessment.Status.corrected_title, null);
    }
    if (declared == null) {
      for (String fact : facts == null ? List.<String>of() : facts) {
        declared = declaredLevel(fact, true);
        if (declared != null) break;
      }
    }
    if (declared != null) {
      return new SeniorityAssessment(declared, SeniorityAssessment.Status.fact, null);
    }
    var texts = new ArrayList<String>();
    if (inferred != null) texts.addAll(inferred);
    if (descriptions != null) texts.addAll(descriptions);
    return inferFromText(texts);
  }

  private static boolean declaredByOriginalEvidence(
      String level, List<String> originalTitles, List<String> facts) {
    boolean inTitles =
        originalTitles != null
            && originalTitles.stream().anyMatch(title -> level.equals(declaredLevel(title, false)));
    boolean inFacts =
        facts != null && facts.stream().anyMatch(fact -> level.equals(declaredLevel(fact, true)));
    return inTitles || inFacts;
  }

  private static String declaredLevel(String text, boolean fromFacts) {
    if (text == null || text.isBlank()) return null;
    String folded = fold(nfc(text));
    var labels = new LinkedHashSet<String>();
    Matcher matcher = LEVEL.matcher(folded);
    while (matcher.find()) {
      String term = matcher.group(1);
      if (isFalsePositive(term, folded, matcher.end())) continue;
      if (fromFacts && term.equals("principal")) continue;
      labels.add(label(term));
    }
    return labels.isEmpty() ? null : String.join("/", labels);
  }

  private static SeniorityAssessment inferFromText(List<String> texts) {
    var explicit = new LinkedHashSet<String>();
    String explicitSnippet = null;
    var years = new LinkedHashSet<String>();
    String yearsSnippet = null;
    for (String raw : texts) {
      if (raw == null || raw.isBlank()) continue;
      // NFC antes do fold: índices do texto dobrado e do trecho citado coincidem mesmo em NFD.
      String text = nfc(raw);
      String folded = fold(text);
      Matcher matcher = EXPLICIT_LEVEL.matcher(folded);
      while (matcher.find()) {
        String term = matcher.group(1);
        if (isFalsePositive(term, folded, matcher.end())) continue;
        explicit.add(label(term));
        Matcher alternative = ALTERNATIVE_LEVEL.matcher(folded.substring(matcher.end()));
        if (alternative.find()) explicit.add(label(alternative.group(1)));
        if (explicitSnippet == null)
          explicitSnippet = snippet(text, matcher.start(), matcher.end());
      }
      // Faixas são consumidas antes das pistas isoladas, para "3-5 anos de experiência" não
      // contar também como "5 anos de experiência".
      var remaining = new StringBuilder(folded);
      for (Pattern pattern : RANGES) {
        Matcher range = pattern.matcher(folded);
        while (range.find()) {
          remaining.replace(range.start(), range.end(), " ".repeat(range.end() - range.start()));
          String level =
              levelForYears(Integer.parseInt(range.group(1)), Integer.parseInt(range.group(2)));
          if (level == null) continue;
          years.add(level);
          if (yearsSnippet == null) yearsSnippet = snippet(text, range.start(), range.end());
        }
      }
      for (Pattern pattern : SINGLES) {
        Matcher single = pattern.matcher(remaining);
        while (single.find()) {
          int value = Integer.parseInt(single.group(1));
          String level = value < 3 ? null : levelForYears(value, value);
          if (level == null) continue;
          years.add(level);
          if (yearsSnippet == null) yearsSnippet = snippet(text, single.start(), single.end());
        }
      }
    }
    if (explicit.size() == 1) return inferred(explicit, explicitSnippet);
    if (explicit.isEmpty() && years.size() == 1) return inferred(years, yearsSnippet);
    return absent();
  }

  private static SeniorityAssessment inferred(Set<String> labels, String snippet) {
    return new SeniorityAssessment(
        labels.iterator().next(),
        SeniorityAssessment.Status.inferred,
        "inferido: “" + snippet + "”");
  }

  private static SeniorityAssessment absent() {
    return new SeniorityAssessment(null, SeniorityAssessment.Status.absent, null);
  }

  /** Faixa ({@code lo}–{@code hi}) para nível; faixas que cruzam níveis não dão pista segura. */
  private static String levelForYears(int lo, int hi) {
    if (hi < lo) return null;
    if (hi <= 2) return "Júnior";
    if (lo >= 5) return "Sênior";
    if (lo >= 3 && lo <= 4 && hi <= 5) return "Pleno";
    return null;
  }

  private static boolean isFalsePositive(String term, String folded, int end) {
    if (!term.equals("pl")) return false;
    // "PL/SQL" (linguagem) não é nível; "PL/SR" (Pleno/Sênior) é.
    return PL_SQL.matcher(folded).region(end, folded.length()).lookingAt();
  }

  private static String nfc(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFC);
  }

  private static String label(String term) {
    return switch (term) {
      case "estagio", "estagiario", "estagiaria", "intern", "internship" -> "Estágio";
      case "junior", "jr" -> "Júnior";
      case "pleno", "pl", "mid", "mid level", "mid-level", "midlevel" -> "Pleno";
      case "senior", "sr" -> "Sênior";
      case "especialista", "specialist" -> "Especialista";
      case "staff" -> "Staff";
      case "lead" -> "Lead";
      case "principal" -> "Principal";
      default -> throw new IllegalArgumentException("Nível desconhecido: " + term);
    };
  }

  private static String snippet(String original, int start, int end) {
    String text = original.substring(start, end).strip().replaceAll("\\s+", " ");
    return text.length() > REASON_SNIPPET_MAX ? text.substring(0, REASON_SNIPPET_MAX) : text;
  }

  /** Minúsculas sem acentos, preservando o comprimento para mapear trechos ao texto original. */
  private static String fold(String text) {
    var out = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      String decomposed = Normalizer.normalize(String.valueOf(text.charAt(i)), Normalizer.Form.NFD);
      out.append(Character.toLowerCase(decomposed.charAt(0)));
    }
    return out.toString();
  }
}
