package dev.curriculotron.sources;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

class AtsBoardSourceTest {
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
                null));
    when(http.get(source.endpoint())).thenReturn("{}");
    assertThat(source.fetch().failures()).hasSize(1);
    assertThatThrownBy(() -> new AtsBoardSource("lever", "../private", http))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
