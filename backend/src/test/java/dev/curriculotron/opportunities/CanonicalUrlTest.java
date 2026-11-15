package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.*;

import dev.curriculotron.opportunities.domain.CanonicalUrl;
import org.junit.jupiter.api.Test;

class CanonicalUrlTest {
  @Test
  void removesTrackingButPreservesJobIdentityAndEncoding() {
    assertThat(
            CanonicalUrl.normalize(
                "https://EXAMPLE.com:443/jobs/42?utm_source=mail&jobId=42#apply"))
        .isEqualTo("https://example.com/jobs/42?jobId=42");
    assertThat(CanonicalUrl.normalize("https://example.com/jobs?q=a%2Fb&ref=123"))
        .isEqualTo("https://example.com/jobs?q=a%2Fb&ref=123");
  }

  @Test
  void preservesMeaningfulDifferences() {
    assertThat(CanonicalUrl.normalize("http://example.com/jobs/42"))
        .isNotEqualTo(CanonicalUrl.normalize("https://example.com/jobs/42"));
    assertThat(CanonicalUrl.normalize("https://example.com/jobs?id=1"))
        .isNotEqualTo(CanonicalUrl.normalize("https://example.com/jobs?id=2"));
  }

  @Test
  void rejectsUnsafeOrMalformedLinks() {
    for (String url :
        new String[] {
          "javascript:alert(1)",
          "file:///tmp/job",
          "/jobs/42",
          "https://user:pass@example.com/jobs",
          "https://",
          ""
        }) {
      assertThatThrownBy(() -> CanonicalUrl.normalize(url))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
