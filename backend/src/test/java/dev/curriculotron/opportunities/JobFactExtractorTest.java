package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.*;

import dev.curriculotron.opportunities.domain.JobFactExtractor;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JobFactExtractorTest {
  private final JobFactExtractor extractor = new JobFactExtractor();
  private final Set<String> stack = Set.of("Java", "Spring Boot", "Kafka", "Microsserviços");
  private final Set<String> regions = Set.of("RM Porto Alegre");

  @Test
  void literalFactsOnly() {
    var f =
        extractor.extract(
            "Desenvolvedor(a) Back-end Java Pleno",
            "CWI",
            "Remoto · São Leopoldo - RS",
            "Experiência com Spring Boot e microsserviços. Desejável JavaScript.",
            stack,
            regions);
    assertThat(f.roleCompatible()).isTrue();
    assertThat(f.workMode()).isEqualTo("remote");
    assertThat(f.seniority()).isEqualTo("pleno");
    assertThat(f.stackFacts()).containsExactlyInAnyOrder("Java", "Spring Boot", "Microsserviços");
  }

  @Test
  void javascriptIsNotJavaAndUnknownStaysUnknown() {
    var f =
        extractor.extract(
            "Desenvolvedor Front-end", "X", null, "React e JavaScript", stack, regions);
    assertThat(f.roleCompatible()).isFalse();
    assertThat(f.stackFacts()).isEmpty();
    assertThat(f.workMode()).isEqualTo("unknown");
    assertThat(f.seniority()).isNull();
  }

  @Test
  void hybridInMetroRegionIsRecognized() {
    var f =
        extractor.extract(
            "Software Engineer Java Sr", "X", "Canoas - RS · hybrid", "Kafka", stack, regions);
    assertThat(f.workMode()).isEqualTo("hybrid");
    assertThat(f.region()).isEqualTo("RM Porto Alegre");
    assertThat(f.seniority()).isEqualTo("senior");
  }
}
