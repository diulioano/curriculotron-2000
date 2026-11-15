package dev.curriculotron.salary;

import static org.assertj.core.api.Assertions.*;

import dev.curriculotron.salary.domain.GlassdoorLink;
import org.junit.jupiter.api.Test;

class GlassdoorLinkTest {
  @Test
  void rejectsUnsafeUrls() {
    for (String url :
        new String[] {
          "javascript:alert(1)",
          "https://www.glassdoor.com.br.evil.test/foo",
          "https://user:password@www.glassdoor.com.br/foo",
          "http://www.glassdoor.com.br/foo",
          "https://www.glassdoor.com.br:8443/foo"
        })
      assertThatThrownBy(() -> GlassdoorLink.validate(url))
          .isInstanceOf(IllegalArgumentException.class);
    assertThat(GlassdoorLink.validate("https://www.glassdoor.com.br/Salários/foo"))
        .contains("https://");
  }
}
