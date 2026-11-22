package dev.curriculotron.profile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.curriculotron.profile.domain.FieldProvenance;
import dev.curriculotron.profile.domain.FieldProvenance.Source;
import dev.curriculotron.profile.domain.ProfileDraft.Field;
import dev.curriculotron.profile.domain.ProfileDraft.WarningCode;
import dev.curriculotron.profile.domain.ProfileMarkdownParser;
import dev.curriculotron.profile.domain.Seniority;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileMarkdownParserTest {

  @Test
  void readsLabeledFieldsWithoutWarningsForTheirValues() {
    var preview =
        ProfileMarkdownParser.parse(
            """
            # Perfil
            - **Cargo:** Desenvolvedor Backend Java
            - Tecnologia principal: Java
            - Tecnologias secundárias: Spring Boot, Angular & Java
            - Tecnologias flexíveis: Kotlin; Golang
            - Senioridade atual: Pleno
            - Níveis alvo: Pleno, Sênior
            - Prioridades: Remoto, Salário
            - Piso júnior CLT: R$ 8.000
            - Piso júnior PJ: 12000,50
            """);

    var draft = preview.draft();
    assertThat(draft.role()).isEqualTo("Desenvolvedor Backend Java");
    assertThat(draft.primaryTechnology()).isEqualTo("Java");
    assertThat(draft.secondaryTechnologies()).containsExactly("Spring Boot", "Angular");
    assertThat(draft.flexibleTechnologies()).containsExactly("Kotlin", "Golang");
    assertThat(draft.currentSeniority()).isEqualTo(Seniority.pleno);
    assertThat(draft.targetLevels()).containsExactly(Seniority.pleno, Seniority.senior);
    assertThat(draft.priorities()).containsExactly("Remoto", "Salário");
    assertThat(draft.juniorFloorCltMonthlyBrl()).isEqualByComparingTo("8000");
    assertThat(draft.juniorFloorPjMonthlyBrl()).isEqualByComparingTo(new BigDecimal("12000.50"));
    assertThat(preview.warnings()).isEmpty();
    assertThat(preview.provenance()).hasSize(Field.values().length);
    assertThat(preview.provenance().get(Field.role))
        .isEqualTo(new FieldProvenance(Source.imported, 2, "- Cargo: Desenvolvedor Backend Java"));
    assertThat(preview.provenance().get(Field.juniorFloorPjMonthlyBrl).line()).isEqualTo(10);
    assertThat(preview.provenance().values()).allMatch(p -> p.source() == Source.imported);
  }

  @Test
  void pointsSectionItemsToTheirLineAndOmitsMissingFields() {
    var preview =
        ProfileMarkdownParser.parse(
            """
            ## Cargo
            Engenheiro de Software
            ## Prioridades
            - Remoto
            """);

    assertThat(preview.provenance())
        .containsOnlyKeys(Field.role, Field.priorities)
        .containsEntry(
            Field.role, new FieldProvenance(Source.imported, 2, "Engenheiro de Software"))
        .containsEntry(Field.priorities, new FieldProvenance(Source.imported, 4, "- Remoto"));
  }

  @Test
  void truncatesLongEvidenceInsteadOfKeepingTheWholeLine() {
    var preview =
        ProfileMarkdownParser.parse("Cargo: Dev\nPrioridades: " + "Empresa X, ".repeat(40));

    var evidence = preview.provenance().get(Field.priorities);
    assertThat(evidence.line()).isEqualTo(2);
    assertThat(evidence.excerpt()).hasSize(FieldProvenance.MAX_EXCERPT).endsWith("…");
  }

  @Test
  void readsSectionsWithBulletItems() {
    var preview =
        ProfileMarkdownParser.parse(
            """
            ## Cargo
            Engenheiro de Software
            ## Tecnologias secundárias
            - Spring Boot
            - React
            ## Prioridades
            - Remoto
            ## Outra seção
            - Ignorada
            """);

    assertThat(preview.draft().role()).isEqualTo("Engenheiro de Software");
    assertThat(preview.draft().secondaryTechnologies()).containsExactly("Spring Boot", "React");
    assertThat(preview.draft().priorities()).containsExactly("Remoto");
    assertThat(preview.warnings())
        .extracting(w -> w.field() + ":" + w.code())
        .contains("primaryTechnology:missing", "currentSeniority:missing", "targetLevels:missing");
  }

  @Test
  void infersFromResumeHeadlineAndMarksFieldsAsInferred() {
    var preview =
        ProfileMarkdownParser.parse(
            """
            # Pessoa Exemplo

            **Software Developer | Java, Spring Boot, Angular & React**

            ## Professional Summary

            Mid-level software developer focused on Java backend development.
            """);

    var draft = preview.draft();
    assertThat(draft.role()).isEqualTo("Software Developer");
    assertThat(draft.primaryTechnology()).isEqualTo("Java");
    assertThat(draft.secondaryTechnologies()).containsExactly("Spring Boot", "Angular", "React");
    assertThat(draft.currentSeniority()).isEqualTo(Seniority.pleno);
    assertThat(draft.targetLevels()).containsExactly(Seniority.pleno);
    assertThat(preview.warnings())
        .filteredOn(w -> w.code() == WarningCode.inferred)
        .extracting(w -> w.field())
        .containsExactlyInAnyOrder(
            Field.role,
            Field.primaryTechnology,
            Field.secondaryTechnologies,
            Field.currentSeniority,
            Field.targetLevels);
    assertThat(preview.warnings())
        .filteredOn(w -> w.code() == WarningCode.missing)
        .extracting(w -> w.field())
        .containsExactlyInAnyOrder(Field.juniorFloorCltMonthlyBrl, Field.juniorFloorPjMonthlyBrl);
    var headline =
        new FieldProvenance(
            Source.inferred, 3, "Software Developer | Java, Spring Boot, Angular & React");
    var summary =
        new FieldProvenance(
            Source.inferred,
            7,
            "Mid-level software developer focused on Java backend development.");
    assertThat(preview.provenance())
        .containsEntry(Field.role, headline)
        .containsEntry(Field.primaryTechnology, headline)
        .containsEntry(Field.secondaryTechnologies, headline)
        .containsEntry(Field.currentSeniority, summary)
        .containsEntry(Field.targetLevels, summary)
        .doesNotContainKeys(Field.juniorFloorCltMonthlyBrl, Field.juniorFloorPjMonthlyBrl);
  }

  @Test
  void reportsInvalidValuesInsteadOfGuessing() {
    var preview =
        ProfileMarkdownParser.parse(
            """
            Senioridade atual: estagiário
            Níveis alvo: Pleno, Diretor
            Piso júnior CLT: oito mil
            Piso júnior PJ: 1000.123
            """);

    assertThat(preview.draft().currentSeniority()).isNull();
    assertThat(preview.draft().targetLevels()).containsExactly(Seniority.pleno);
    assertThat(preview.draft().juniorFloorCltMonthlyBrl()).isNull();
    assertThat(preview.draft().juniorFloorPjMonthlyBrl()).isNull();
    assertThat(preview.warnings())
        .filteredOn(w -> w.code() == WarningCode.invalid)
        .extracting(w -> w.field())
        .containsExactlyInAnyOrderElementsOf(
            List.of(
                Field.currentSeniority,
                Field.targetLevels,
                Field.juniorFloorCltMonthlyBrl,
                Field.juniorFloorPjMonthlyBrl));
    assertThat(preview.provenance()).containsOnlyKeys(Field.targetLevels);
  }
}
