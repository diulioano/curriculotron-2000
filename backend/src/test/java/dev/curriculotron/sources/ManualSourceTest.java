package dev.curriculotron.sources;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ManualSourceTest {
  @Test
  void preservesTextWithoutInventingUrlOrStructuredFacts() {
    var result = new ManualSource(null, "  Java pleno\nEmpresa informada  ").fetch();
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().url()).isNull();
    assertThat(result.items().getFirst().text()).isEqualTo("  Java pleno\nEmpresa informada  ");
    assertThat(result.failures()).isEmpty();
    assertThat(result.limitations()).isNotEmpty();
  }

  @Test
  void normalizesUrlWithoutFetchingIt() {
    var item =
        new ManualSource("https://EXAMPLE.com/jobs/1?utm_source=mail", null)
            .fetch()
            .items()
            .getFirst();
    assertThat(item.url()).isEqualTo("https://example.com/jobs/1");
    assertThat(item.text()).isNull();
    assertThat(item.source()).isEqualTo("manual");
  }

  @Test
  void rejectsEmptyInputAndUnsafeUrlsEvenWhenTextExists() {
    assertThatThrownBy(() -> new ManualSource(" ", " ").fetch())
        .isInstanceOf(IllegalArgumentException.class);
    for (String url :
        new String[] {"file:///secret", "javascript:alert(1)", "https://user:pass@example.com"}) {
      assertThatThrownBy(() -> new ManualSource(url, "vaga").fetch())
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
