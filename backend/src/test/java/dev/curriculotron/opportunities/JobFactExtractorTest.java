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

  @Test
  void distinguishesCFromCPlusPlusAndCSharp() {
    var cppAndSharp =
        extractor.extract(
            "Backend Developer C++",
            "X",
            null,
            "Requirements: C++ and C#.",
            Set.of(" C ", ".NET"),
            regions);
    assertThat(cppAndSharp.stackFacts()).isEmpty();

    var exactC =
        extractor.extract(
            "Backend Developer C",
            "X",
            null,
            "Requirements: C and .NET.",
            Set.of(" C ", ".NET"),
            regions);
    assertThat(exactC.stackFacts()).containsExactlyInAnyOrder("C", ".NET");
  }

  @Test
  void ignoresNegatedIncidentalAndMismatchedVersionMentions() {
    var negatedAndIncidental =
        extractor.extract(
            "Service Designer",
            "CWI",
            null,
            "Não utilizamos Java. Outra equipe usa Spring Boot. About the company: Kafka.",
            stack,
            regions);
    assertThat(negatedAndIncidental.stackFacts()).isEmpty();

    var versionMismatch =
        extractor.extract(
            "Backend Developer Java",
            "CI&T",
            null,
            "Requirements: Java 21.",
            Set.of("Java 17"),
            regions);
    assertThat(versionMismatch.stackFacts()).isEmpty();
  }
}
