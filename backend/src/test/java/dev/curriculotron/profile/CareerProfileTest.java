package dev.curriculotron.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.curriculotron.profile.domain.CareerProfile;
import dev.curriculotron.profile.domain.FieldProvenance;
import dev.curriculotron.profile.domain.FieldProvenance.Source;
import dev.curriculotron.profile.domain.InvalidProfileException;
import dev.curriculotron.profile.domain.ProfileDraft.Field;
import dev.curriculotron.profile.domain.Seniority;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CareerProfileTest {

  static CareerProfile profile(
      List<String> secondary, List<String> flexible, List<Seniority> levels, BigDecimal clt) {
    return new CareerProfile(
        1,
        "  Desenvolvedor   Backend ",
        "Java",
        secondary,
        flexible,
        Seniority.pleno,
        levels,
        List.of("Remoto"),
        clt,
        null,
        CareerProfile.Origin.manual);
  }

  @Test
  void keepsEachTechnologyInItsStrongestGroup() {
    var value =
        profile(
            List.of("Spring Boot", "java", "spring boot"),
            List.of("Kotlin", "Spring  Boot", "Java"),
            List.of(Seniority.pleno),
            new BigDecimal("8000.00"));

    assertThat(value.role()).isEqualTo("Desenvolvedor Backend");
    assertThat(value.secondaryTechnologies()).containsExactly("Spring Boot");
    assertThat(value.flexibleTechnologies()).containsExactly("Kotlin");
    assertThat(value.technologies()).containsExactly("Java", "Spring Boot", "Kotlin");
    assertThat(value.juniorFloorCltMonthlyBrl()).isEqualTo(new BigDecimal("8000"));
  }

  @Test
  void rejectsMissingTargetLevelsDuplicatedLevelsAndInvalidFloors() {
    assertThatThrownBy(() -> profile(List.of(), List.of(), List.of(), null))
        .isInstanceOf(InvalidProfileException.class);
    assertThatThrownBy(
            () -> profile(List.of(), List.of(), List.of(Seniority.pleno, Seniority.pleno), null))
        .isInstanceOf(InvalidProfileException.class);
    assertThatThrownBy(
            () -> profile(List.of(), List.of(), List.of(Seniority.pleno), new BigDecimal("0")))
        .isInstanceOf(InvalidProfileException.class);
    assertThatThrownBy(
            () -> profile(List.of(), List.of(), List.of(Seniority.pleno), new BigDecimal("10.123")))
        .isInstanceOf(InvalidProfileException.class);
  }

  @Test
  void keepsProvenanceAcrossVersionsAndTreatsMissingMetadataAsEmpty() {
    var base = profile(List.of(), List.of(), List.of(Seniority.pleno), null);
    var tracked =
        new CareerProfile(
            1,
            base.role(),
            base.primaryTechnology(),
            base.secondaryTechnologies(),
            base.flexibleTechnologies(),
            base.currentSeniority(),
            base.targetLevels(),
            base.priorities(),
            null,
            null,
            CareerProfile.Origin.markdown,
            Map.of(Field.role, new FieldProvenance(Source.edited, 3, "Cargo: Dev")));

    assertThat(base.provenance()).isEmpty();
    assertThat(tracked.withVersion(4).provenance()).containsOnlyKeys(Field.role);
    assertThat(tracked.sameContent(base)).isTrue();
  }

  @Test
  void rejectsInconsistentProvenance() {
    assertThatThrownBy(() -> new FieldProvenance(Source.imported, null, null))
        .isInstanceOf(InvalidProfileException.class);
    assertThatThrownBy(() -> new FieldProvenance(Source.declared, 2, "Cargo: Dev"))
        .isInstanceOf(InvalidProfileException.class);
    assertThatThrownBy(() -> new FieldProvenance(Source.inferred, 2, null))
        .isInstanceOf(InvalidProfileException.class);
    assertThatThrownBy(() -> new FieldProvenance(Source.edited, 0, "x"))
        .isInstanceOf(InvalidProfileException.class);
    assertThatThrownBy(() -> new FieldProvenance(Source.edited, 1, "x".repeat(201)))
        .isInstanceOf(InvalidProfileException.class);
    assertThat(new FieldProvenance(Source.declared, null, null).line()).isNull();
  }

  @Test
  void comparesContentIgnoringVersionAndOrigin() {
    var manual = profile(List.of("React"), List.of(), List.of(Seniority.pleno), null);
    var imported =
        new CareerProfile(
            7,
            manual.role(),
            manual.primaryTechnology(),
            manual.secondaryTechnologies(),
            manual.flexibleTechnologies(),
            manual.currentSeniority(),
            manual.targetLevels(),
            manual.priorities(),
            null,
            null,
            CareerProfile.Origin.markdown);

    assertThat(manual.sameContent(imported)).isTrue();
    assertThat(manual.sameContent(profile(List.of(), List.of(), List.of(Seniority.pleno), null)))
        .isFalse();
  }
}
