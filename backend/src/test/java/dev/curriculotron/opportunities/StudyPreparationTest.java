package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;

import dev.curriculotron.opportunities.domain.StudyPreparation;
import dev.curriculotron.opportunities.domain.StudyPreparation.Item;
import dev.curriculotron.opportunities.domain.StudyPreparation.ProfileTechnologies;
import dev.curriculotron.opportunities.domain.StudyPreparation.Tier;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import dev.curriculotron.opportunities.domain.VacancyEvidence.Completeness;
import java.util.List;
import org.junit.jupiter.api.Test;

/** US006: preparação vinculada às evidências da vaga sem vazar negações ou outra equipe. */
class StudyPreparationTest {

  private final StudyPreparation preparation = new StudyPreparation();

  @Test
  void groupsByStrongestRequirementExcludesNegationsAndMatchesProfileTiers() {
    var profile = new ProfileTechnologies(3L, "Java 17", List.of("Spring Boot"), List.of("Kotlin"));
    var evidence =
        List.of(
            evidence(
                """
                Requisitos: experiência sólida com Java e Spring Boot.
                Diferenciais: desejável Docker e Spring Boot; conhecimento em Kotlin.
                Responsabilidades: a equipe usa Docker e trabalha com Kafka no dia a dia.
                Não exigimos Python.
                Sobre a empresa: outras equipes utilizam Angular.
                """));

    var plan = preparation.plan(evidence, profile);

    assertThat(plan.profileVersion()).isEqualTo(3L);
    assertThat(plan.required())
        .extracting(Item::technology, Item::profileTier)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple("Java 17", Tier.primary),
            org.assertj.core.groups.Tuple.tuple("Spring Boot", Tier.secondary));
    assertThat(plan.optional())
        .extracting(Item::technology, Item::profileTier)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple("Docker", Tier.none),
            org.assertj.core.groups.Tuple.tuple("Kotlin", Tier.flexible));
    assertThat(plan.mentioned())
        .extracting(Item::technology, Item::profileTier)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("Kafka", Tier.none));
    assertThat(plan.required())
        .extracting(Item::technology)
        .doesNotContain("Docker", "Python", "Angular");
    assertThat(plan.optional())
        .extracting(Item::technology)
        .doesNotContain("Spring Boot", "Python", "Angular");
    assertThat(plan.mentioned())
        .extracting(Item::technology)
        .doesNotContain("Docker", "Python", "Angular");
  }

  @Test
  void returnsNullVersionAndNoneTiersWithoutSavedProfile() {
    var plan =
        preparation.plan(
            List.of(evidence("Requisitos: Angular e TypeScript.")), ProfileTechnologies.NONE);

    assertThat(plan.profileVersion()).isNull();
    assertThat(plan.required())
        .extracting(Item::technology, Item::profileTier)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple("Angular", Tier.none),
            org.assertj.core.groups.Tuple.tuple("TypeScript", Tier.none));
  }

  private static VacancyEvidence evidence(String text) {
    return new VacancyEvidence(
        "ev-1",
        "opp-1",
        null,
        "manual",
        "Desenvolvedor Full Stack",
        "Remoto",
        text,
        Completeness.complete,
        null,
        1,
        null,
        null);
  }
}
