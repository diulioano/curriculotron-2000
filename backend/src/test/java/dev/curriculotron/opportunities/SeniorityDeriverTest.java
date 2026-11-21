package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;

import dev.curriculotron.opportunities.domain.SeniorityAssessment;
import dev.curriculotron.opportunities.domain.SeniorityAssessment.Status;
import dev.curriculotron.opportunities.domain.SeniorityDeriver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SeniorityDeriverTest {

  private static SeniorityAssessment fromTitle(String title) {
    return SeniorityDeriver.derive(title, List.of(), List.of(), List.of());
  }

  private static SeniorityAssessment fromDescription(String description) {
    return SeniorityDeriver.derive(
        "Desenvolvedor Java", List.of(), List.of(), List.of(description));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "Desenvolvedor Java Júnior|Júnior",
        "Dev Backend Jr.|Júnior",
        "Desenvolvedora Pleno|Pleno",
        "Desenvolvedor Backend Pl|Pleno",
        "Java Developer Mid-Level|Pleno",
        "Engenheiro de Software SÊNIOR|Sênior",
        "Backend Sr|Sênior",
        "Especialista em Java|Especialista",
        "Staff Software Engineer|Staff",
        "Tech Lead Java|Lead",
        "Estágio em Desenvolvimento|Estágio",
        "Estagiário de Backend|Estágio",
        "Java Intern|Estágio",
        "Principal Engineer|Principal",
        "Desenvolvedor Pleno/Sênior|Pleno/Sênior",
        "Desenvolvedor Java PL/SR|Pleno/Sênior",
        "Desenvolvedor PL/SR|Pleno/Sênior",
        "Desenvolvedor Se\u0302nior|Sênior",
        "Desenvolvedor Ju\u0301nior|Júnior"
      })
  void shouldTreatLevelInTitleAsFactWithoutReason(String title, String label) {
    var result = fromTitle(title);

    assertThat(result.status()).isEqualTo(Status.fact);
    assertThat(result.label()).isEqualTo(label);
    assertThat(result.reason()).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "Desenvolvedor de Aplicação Java",
    "Analista de Sistemas",
    "Desenvolvedor PL/SQL",
    "Desenvolvedor PL / SQL",
    "Desenvolvedor PL SQL",
    "Desenvolvedor Backend Java",
    "Internacional Backend Developer",
    "Leader de Plataforma"
  })
  void shouldNotMatchLevelInsideOtherWords(String title) {
    var result = fromTitle(title);

    assertThat(result.status()).isEqualTo(Status.absent);
    assertThat(result.label()).isNull();
    assertThat(result.reason()).isNull();
  }

  @Test
  void shouldTreatLevelInDeclaredFactsAsFact() {
    var result =
        SeniorityDeriver.derive(
            "Desenvolvedor Java",
            List.of("Menciona Java", "Nível Pleno informado"),
            List.of(),
            List.of());

    assertThat(result).isEqualTo(new SeniorityAssessment("Pleno", Status.fact, null));
  }

  @Test
  void shouldPreferTitleOverDeclaredFacts() {
    var result =
        SeniorityDeriver.derive(
            "Desenvolvedor Sênior", List.of("Nível Pleno"), List.of(), List.of());

    assertThat(result.label()).isEqualTo("Sênior");
    assertThat(result.status()).isEqualTo(Status.fact);
  }

  @Test
  void shouldNotTreatPrincipalAdjectiveInFactsAsLevel() {
    var result =
        SeniorityDeriver.derive(
            "Desenvolvedor Java", List.of("Linguagem principal Java"), List.of(), List.of());

    assertThat(result.status()).isEqualTo(Status.absent);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "Exigimos 3+ anos de experiência com Java.|Pleno|inferido: “3+ anos”",
        "Experiência mínima de 4 anos em Spring.|Pleno|inferido: “Experiência mínima de 4 anos”",
        "Buscamos 5+ anos em backend.|Sênior|inferido: “5+ anos”",
        "Pelo menos 6 anos de experiência.|Sênior|inferido: “6 anos de experiência”",
        "Perfil com 0-2 anos de atuação.|Júnior|inferido: “0-2 anos”",
        "Ideal para 1 a 2 anos de experiência.|Júnior|inferido: “1 a 2 anos”",
        "Procuramos nível pleno para o time.|Pleno|inferido: “nível pleno”",
        "Vaga para sênior com foco em arquitetura.|Sênior|inferido: “Vaga para sênior”"
      })
  void shouldInferLevelFromExplicitClueAndAlwaysGiveReason(
      String description, String label, String reason) {
    var result = fromDescription(description);

    assertThat(result.status()).isEqualTo(Status.inferred);
    assertThat(result.label()).isEqualTo(label);
    assertThat(result.reason()).isEqualTo(reason);
  }

  @Test
  void shouldInferFromRegisteredInferencesToo() {
    var result =
        SeniorityDeriver.derive(
            "Desenvolvedor Java", List.of(), List.of("Requer 5+ anos de experiência"), List.of());

    assertThat(result.status()).isEqualTo(Status.inferred);
    assertThat(result.label()).isEqualTo("Sênior");
    assertThat(result.reason()).isEqualTo("inferido: “5+ anos”");
  }

  @Test
  void shouldNotCountRangeUpperBoundAsSeparateClue() {
    var result = fromDescription("Experiência: 3-5 anos de experiência com Java.");

    assertThat(result.status()).isEqualTo(Status.inferred);
    assertThat(result.label()).isEqualTo("Pleno");
  }

  @Test
  void shouldInferPlenoFromRangeThatStaysWithinPleno() {
    var result = fromDescription("Experiência de 3-5 anos com Java.");

    assertThat(result.status()).isEqualTo(Status.inferred);
    assertThat(result.label()).isEqualTo("Pleno");
  }

  @Test
  void shouldInferFromDecomposedUnicodeKeepingSnippetAligned() {
    var result = fromDescription("Requisito: experie\u0302ncia mi\u0301nima de 4 anos em Java.");

    assertThat(result.status()).isEqualTo(Status.inferred);
    assertThat(result.label()).isEqualTo("Pleno");
    assertThat(result.reason()).isEqualTo("inferido: “experiência mínima de 4 anos”");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "Somos uma empresa com 20+ anos de mercado.",
        "Há 15 anos no mercado de tecnologia.",
        "Contrato de 2 anos.",
        "Experiência de 1 a 4 anos.",
        "Experiência de 3 a 8 anos.",
        "Experiência de 4-10 anos.",
        "Nível pleno ou sênior.",
        "Perfil pleno e perfil sênior serão considerados.",
        "Requisitos: Java e Spring Boot."
      })
  void shouldReturnAbsentWhenClueIsUnsafeOrConflicting(String description) {
    var result = fromDescription(description);

    assertThat(result).isEqualTo(new SeniorityAssessment(null, Status.absent, null));
  }

  @Test
  void shouldNeverReturnInferredWithoutReason() {
    for (String text :
        List.of("3+ anos", "nível pleno", "5+ anos de experiência", "0-2 anos", "perfil sênior")) {
      var result = fromDescription(text);
      if (result.status() == Status.inferred) {
        assertThat(result.reason()).isNotBlank();
      }
    }
  }

  @Test
  void shouldHandleMissingInputs() {
    var result = SeniorityDeriver.derive(null, null, null, null);

    assertThat(result.status()).isEqualTo(Status.absent);
  }

  @Test
  void shouldMarkLevelReadFromPersonCorrectedTitleAsCorrectedTitle() {
    var result =
        SeniorityDeriver.derive(
            "Desenvolvedor Java Sênior",
            true,
            List.of("Desenvolvedor Java"),
            List.of(),
            List.of(),
            List.of());

    assertThat(result).isEqualTo(new SeniorityAssessment("Sênior", Status.corrected_title, null));
  }

  @Test
  void shouldMarkCorrectedTitleLevelEvenWhenOriginalEvidenceDeclaredAnotherLevel() {
    var result =
        SeniorityDeriver.derive(
            "Desenvolvedor Java Sênior",
            true,
            List.of("Desenvolvedor Java Pleno"),
            List.of("Nível Pleno informado"),
            List.of(),
            List.of());

    assertThat(result).isEqualTo(new SeniorityAssessment("Sênior", Status.corrected_title, null));
  }

  @Test
  void shouldKeepFactWhenCorrectedTitleRepeatsLevelDeclaredByOriginalTitleOrFacts() {
    var fromTitle =
        SeniorityDeriver.derive(
            "Desenvolvedor Java Pleno Spring",
            true,
            List.of("Desenvolvedor Java Pleno"),
            List.of(),
            List.of(),
            List.of());
    var fromFacts =
        SeniorityDeriver.derive(
            "Desenvolvedor Java Pleno",
            true,
            List.of("Desenvolvedor Java"),
            List.of("Nível Pleno informado"),
            List.of(),
            List.of());

    assertThat(fromTitle).isEqualTo(new SeniorityAssessment("Pleno", Status.fact, null));
    assertThat(fromFacts).isEqualTo(new SeniorityAssessment("Pleno", Status.fact, null));
  }

  @Test
  void shouldKeepFactWhenCorrectedTitleHasNoLevelButOriginalFactsDo() {
    var result =
        SeniorityDeriver.derive(
            "Desenvolvedor Java",
            true,
            List.of("Desenvolvedor Backend"),
            List.of("Nível Pleno informado"),
            List.of(),
            List.of());

    assertThat(result).isEqualTo(new SeniorityAssessment("Pleno", Status.fact, null));
  }

  @Test
  void shouldKeepInferredOrAbsentWhenCorrectedTitleHasNoLevel() {
    var inferred =
        SeniorityDeriver.derive(
            "Desenvolvedor Java",
            true,
            List.of("Especialista em Vendas"),
            List.of(),
            List.of(),
            List.of("Exigimos 3+ anos."));
    var absent =
        SeniorityDeriver.derive(
            "Desenvolvedor Java", true, List.of(), List.of(), List.of(), List.of());

    assertThat(inferred.status()).isEqualTo(Status.inferred);
    assertThat(absent).isEqualTo(new SeniorityAssessment(null, Status.absent, null));
  }

  @Test
  void shouldNeverReturnCorrectedTitleForUncorrectedTitle() {
    var result =
        SeniorityDeriver.derive("Desenvolvedor Java Pleno", List.of(), List.of(), List.of());

    assertThat(result).isEqualTo(new SeniorityAssessment("Pleno", Status.fact, null));
  }
}
