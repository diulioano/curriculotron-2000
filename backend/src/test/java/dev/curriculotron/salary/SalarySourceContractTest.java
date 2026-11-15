package dev.curriculotron.salary;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.orchestration.application.TaskRunService;
import dev.curriculotron.orchestration.domain.*;
import dev.curriculotron.salary.adapter.out.persistence.SalaryEstimatePersistenceAdapter;
import dev.curriculotron.salary.application.SalaryEstimateService;
import dev.curriculotron.salary.application.port.out.SalaryReferenceSource;
import dev.curriculotron.salary.domain.*;
import dev.curriculotron.shared.*;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.support.AppTestBase;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class SalarySourceContractTest extends AppTestBase {
  @MockitoBean SalaryReferenceSource source;
  @Autowired SalaryEstimateService salary;
  @Autowired SalaryEstimatePersistenceAdapter store;
  @Autowired JdbcTemplate jdbc;
  @Autowired AppClock clock;
  @Autowired WriteExecutor writer;
  @Autowired TaskRunService tasks;

  @Test
  void nonSuccessStateCannotSmuggleAnEstimate() {
    var j = job("Contradictory response");
    when(source.lookup(any()))
        .thenReturn(
            new SalaryReferenceSource.Result(
                "blocked", estimate(SalaryEstimateService.profile(j))));
    assertThat(salary.enrich(j, task(), false)).isEqualTo("invalid");
    assertThat(store.latest(SalaryEstimateService.profile(j))).isEmpty();
  }

  @Test
  void providerLimitStopsRemainingProfiles() {
    var first = job("Limit first");
    var second = job("Limit second");
    when(source.lookup(any())).thenReturn(new SalaryReferenceSource.Result("provider_limit", null));
    salary.enrichIds(new LinkedHashSet<>(List.of(first.id(), second.id())), task());
    verify(source, times(1)).lookup(any());
  }

  private Opportunity job(String company) {
    var j =
        new Opportunity(
            Ids.newId(),
            "Desenvolvedor Java Pleno",
            company,
            company + " region",
            "unknown",
            "clt",
            null,
            List.of(),
            List.of(),
            List.of(),
            "none",
            "open",
            null);
    writer.run(() ->
        jdbc.update(
            "INSERT INTO opportunity(id,title,company_name,location,work_mode,contract_type,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)",
            j.id(),
            j.title(),
            j.companyName(),
            j.location(),
            j.workMode(),
            j.contractType(),
            clock.now().toString(),
            clock.now().toString()));
    return j;
  }

  private String task() {
    return tasks
        .createOrGet(NewTaskRun.system("salary", TaskRunTrigger.manual, Ids.newId()))
        .run()
        .id();
  }

  private SalaryEstimate estimate(SalaryProfile p) {
    return new SalaryEstimate(
        Ids.newId(),
        p,
        new BigDecimal("6000"),
        new BigDecimal("8000"),
        "monthly",
        "BRL",
        10,
        "Glassdoor",
        "https://www.glassdoor.com.br/Sal%C3%A1rio/fixture.htm",
        clock.now(),
        null,
        "Fixture 6000–8000 BRL.");
  }

  @Test
  void oneSuccessfulConsultationServesTwoJobs() {
    var a = job("Source success");
    var b = job("Source success");
    when(source.lookup(any()))
        .thenReturn(
            new SalaryReferenceSource.Result("found", estimate(SalaryEstimateService.profile(a))));
    var task = task();
    assertThat(salary.enrich(a, task, false)).isEqualTo("succeeded");
    assertThat(salary.enrich(b, task, false)).isEqualTo("succeeded");
    verify(source, times(1)).lookup(any());
  }

  @Test
  void failureStatesDoNotRetryOrInventSalary() {
    for (String state :
        List.of("not_found", "blocked", "unavailable", "provider_limit", "invalid")) {
      reset(source);
      var j = job("Source failure " + state);
      var task = task();
      when(source.lookup(any())).thenReturn(new SalaryReferenceSource.Result(state, null));
      assertThat(salary.enrich(j, task, false)).isEqualTo(state);
      assertThat(salary.enrich(j, task, false)).isEqualTo("already_attempted");
      verify(source, times(1)).lookup(any());
      assertThat(store.latest(SalaryEstimateService.profile(j))).isEmpty();
    }
  }

  @Test
  void incompatibleResponseIsRejectedAndAttemptIsCompleted() {
    var j = job("Expected company");
    when(source.lookup(any()))
        .thenReturn(
            new SalaryReferenceSource.Result(
                "found",
                estimate(
                    new SalaryProfile(
                        "Wrong company",
                        "desenvolvedor java",
                        "pleno",
                        "brasil",
                        "unknown",
                        "clt"))));
    assertThat(salary.enrich(j, task(), false)).isEqualTo("invalid");
    assertThat(store.latest(SalaryEstimateService.profile(j))).isEmpty();
  }

  @Test
  void marketFallbackRemainsSeparate() {
    var j = job("Market fallback");
    salary.importEstimate(estimate(SalaryEstimateService.profile(j).market()));
    assertThat(salary.enrich(j, task(), false)).isEqualTo("succeeded");
    verifyNoInteractions(source);
  }

  @Test
  void validatesCurrencyEvidenceSampleAndUnit() {
    var p = new SalaryProfile("Company", "java", "pleno", "brasil", "unknown", "clt");
    assertThatThrownBy(() ->
            new SalaryEstimate(
                "e",
                p,
                BigDecimal.ONE,
                BigDecimal.TEN,
                "monthly",
                "USD",
                1,
                "Glassdoor",
                "https://www.glassdoor.com.br/",
                clock.now(),
                null,
                "1–10"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            new SalaryEstimate(
                "e",
                p,
                BigDecimal.ONE,
                BigDecimal.TEN,
                "monthly",
                "BRL",
                -1,
                "Glassdoor",
                "https://www.glassdoor.com.br/",
                clock.now(),
                null,
                "1–10"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            new SalaryEstimate(
                "e",
                p,
                BigDecimal.ONE,
                BigDecimal.TEN,
                "monthly",
                "BRL",
                1,
                "Glassdoor",
                "https://www.glassdoor.com.br/",
                clock.now(),
                null,
                ""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(SalaryNormalizer.monthly(BigDecimal.ONE, "daily")).isNull();
    assertThat(SalaryNormalizer.monthly(BigDecimal.ONE, "unknown")).isNull();
  }
}
