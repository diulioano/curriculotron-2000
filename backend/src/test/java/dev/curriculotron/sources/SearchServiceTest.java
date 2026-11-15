package dev.curriculotron.sources;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.curriculotron.audit.application.IntegrationStatusService;
import dev.curriculotron.opportunities.application.DailySelectionService;
import dev.curriculotron.orchestration.application.TaskRunService;
import dev.curriculotron.orchestration.domain.NewTaskRun;
import dev.curriculotron.orchestration.domain.TaskRunState;
import dev.curriculotron.orchestration.domain.TaskRunTrigger;
import dev.curriculotron.support.AppTestBase;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Seeded companies â†’ configured sources â†’ literal facts â†’ objective rank â†’ daily selection.
 */
class SearchServiceTest extends AppTestBase {
  @MockitoBean JobSourceFactory factory;
  @Autowired SearchService search;
  @Autowired TaskRunService tasks;
  @Autowired DailySelectionService selections;
  @Autowired IntegrationStatusService status;
  @Autowired ConfiguredSourceRepository boards;
  @Autowired EntityManager em;
  @Autowired SearchCriteriaRepository criteria;

  @Test
  void searchRanksSourcedJobsAndKeepsFailuresVisible() {
    assertThat(boards.boards())
        .extracting(ConfiguredSourceRepository.Board::adapter)
        .containsExactlyInAnyOrder(
            "lever:bv",
            "lever:ciandt",
            "oracle:epdm.fa.la1/CX_3001",
            "cwi:oportunidades",
            "gupy:db");

    JobSource cwi =
        () ->
            new JobSource.SourceResult(
                List.of(
                    new JobSource.Item(
                        "cwi:oportunidades",
                        "268",
                        "https://cwi.com.br/talentos/oportunidade/desenvolvedor-a-java-268",
                        "Desenvolvedor(a) Java\nRemoto",
                        "Desenvolvedor(a) Java",
                        "Remoto · São Leopoldo - RS"),
                    new JobSource.Item(
                        "cwi:oportunidades",
                        "209",
                        "https://cwi.com.br/talentos/oportunidade/estagio-209",
                        "Estágio em Suporte Técnico",
                        "Estágio em Suporte Técnico",
                        "Presencial")),
                List.of(),
                List.of("restrita"));
    JobSource down =
        () ->
            new JobSource.SourceResult(
                List.of(), List.of(new JobSource.Failure("x", "unavailable", "fora")), List.of());
    when(factory.forAdapter("cwi:oportunidades")).thenReturn(cwi);
    when(factory.forAdapter("lever:bv")).thenReturn(down);
    when(factory.forAdapter("lever:ciandt")).thenReturn(down);
    when(factory.forAdapter("oracle:epdm.fa.la1/CX_3001")).thenReturn(down);
    when(factory.forAdapter("gupy:db")).thenReturn(down);

    var run = tasks.createOrGet(NewTaskRun.system("search", TaskRunTrigger.manual, "t-1")).run();
    search.execute(run.id());

    var done = tasks.find(run.id()).orElseThrow();
    assertThat(done.state()).isEqualTo(TaskRunState.completed);
    assertThat(done.sourcesConsulted()).hasSize(5);
    assertThat(done.sourcesFailed())
        .containsExactlyInAnyOrder(
            "lever:bv", "lever:ciandt", "oracle:epdm.fa.la1/CX_3001", "gupy:db");

    var selected = selections.selectToday();
    assertThat(selected).hasSize(1);
    var row =
        (Object[])
            em.createNativeQuery(
                    "SELECT title,company_name,work_mode,fit FROM opportunity WHERE id=:id")
                .setParameter("id", selected.getFirst())
                .getSingleResult();
    assertThat(row).containsExactly("Desenvolvedor(a) Java", "CWI", "remote", "strong");
    assertThat(selections.secondary()).hasSize(1);

    // Second run of the same sources does not duplicate vacancies.
    var again = tasks.createOrGet(NewTaskRun.system("search", TaskRunTrigger.manual, "t-2")).run();
    search.execute(again.id());
    assertThat(
            ((Number) em.createNativeQuery("SELECT count(*) FROM opportunity").getSingleResult())
                .intValue())
        .isEqualTo(2);
  }
}
