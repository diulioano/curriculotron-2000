package dev.curriculotron.sources;

import org.springframework.stereotype.Component;

/**
 * Builds a source from {@code company.careers_adapter}. Supported values: {@code
 * greenhouse:<board>}, {@code lever:<account>}, {@code workable:<account>}, {@code
 * gupy:<subdomain>}, {@code oracle:<pod>.fa.<dc>/<site>}, {@code cwi:oportunidades}.
 */
@Component
public class JobSourceFactory {
  private final PublicSourceHttp http = new PublicSourceHttp();

  public JobSource forAdapter(String adapter) {
    if (adapter == null || !adapter.contains(":"))
      throw new IllegalArgumentException("Adaptador de carreiras inválido");
    String[] parts = adapter.split(":", 2);
    return switch (parts[0]) {
      case "greenhouse", "lever", "workable" -> new AtsBoardSource(parts[0], parts[1], http);
      case "gupy" -> new GupySource(parts[1], http);
      case "oracle" -> new OracleRecruitingSource(parts[1], http);
      case "cwi" -> {
        if (!parts[1].equals("oportunidades"))
          throw new IllegalArgumentException("Adaptador de carreiras inválido");
        yield new CwiCareersSource(http);
      }
      default -> throw new IllegalArgumentException("Adaptador de carreiras não suportado");
    };
  }
}
