package dev.curriculotron.sources;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.curriculotron.opportunities.application.OpportunityIngestionService;
import dev.curriculotron.opportunities.application.port.out.CompatibilityRepository;
import dev.curriculotron.opportunities.application.port.out.SelectionRepository;
import dev.curriculotron.orchestration.application.TaskRunService;
import dev.curriculotron.orchestration.domain.NewTaskRun;
import dev.curriculotron.orchestration.domain.TaskRunTrigger;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.support.AppTestBase;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Search ingestion paths that share the assessment pipeline, isolated from selection counts. */
class SearchIngestionTest extends AppTestBase {
  @MockitoBean JobSourceFactory factory;
  @Autowired SearchService search;
  @Autowired TaskRunService tasks;
  @Autowired EntityManager em;
  @Autowired OpportunityIngestionService ingestion;
  @Autowired PublicationStore publications;
  @Autowired SelectionRepository selectionRows;
  @Autowired CompatibilityRepository assessments;
  @Autowired AppClock clock;

  @Test
  void shouldNotPublishOrSelectAnItemWhoseExternalIdentityConflictsWithAnotherUrl() {
    String a =
        ingestion.ingest(
            new JobSource.Item(
                "cwi:oportunidades",
                "conflict-a",
                "https://search-conflict.test/a",
                "Requirements: Java",
                "Backend Developer",
                "Remote"),
            "CWI");
    String b =
        ingestion.ingest(
            new JobSource.Item(
                "cwi:oportunidades",
                "conflict-b",
                "https://search-conflict.test/b",
                "Requirements: Java",
                "Backend Developer",
                "Remote"),
            "CWI");
    var originalDate = clock.localDate().minusDays(10);
    publications.record(a, originalDate);
    publications.record(b, originalDate.minusDays(1));
    JobSource empty = () -> new JobSource.SourceResult(List.of(), List.of(), List.of());
    when(factory.forAdapter(anyString())).thenReturn(empty);
    JobSource conflict =
        () ->
            new JobSource.SourceResult(
                List.of(
                    new JobSource.Item(
                        "cwi:oportunidades",
                        "conflict-a",
                        "https://search-conflict.test/b",
                        "Requirements: Java",
                        "Conflicting title",
                        "Remote",
                        clock.localDate())),
                List.of(),
                List.of());
    when(factory.forAdapter("cwi:oportunidades")).thenReturn(conflict);
    var run =
        tasks
            .createOrGet(NewTaskRun.system("search", TaskRunTrigger.manual, "conflict-search"))
            .run();
    search.execute(run.id());
    assertThat(publications.find(a)).isEqualTo(originalDate.toString());
    assertThat(publications.find(b)).isEqualTo(originalDate.minusDays(1).toString());
    assertThat(selectionRows.selected(clock.localDate())).doesNotContain(a, b);
  }

  @Test
  void juniorCountsOnlyAtCompaniesWithUserJustification() {
    String sap = ingestion.ingest(juniorItem("junior-sap"), "SAP");
    String bv = ingestion.ingest(juniorItem("junior-bv"), "BV");

    var sapAssessment = assessments.find(sap).orElseThrow();
    assertThat(sapAssessment.fit()).isEqualTo("strong");
    assertThat(sapAssessment.fitReasons()).anyMatch(r -> r.contains("Justificativa para júnior"));
    assertThat(assessments.find(bv).orElseThrow().fit()).isEqualTo("weak");
  }

  @Test
  void shouldAssessManualAndRoutineSearchesTheSameWay() {
    JobSource empty = () -> new JobSource.SourceResult(List.of(), List.of(), List.of());
    when(factory.forAdapter(anyString())).thenReturn(empty);

    when(factory.forAdapter("cwi:oportunidades")).thenReturn(single("parity-manual"));
    var manual =
        tasks.createOrGet(NewTaskRun.system("search", TaskRunTrigger.manual, "parity-m")).run();
    search.execute(manual.id());

    when(factory.forAdapter("cwi:oportunidades")).thenReturn(single("parity-routine"));
    var routine =
        tasks.createOrGet(NewTaskRun.system("search", TaskRunTrigger.routine, "parity-r")).run();
    search.execute(routine.id());

    var byManual = assessments.find(idOf("parity-manual")).orElseThrow();
    var byRoutine = assessments.find(idOf("parity-routine")).orElseThrow();
    assertThat(byManual.technologyStatus().name()).isEqualTo("matched");
    assertThat(byManual.roleStatus().name()).isEqualTo("matched");
    assertThat(byRoutine.roleStatus()).isEqualTo(byManual.roleStatus());
    assertThat(byRoutine.technologyStatus()).isEqualTo(byManual.technologyStatus());
    assertThat(byRoutine.fit()).isEqualTo(byManual.fit());
    assertThat(byRoutine.fitScore()).isEqualTo(byManual.fitScore());
    assertThat(byRoutine.fitReasons()).isEqualTo(byManual.fitReasons());
  }

  private JobSource single(String externalId) {
    return () ->
        new JobSource.SourceResult(
            List.of(
                new JobSource.Item(
                    "cwi:oportunidades",
                    externalId,
                    "https://parity.test/" + externalId,
                    "Requirements: Java, Spring Boot",
                    "Desenvolvedor(a) Java",
                    "Remoto",
                    clock.localDate())),
            List.of(),
            List.of());
  }

  private String idOf(String externalId) {
    return (String)
        em.createNativeQuery(
                "SELECT opportunity_id FROM opportunity_source WHERE external_id=:external")
            .setParameter("external", externalId)
            .getSingleResult();
  }

  private static JobSource.Item juniorItem(String externalId) {
    return new JobSource.Item(
        "test",
        externalId,
        "https://example.com/vaga/" + externalId,
        "Desenvolvedor Java Júnior\nRemoto",
        "Desenvolvedor Java Júnior",
        "Remoto");
  }
}
