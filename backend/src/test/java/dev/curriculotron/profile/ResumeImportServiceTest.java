package dev.curriculotron.profile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.curriculotron.profile.application.ResumeImportService;
import dev.curriculotron.profile.domain.FactKind;
import dev.curriculotron.profile.domain.ParsedFact;
import dev.curriculotron.profile.domain.ResumeParser;
import dev.curriculotron.support.AppTestBase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** T019 — FR-001: currículo vira ProfileVersion(imported) + fatos documented com linha. */
class ResumeImportServiceTest extends AppTestBase {

  static final String SAMPLE =
      """
      # Fulana de Tal

      **Software Developer**

      fulana@example.com | +55 (51) 90000-0000

      ## Professional Experience

      ### Empresa X — Porto Alegre
      **Java Developer**
      2023 – Present

      - Developed REST APIs with **Spring Boot**.

      ## Technical Skills

      - **Backend:** Java, Kafka

      ## Languages

      - Portuguese (native)
      """;

  @Autowired ResumeImportService importer;
  @Autowired JdbcClient jdbc;

  @Test
  void cabecalhoComContatoNaoViraFato() {
    var facts = ResumeParser.parse(SAMPLE);
    assertThat(facts)
        .extracting(ParsedFact::statement)
        .noneMatch(s -> s.contains("@") || s.contains("Fulana") || s.contains("90000"));
  }

  @Test
  void classificaSecoesELinhaDeOrigem() {
    var facts = ResumeParser.parse(SAMPLE);
    assertThat(facts)
        .contains(
            new ParsedFact(FactKind.experience, "Empresa X — Porto Alegre", 9),
            new ParsedFact(
                FactKind.experience,
                "Empresa X — Porto Alegre — Developed REST APIs with Spring Boot.",
                13),
            new ParsedFact(FactKind.skill, "Backend: Java, Kafka", 17),
            new ParsedFact(FactKind.language, "Portuguese (native)", 21));
  }

  @Test
  void importaCurriculoRealComoDocumentadoEReimportacaoNaoDuplica() throws Exception {
    Path file = Path.of("..", "curriculo-en-draft.md");
    org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(file), "currículo do repositório");

    var first = importer.importFile(file);
    var second = importer.importFile(file);

    assertThat(first.created()).isTrue();
    assertThat(first.factCount()).isGreaterThan(10);
    assertThat(second.created()).isFalse();
    assertThat(second.profileVersionId()).isEqualTo(first.profileVersionId());

    String status =
        jdbc.sql("SELECT status FROM profile_version WHERE id = ?")
            .param(first.profileVersionId())
            .query(String.class)
            .single();
    assertThat(status).isEqualTo("imported");

    List<Map<String, Object>> rows =
        jdbc.sql(
                "SELECT kind, statement, origin, origin_ref FROM profile_fact WHERE profile_version_id = ?")
            .param(first.profileVersionId())
            .query()
            .listOfRows();
    assertThat(rows)
        .allSatisfy(r -> {
          assertThat(r.get("origin")).isEqualTo("documented");
          assertThat((String) r.get("origin_ref")).matches("curriculo-en-draft\\.md:linha \\d+");
          assertThat((String) r.get("statement")).doesNotContain("@gmail.com");
        });
    assertThat(rows).anySatisfy(r -> assertThat((String) r.get("statement")).contains("Kafka"));
    assertThat(rows).anySatisfy(r -> assertThat(r.get("kind")).isEqualTo("skill"));
  }
}
