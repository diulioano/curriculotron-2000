package dev.curriculotron.profile.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Converte o currículo em Markdown em fatos (FR-001). Só o que está escrito vira fato (RB-006); o
 * cabeçalho (nome e contato) não vira fato.
 *
 * <p>Seções de nível 2 definem o tipo; cabeçalhos de nível 3 (empresa) dão contexto às linhas de
 * experiência seguintes.
 */
public final class ResumeParser {

  private ResumeParser() {}

  public static List<ParsedFact> parse(String content) {
    List<ParsedFact> out = new ArrayList<>();
    String[] lines = content.split("\\R", -1);
    FactKind kind = null;
    String context = null;

    for (int i = 0; i < lines.length; i++) {
      int lineNo = i + 1;
      String raw = lines[i].strip();
      if (raw.isEmpty()) {
        continue;
      }
      if (raw.startsWith("## ")) {
        kind = kindOf(raw.substring(3));
        context = null;
        continue;
      }
      if (raw.startsWith("# ")) {
        kind = null; // cabeçalho com nome/contato: não vira fato
        continue;
      }
      if (kind == null) {
        continue;
      }
      if (raw.startsWith("### ")) {
        context = clean(raw.substring(4));
        out.add(new ParsedFact(kind, context, lineNo));
        continue;
      }
      String text = clean(raw.startsWith("- ") || raw.startsWith("* ") ? raw.substring(2) : raw);
      if (text.isEmpty()) {
        continue;
      }
      if (context != null && kind == FactKind.experience) {
        text = context + " — " + text;
      }
      out.add(new ParsedFact(kind, text, lineNo));
    }
    return out;
  }

  /** Hash do conteúdo: o mesmo arquivo não gera nova versão. */
  public static String sha256(String content) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static FactKind kindOf(String heading) {
    String h = heading.toLowerCase(Locale.ROOT);
    if (h.contains("skill") || h.contains("competên") || h.contains("habilidade")) {
      return FactKind.skill;
    }
    if (h.contains("education") || h.contains("formação") || h.contains("educação")) {
      return FactKind.education;
    }
    if (h.contains("language") || h.contains("idioma")) {
      return FactKind.language;
    }
    if (h.contains("experience")
        || h.contains("experiência")
        || h.contains("summary")
        || h.contains("resumo")
        || h.contains("project")
        || h.contains("projeto")) {
      return FactKind.experience;
    }
    return FactKind.preference;
  }

  /** Remove marcação Markdown simples (negrito, itálico, espaços de quebra). */
  private static String clean(String s) {
    return s.replace("**", "").replace("__", "").replaceAll("\\s+", " ").strip();
  }
}
