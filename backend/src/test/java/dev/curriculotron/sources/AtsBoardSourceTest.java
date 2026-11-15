package dev.curriculotron.sources;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.curriculotron.opportunities.domain.VacancyEvidence.Completeness;
import org.junit.jupiter.api.Test;

class AtsBoardSourceTest {
  @Test
  void shouldPreserveLeverHtmlFallbackWhenPlainAdditionalFieldIsAbsent() throws Exception {
    var http = mock(PublicSourceHttp.class);
    var source = new AtsBoardSource("lever", "example", http);
    when(http.get(source.endpoint()))
        .thenReturn(
            """
            [{"id":"1","text":"Backend Developer","hostedUrl":"https://jobs.example/1",
              "descriptionPlain":"Build services","additional":"<p>Requirements: Java</p>"}]
            """);
    var item = source.fetch().items().getFirst();
    assertThat(item.text()).contains("Requirements: Java");
    assertThat(item.completeness()).isEqualTo(Completeness.complete);
    verify(http, times(1)).get(source.endpoint());
  }

  @Test
  void parsesPublicBoardsAndReportsMalformedResponses() throws Exception {
    var http = mock(PublicSourceHttp.class);
    var source = new AtsBoardSource("greenhouse", "example", http);
    when(http.get(source.endpoint()))
        .thenReturn(
            "{\"jobs\":[{\"id\":12,\"title\":\"Java\",\"absolute_url\":\"https://jobs.example/12\",\"content\":\"Remote\"}]}");
    assertThat(source.fetch().items())
        .containsExactly(
            new JobSource.Item(
                "greenhouse:example",
                "12",
                "https://jobs.example/12",
                "Java\nRemote",
                "Java",
                null,
                null,
                Completeness.complete,
                null));
    when(http.get(source.endpoint())).thenReturn("{}");
    assertThat(source.fetch().failures()).hasSize(1);
    assertThatThrownBy(() -> new AtsBoardSource("lever", "../private", http))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldKeepLeverStructuredRequirementsAndAdditionalTextFromTheSameResponse()
      throws Exception {
    var http = mock(PublicSourceHttp.class);
    var source = new AtsBoardSource("lever", "example", http);
    when(http.get(source.endpoint()))
        .thenReturn(
            """
            [{"id":"1","text":"Backend Developer","hostedUrl":"https://jobs.example/1",
              "descriptionPlain":"Build services","lists":[{"text":"Requirements","content":"<ul><li>Java 21</li><li>Spring Boot</li></ul>"}],
              "additionalPlain":"Remote work"}]
            """);
    var item = source.fetch().items().getFirst();
    assertThat(item.text())
        .contains("Build services", "Requirements", "Java 21", "Spring Boot", "Remote work");
    assertThat(item.text()).doesNotContain("<li>");
    assertThat(item.completeness()).isEqualTo(Completeness.complete);
    verify(http, times(1)).get(source.endpoint());
  }

  @Test
  void shouldNotClaimCompletenessWhenDescriptionIsAbsentOrListsHaveAnUnexpectedShape()
      throws Exception {
    var http = mock(PublicSourceHttp.class);
    var greenhouse = new AtsBoardSource("greenhouse", "example", http);
    when(http.get(greenhouse.endpoint()))
        .thenReturn(
            """
            {"jobs":[{"id":"1","title":"Developer","absolute_url":"https://jobs.example/1"}]}
            """);
    assertThat(greenhouse.fetch().items().getFirst().completeness())
        .isEqualTo(Completeness.unknown);
    var lever = new AtsBoardSource("lever", "example", http);
    when(http.get(lever.endpoint()))
        .thenReturn(
            """
            [{"id":"1","text":"Developer","hostedUrl":"https://jobs.example/1",
              "descriptionPlain":"Build services","lists":{"requirements":"Java"}}]
            """);
    assertThat(lever.fetch().items().getFirst().completeness()).isEqualTo(Completeness.unknown);
  }

  @Test
  void shouldPreserveReadableGreenhouseSectionsAndMarkLocalTruncation() throws Exception {
    var http = mock(PublicSourceHttp.class);
    var source = new AtsBoardSource("greenhouse", "example", http);
    when(http.get(source.endpoint()))
        .thenReturn(
            """
            {"jobs":[{"id":"1","title":"Developer","absolute_url":"https://jobs.example/1",
              "content":"<h2>Requirements</h2><p>Java &amp; Spring</p><script>ignored()</script>"}]}
            """);
    var full = source.fetch().items().getFirst();
    assertThat(full.text())
        .contains("Requirements\n", "Java & Spring")
        .doesNotContain("<", "ignored()");
    assertThat(full.completeness()).isEqualTo(Completeness.complete);
    when(http.get(source.endpoint()))
        .thenReturn(
            "{\"jobs\":[{\"id\":\"1\",\"title\":\"Developer\",\"absolute_url\":\"https://jobs.example/1\",\"content\":\""
                + "x".repeat(100_010)
                + "\"}]}");
    var partial = source.fetch().items().getFirst();
    assertThat(partial.text()).hasSize(100_000);
    assertThat(partial.completeness()).isEqualTo(Completeness.partial);
    assertThat(partial.completenessReason()).isEqualTo("locally_truncated");
  }
}
