package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.*;

import dev.curriculotron.opportunities.application.port.out.OpportunityRepository;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.support.AppTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OpportunityRepositoryTest extends AppTestBase {
  @Autowired OpportunityRepository repository;
  @Autowired WriteExecutor writer;

  @Test
  void storesManualEvidenceWithoutInventingFacts() {
    String id = writer.write(() -> repository.createManual("Java remoto", "2026-11-14T12:00:00Z"));
    var job = repository.find(id).orElseThrow();
    assertThat(job.title()).isEqualTo("Java remoto");
    assertThat(job.companyName()).isEqualTo("Empresa a confirmar");
    assertThat(job.salary()).isNull();
    assertThat(job.facts()).containsExactly("Java remoto");
    assertThat(job.inferred()).isEmpty();
    writer.run(() -> repository.setDecision(id, "discarded", "2026-11-14T13:00:00Z"));
    assertThat(repository.find(id).orElseThrow().userDecision()).isEqualTo("discarded");
    assertThat(repository.discarded()).extracting(o -> o.id()).contains(id);
    writer.run(() -> repository.setDecision(id, "none", "2026-11-14T14:00:00Z"));
    assertThat(repository.find(id).orElseThrow().userDecision()).isEqualTo("none");
    assertThat(repository.discarded()).extracting(o -> o.id()).doesNotContain(id);
  }

  @Test
  void storesSourcedVacancyWithLiteralFactsAndObjectiveRank() {
    String id =
        writer.write(() ->
            repository.createFromSource(
                new OpportunityRepository.SourcedOpportunity(
                    "Desenvolvedor Java",
                    null,
                    "Porto Alegre",
                    "hybrid",
                    java.util.List.of("Java", "Spring"),
                    "strong",
                    80,
                    java.util.List.of("Java no perfil")),
                "2026-11-14T12:00:00Z"));
    var job = repository.find(id).orElseThrow();
    assertThat(job.companyName()).isEqualTo("Empresa a confirmar");
    assertThat(job.workMode()).isEqualTo("hybrid");
    assertThat(job.availability()).isEqualTo("open");
    assertThat(job.facts()).containsExactly("Java", "Spring");
    assertThat(job.inferred()).isEmpty();
    assertThat(job.fitReasons()).containsExactly("Java no perfil");
    assertThat(job.salary()).isNull();
  }

  @Test
  void rejectsDecisionsOutsideThisRoute() {
    String id = writer.write(() -> repository.createManual("Vaga", "2026-11-14T12:00:00Z"));
    assertThatThrownBy(() -> writer.run(() -> repository.setDecision(id, "applied", "x")))
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
    assertThat(repository.find(id).orElseThrow().userDecision()).isEqualTo("none");
  }
}
