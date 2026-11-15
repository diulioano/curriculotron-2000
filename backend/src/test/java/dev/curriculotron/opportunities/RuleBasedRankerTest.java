package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;

import dev.curriculotron.opportunities.domain.RuleBasedRanker;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleBasedRankerTest {
  private final RuleBasedRanker ranker = new RuleBasedRanker();
  private final RuleBasedRanker.Criteria criteria =
      new RuleBasedRanker.Criteria(
          Set.of("backend"),
          Set.of("Java", "Spring Boot"),
          Set.of("RM Porto Alegre"),
          Set.of("CWI"),
          true,
          true);

  @Test
  void strongFitRequiresRoleAndDocumentedStackAndLocation() {
    assertThat(rank("remote", "", "pleno", "", Set.of("Java"), true).fit()).isEqualTo("strong");
    assertThat(rank("unknown", "", "pleno", "", Set.of("Java"), true).fit()).isEqualTo("medium");
    assertThat(rank("hybrid", "São Paulo", "pleno", "", Set.of("Java"), true).fit())
        .isEqualTo("weak");
    assertThat(rank("remote", "", "pleno", "", Set.of("Python"), true).fit()).isEqualTo("weak");
  }

  @Test
  void priorityNeverOverridesSalaryOrJuniorJustification() {
    assertThat(rank("remote", "", "pleno", "", Set.of("Java"), false).fit()).isEqualTo("weak");
    assertThat(rank("remote", "", "junior", "", Set.of("Java"), true).fit()).isEqualTo("weak");
    var result =
        rank(
            "remote",
            "",
            "junior",
            "Salário compatível; empresa prioritária; plano de crescimento informado",
            Set.of("Java"),
            true);
    assertThat(result.fit()).isEqualTo("strong");
    assertThat(result.reasons()).anyMatch(reason -> reason.contains("crescimento"));
  }

  @Test
  void acceptsConfiguredHybridRegionAndNormalizesCase() {
    assertThat(rank("hybrid", "rm porto alegre", "pleno", "", Set.of("JAVA"), true).fit())
        .isEqualTo("strong");
    assertThat(rank("onsite", "RM Porto Alegre", "pleno", "", Set.of("Java"), true).fit())
        .isEqualTo("weak");
  }

  private RuleBasedRanker.Result rank(
      String mode,
      String region,
      String level,
      String justification,
      Set<String> facts,
      boolean salaryEligible) {
    return ranker.rank(
        new RuleBasedRanker.Input(
            "backend",
            "CWI",
            mode,
            region,
            level,
            justification,
            facts,
            new dev.curriculotron.opportunities.domain.ObjectiveFilter.Result(
                salaryEligible, true, "Salário a confirmar.")),
        criteria);
  }
}
