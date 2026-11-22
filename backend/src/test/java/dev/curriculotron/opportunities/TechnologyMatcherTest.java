package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;

import dev.curriculotron.opportunities.domain.TechnologyMatcher;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import java.util.List;
import org.junit.jupiter.api.Test;

class TechnologyMatcherTest {
  private final TechnologyMatcher matcher = new TechnologyMatcher();

  @Test
  void distinguishesTeamUsageAfterARequirement() {
    var result =
        matcher.match(
            List.of(source("Requisitos: Java. A equipe de produto usa Angular no frontend.")),
            List.of("Angular"));
    assertThat(result.evidence().getFirst().mentionKind().name()).isEqualTo("usage");
  }

  @Test
  void shouldRecognizeRequirementsFromStructuredLeverPayload() throws Exception {
    var http = org.mockito.Mockito.mock(dev.curriculotron.sources.PublicSourceHttp.class);
    var source = new dev.curriculotron.sources.AtsBoardSource("lever", "example", http);
    org.mockito.Mockito.when(http.get(source.endpoint()))
        .thenReturn(
            """
            [{"id":"1","text":"Backend Developer","hostedUrl":"https://jobs.example/1",
              "descriptionPlain":"Build services","lists":[{"text":"Requirements","content":"<ul><li>Java</li></ul>"}]}]
            """);
    var item = source.fetch().items().getFirst();
    var evidence =
        new VacancyEvidence(
            "lever",
            "o",
            null,
            item.source(),
            item.title(),
            null,
            item.text(),
            item.completeness(),
            item.completenessReason(),
            1,
            null,
            item.url());
    assertThat(matcher.match(List.of(evidence), List.of("Java")).status().name())
        .isEqualTo("matched");
  }

  @Test
  void shouldKeepMultilineSectionsAndExcludeCompanyPresentation() {
    var requirements =
        matcher.match(List.of(source("Requirements:\nJava 21\nSpring Boot")), List.of("Java"));
    assertThat(requirements.status().name()).isEqualTo("matched");
    assertThat(requirements.evidence().getFirst().section()).isEqualTo("requirements");
    assertThat(
            matcher
                .match(
                    List.of(
                        source(
                            "About the company: We use Java. Responsibilities: Python backend development.")),
                    List.of("Java"))
                .status()
                .name())
        .isEqualTo("mismatched");
    assertThat(
            matcher
                .match(
                    List.of(source("About us\nWe use Java\nRequirements\nPython")), List.of("Java"))
                .status()
                .name())
        .isEqualTo("mismatched");
    assertThat(matcher.match(List.of(source("Preferred\nJava")), List.of("Java")).status().name())
        .isEqualTo("matched");
  }

  @Test
  void shouldKeepConfiguredTechnologiesAsAlternativesDespiteAnotherNegativeMention() {
    assertThat(
            matcher
                .match(
                    List.of(source("Requirements: Java. We do not use Spring Boot.")),
                    List.of("Java", "Spring Boot"))
                .status()
                .name())
        .isEqualTo("matched");
    assertThat(
            matcher
                .match(List.of(source("Requirements: Java. We do not use Java.")), List.of("Java"))
                .status()
                .name())
        .isEqualTo("unknown");
  }

  @Test
  void shouldPreserveTechnologySymbolsAndExplicitAliases() {
    assertThat(matcher.match(List.of(source("Requirements: C++.")), List.of("C")).status().name())
        .isEqualTo("mismatched");
    assertThat(matcher.match(List.of(source("Requirements: C#.")), List.of("C")).status().name())
        .isEqualTo("mismatched");
    assertThat(
            matcher.match(List.of(source("Requirements: .NET.")), List.of(".NET")).status().name())
        .isEqualTo("matched");
    assertThat(
            matcher
                .match(List.of(source("Requirements: SpringBoot.")), List.of("Spring Boot"))
                .status()
                .name())
        .isEqualTo("matched");
  }

  private VacancyEvidence source(String text) {
    return new VacancyEvidence(
        "e",
        "o",
        null,
        "fixture",
        "Backend Developer",
        null,
        text,
        VacancyEvidence.Completeness.complete,
        null,
        1,
        null,
        null);
  }
}
