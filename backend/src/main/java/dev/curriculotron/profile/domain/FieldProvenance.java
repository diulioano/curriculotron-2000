package dev.curriculotron.profile.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Origem de um campo do perfil (US006). {@code line}/{@code excerpt} apontam a linha do Markdown
 * que serviu de evidência; guarda-se só esse trecho curto, nunca o documento inteiro.
 */
public record FieldProvenance(Source source, Integer line, String excerpt) {

  public enum Source {
    /** Lido de um rótulo ou seção do Markdown. */
    imported,
    /** Deduzido do texto do currículo (manchete, resumo). */
    inferred,
    /** Valor importado ou inferido corrigido pela pessoa. */
    edited,
    /** Informado pela pessoa, sem evidência documental. */
    declared
  }

  public static final int MAX_EXCERPT = 200;
  static final int MAX_LINE = 100_000;

  public FieldProvenance {
    if (source == null) throw new InvalidProfileException("provenance.source");
    if ((line == null) != (excerpt == null)) throw new InvalidProfileException("provenance.ref");
    if (line != null && (line < 1 || line > MAX_LINE))
      throw new InvalidProfileException("provenance.line");
    if (excerpt != null) {
      excerpt = excerpt.strip().replaceAll("\\s+", " ");
      if (excerpt.isEmpty() || excerpt.length() > MAX_EXCERPT)
        throw new InvalidProfileException("provenance.excerpt");
    }
    if (source == Source.declared && line != null)
      throw new InvalidProfileException("provenance.declared");
    if (source == Source.imported && line == null)
      throw new InvalidProfileException("provenance.imported");
  }

  /** Evidência de uma linha (1-based) do Markdown, reduzida a um trecho curto. */
  public static FieldProvenance from(Source source, int line, String text) {
    String compact = text.strip().replaceAll("\\s+", " ");
    if (compact.length() > MAX_EXCERPT) compact = compact.substring(0, MAX_EXCERPT - 1) + "…";
    return new FieldProvenance(source, line, compact);
  }

  /** Cópia imutável ordenada por campo; {@code null} (cliente sem metadados) vira vazio. */
  public static Map<ProfileDraft.Field, FieldProvenance> copy(
      Map<ProfileDraft.Field, FieldProvenance> value) {
    if (value == null || value.isEmpty()) return Map.of();
    var result = new EnumMap<ProfileDraft.Field, FieldProvenance>(ProfileDraft.Field.class);
    value.forEach((field, provenance) -> {
      if (field == null || provenance == null) throw new InvalidProfileException("provenance");
      result.put(field, provenance);
    });
    return Collections.unmodifiableMap(result);
  }
}
