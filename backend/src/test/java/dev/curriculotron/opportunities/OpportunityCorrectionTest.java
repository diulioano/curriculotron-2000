package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.curriculotron.audit.application.ActivityLogService;
import dev.curriculotron.opportunities.application.AssessOpportunityService;
import dev.curriculotron.opportunities.application.InvalidCorrectionException;
import dev.curriculotron.opportunities.application.OpportunityCorrectionService;
import dev.curriculotron.opportunities.application.OpportunityIngestionService;
import dev.curriculotron.opportunities.application.OpportunityMergedException;
import dev.curriculotron.opportunities.application.OpportunityNotFoundException;
import dev.curriculotron.opportunities.application.port.out.OpportunityCorrectionRepository;
import dev.curriculotron.opportunities.domain.CorrectableOpportunityField;
import dev.curriculotron.opportunities.domain.VacancyEvidence.Completeness;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.Ids;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.sources.JobSource;
import dev.curriculotron.sources.PublicationStore;
import dev.curriculotron.support.AppTestBase;
import dev.curriculotron.support.TestSession;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Correção manual de vaga contra SQLite real com Flyway V14. */
@ExtendWith(OutputCaptureExtension.class)
class OpportunityCorrectionTest extends AppTestBase {
  private static final AtomicInteger SEQUENCE = new AtomicInteger();

  @Autowired MockMvc mvc;
  @Autowired OpportunityIngestionService ingestion;
  @Autowired JdbcClient jdbc;
  @Autowired AppClock clock;
  @Autowired PublicationStore publications;
  @Autowired OpportunityCorrectionService correctionService;
  @Autowired OpportunityCorrectionRepository correctionRepository;
  @Autowired AssessOpportunityService assessor;
  @Autowired WriteExecutor writer;
  @Autowired ActivityLogService activity;

  private TestSession session;

  @BeforeEach
  void login() throws Exception {
    clock.setClock(Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), AppClock.ZONE));
    TestSession.setup(mvc, TestSession.PASSWORD);
    session = TestSession.login(mvc, TestSession.PASSWORD);
  }

  private String newOpportunity() {
    int n = SEQUENCE.incrementAndGet();
    return ingestion.ingest(
        new JobSource.Item(
            "lever:ciandt",
            "correction-" + n,
            "https://jobs.lever.co/ciandt/correction-" + n,
            "Requisitos: Java e Spring Boot\nTrabalho remoto",
            "Desenvolvedor Backend Pleno",
            "Remoto",
            clock.localDate(),
            Completeness.complete,
            null),
        "CI&T");
  }

  private ResultActions correct(String id, String body) throws Exception {
    return mvc.perform(
        post("/api/opportunities/" + id + "/corrections")
            .session(session.session())
            .header("X-CSRF-Token", session.csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private long count(String sql, Object... params) {
    return jdbc.sql(sql).params(params).query(Long.class).single();
  }

  private long corrections(String id) {
    return count("SELECT count(*) FROM opportunity_correction WHERE opportunity_id = ?", id);
  }

  private long logs(String id) {
    return count(
        "SELECT count(*) FROM activity_log WHERE action = 'opportunity.corrected' AND target_id = ?",
        id);
  }

  private List<Map<String, Object>> originalEvidence(String id) {
    return jdbc.sql(
            "SELECT id, source_id, title, location, description_text, content_revision, fetched_at"
                + " FROM opportunity_evidence WHERE opportunity_id = ? ORDER BY id")
        .param(id)
        .query()
        .listOfRows();
  }

  private List<Map<String, Object>> sources(String id) {
    return jdbc.sql(
            "SELECT id, source, external_id, canonical_url, fetched_at, raw_excerpt"
                + " FROM opportunity_source WHERE opportunity_id = ? ORDER BY id")
        .param(id)
        .query()
        .listOfRows();
  }

  private void assertRejected(String id, String body, String code) throws Exception {
    long before = corrections(id);
    long logsBefore = logs(id);
    var snapshot = jdbc.sql("SELECT * FROM opportunity WHERE id = ?").param(id).query().singleRow();
    correct(id, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value(code));
    assertThat(corrections(id)).isEqualTo(before);
    assertThat(logs(id)).isEqualTo(logsBefore);
    assertThat(jdbc.sql("SELECT * FROM opportunity WHERE id = ?").param(id).query().singleRow())
        .isEqualTo(snapshot);
  }

  @Test
  void shouldReturnDeadlinesInBatchOmittingOpportunitiesWithoutDeadline() {
    String withDeadline = newOpportunity();
    String withoutDeadline = newOpportunity();
    jdbc.sql("UPDATE opportunity SET deadline_at = ? WHERE id = ?")
        .params("2026-12-01", withDeadline)
        .update();

    assertThat(
            correctionRepository.deadlinesAt(List.of(withDeadline, withoutDeadline, "inexistente")))
        .containsExactly(Map.entry(withDeadline, "2026-12-01"));
    assertThat(correctionRepository.deadlinesAt(List.of())).isEmpty();
    assertThat(correctionRepository.deadlineAt(withDeadline)).contains("2026-12-01");
    assertThat(correctionRepository.deadlineAt(withoutDeadline)).isEmpty();
  }

  @Test
  void shouldExposeEachOpportunityDeadlineInListResponse() throws Exception {
    String withDeadline = newOpportunity();
    String withoutDeadline = newOpportunity();
    jdbc.sql("UPDATE opportunity SET deadline_at = ? WHERE id = ?")
        .params("2026-12-01", withDeadline)
        .update();
    for (String id : List.of(withDeadline, withoutDeadline)) {
      mvc.perform(
              post("/api/opportunities/" + id + "/discard")
                  .session(session.session())
                  .header("X-CSRF-Token", session.csrf()))
          .andExpect(status().isOk());
    }

    String body =
        mvc.perform(get("/api/opportunities?list=discarded").session(session.session()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(
            JsonPath.<List<String>>read(
                body, "$.items[?(@.id=='" + withDeadline + "')].deadlineAt"))
        .containsExactly("2026-12-01");
    assertThat(
            JsonPath.<List<String>>read(
                body, "$.items[?(@.id=='" + withoutDeadline + "')].deadlineAt"))
        .containsExactly((String) null);
  }

  @Test
  void shouldRejectUpdateAndDeleteOnAppendOnlyCorrectionTable() {
    String id = newOpportunity();
    String correctionId = Ids.newId();
    jdbc.sql(
            "INSERT INTO opportunity_correction(id, opportunity_id, field_name, previous_value,"
                + " corrected_value, corrected_at) VALUES (?, ?, 'title', 'a', 'b',"
                + " '2026-11-14T15:00:00.000Z')")
        .params(correctionId, id)
        .update();

    assertThatThrownBy(() ->
            jdbc.sql("UPDATE opportunity_correction SET corrected_value = 'x' WHERE id = ?")
                .param(correctionId)
                .update())
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("opportunity_correction e append-only");
    assertThatThrownBy(() ->
            jdbc.sql("DELETE FROM opportunity_correction WHERE id = ?")
                .param(correctionId)
                .update())
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("opportunity_correction e append-only");
    assertThat(corrections(id)).isEqualTo(1);
  }

  @Test
  void shouldEnforceSchemaChecksOnCorrectionTable() {
    String id = newOpportunity();
    assertThatThrownBy(() ->
            jdbc.sql(
                    "INSERT INTO opportunity_correction(id, opportunity_id, field_name,"
                        + " corrected_at) VALUES (?, ?, 'fit', '2026-11-14T15:00:00.000Z')")
                .params(Ids.newId(), id)
                .update())
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() ->
            jdbc.sql(
                    "INSERT INTO opportunity_correction(id, opportunity_id, field_name, author,"
                        + " corrected_at) VALUES (?, ?, 'title', 'bot:x',"
                        + " '2026-11-14T15:00:00.000Z')")
                .params(Ids.newId(), id)
                .update())
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void shouldApplyPartialCorrectionAndKeepOriginalSourcesAndEvidence() throws Exception {
    String id = newOpportunity();
    var evidenceBefore = originalEvidence(id);
    var sourcesBefore = sources(id);
    assertThat(evidenceBefore).isNotEmpty();
    assertThat(sourcesBefore).isNotEmpty();

    correct(
            id,
            """
            {"workMode":"hybrid","contractType":"clt","location":"  São Leopoldo, RS ",
             "reason":"Confirmado no edital oficial."}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.workMode").value("hybrid"))
        .andExpect(jsonPath("$.contractType").value("clt"))
        .andExpect(jsonPath("$.location").value("São Leopoldo, RS"))
        .andExpect(jsonPath("$.title").value("Desenvolvedor Backend Pleno"))
        .andExpect(jsonPath("$.corrections", hasSize(3)))
        .andExpect(
            jsonPath("$.corrections[*].fieldName")
                .value(containsInAnyOrder("work_mode", "contract_type", "location")))
        .andExpect(jsonPath("$.corrections[0].authorLabel").value("você"))
        .andExpect(jsonPath("$.corrections[0].reason").value("Confirmado no edital oficial."))
        .andExpect(jsonPath("$.corrections[0].correctedAt").value("2026-11-14T15:00:00Z"))
        .andExpect(
            jsonPath("$.corrections[?(@.fieldName=='location')].previousValue")
                .value(org.hamcrest.Matchers.hasItem("Remoto")))
        .andExpect(
            jsonPath("$.corrections[?(@.fieldName=='work_mode')].correctedValue")
                .value(org.hamcrest.Matchers.hasItem("hybrid")))
        .andExpect(jsonPath("$.sources", hasSize(sourcesBefore.size())));

    assertThat(corrections(id)).isEqualTo(3);
    assertThat(logs(id)).isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM activity_log WHERE action = 'opportunity.corrected'"
                    + " AND target_id = ? AND target_type = 'opportunity' AND author = 'user'",
                id))
        .isEqualTo(1);
    assertThat(sources(id)).isEqualTo(sourcesBefore);
    assertThat(originalEvidence(id)).isEqualTo(evidenceBefore);

    mvc.perform(get("/api/opportunities/" + id).session(session.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workMode").value("hybrid"))
        .andExpect(jsonPath("$.corrections", hasSize(3)));
  }

  @Test
  void shouldRecordOnlyFieldsThatActuallyChanged() throws Exception {
    String id = newOpportunity();

    correct(id, "{\"title\":\"Desenvolvedor Backend Pleno\",\"workMode\":\"remote\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("invalid_correction"));
    assertThat(corrections(id)).isZero();

    correct(id, "{\"title\":\"Desenvolvedor(a) Full Stack\",\"companyName\":\"CI&T\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.corrections", hasSize(1)))
        .andExpect(jsonPath("$.corrections[0].fieldName").value("title"))
        .andExpect(
            jsonPath("$.corrections[0].correctedValue").value("Desenvolvedor(a) Full Stack"));
  }

  @Test
  void shouldSetUpdateAndExplicitlyClearSalary() throws Exception {
    String id = newOpportunity();

    correct(
            id,
            """
            {"salary":{"min":9500,"max":12000.50,"unit":"monthly"},"reason":"Edital"}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.salary.min").value(9500))
        .andExpect(jsonPath("$.salary.max").value(12000.5))
        .andExpect(jsonPath("$.salary.unit").value("monthly"))
        .andExpect(
            jsonPath("$.corrections[*].fieldName")
                .value(containsInAnyOrder("salary_min", "salary_max", "salary_unit")))
        .andExpect(
            jsonPath("$.corrections[?(@.fieldName=='salary_max')].correctedValue")
                .value(org.hamcrest.Matchers.hasItem("12000.5")))
        .andExpect(
            jsonPath("$.corrections[?(@.fieldName=='salary_min')].previousValue")
                .value(org.hamcrest.Matchers.hasItem((Object) null)));

    // Mesmo valor reenviado não gera nova correção.
    correct(id, "{\"salary\":{\"min\":9500.00,\"max\":12000.5,\"unit\":\"monthly\"}}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("invalid_correction"));

    // Omitir o salário preserva o valor.
    correct(id, "{\"availability\":\"closed\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.salary.min").value(9500))
        .andExpect(jsonPath("$.availability").value("closed"));

    // Só o teto muda.
    correct(id, "{\"salary\":{\"min\":9500,\"max\":13000,\"unit\":\"monthly\"}}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.salary.max").value(13000));

    correct(id, "{\"clearSalary\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.salary").isEmpty())
        .andExpect(
            jsonPath("$.corrections[?(@.fieldName=='salary_min' && @.correctedValue==null)]")
                .isNotEmpty());

    assertThat(
            jdbc.sql("SELECT salary_min, salary_max, salary_unit FROM opportunity WHERE id = ?")
                .param(id)
                .query()
                .singleRow())
        .containsEntry("salary_min", null)
        .containsEntry("salary_max", null)
        .containsEntry("salary_unit", "unknown");
  }

  @Test
  void shouldDistinguishOmittedDatesFromExplicitNull() throws Exception {
    String id = newOpportunity();
    String originalPublished =
        jdbc.sql("SELECT published_at FROM opportunity WHERE id = ?")
            .param(id)
            .query(String.class)
            .optional()
            .orElse(null);

    correct(id, "{\"deadlineAt\":\"2026-11-30\",\"publishedAt\":\"2026-11-11\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deadlineAt").value("2026-11-30"))
        .andExpect(jsonPath("$.publishedAt").value("2026-11-11"))
        .andExpect(
            jsonPath("$.corrections[*].fieldName")
                .value(containsInAnyOrder("deadline_at", "published_at")))
        .andExpect(
            jsonPath("$.corrections[?(@.fieldName=='published_at')].previousValue")
                .value(org.hamcrest.Matchers.hasItem(originalPublished)));

    // Chaves omitidas preservam as duas datas.
    correct(id, "{\"availability\":\"closed\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deadlineAt").value("2026-11-30"))
        .andExpect(jsonPath("$.publishedAt").value("2026-11-11"));

    // null explícito limpa apenas a data indicada.
    correct(id, "{\"deadlineAt\":null}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deadlineAt").doesNotExist())
        .andExpect(jsonPath("$.publishedAt").value("2026-11-11"));
    correct(id, "{\"publishedAt\":null}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publishedAt").doesNotExist());

    assertThat(
            jdbc.sql("SELECT deadline_at, published_at FROM opportunity WHERE id = ?")
                .param(id)
                .query()
                .singleRow())
        .containsEntry("deadline_at", null)
        .containsEntry("published_at", null);

    // Limpar o que já está vazio não é alteração.
    correct(id, "{\"deadlineAt\":null}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("invalid_correction"));
  }

  @Test
  void shouldRejectInvalidCorrectionsWithoutTouchingTheDatabase() throws Exception {
    String id = newOpportunity();
    correct(id, "{\"availability\":\"closed\"}").andExpect(status().isOk());

    assertRejected(id, "{}", "invalid_correction");
    assertRejected(id, "{\"reason\":\"só motivo\"}", "invalid_correction");
    assertRejected(id, "{\"availability\":\"closed\"}", "invalid_correction");
    assertRejected(id, "{\"clearSalary\":false}", "invalid_correction");
    assertRejected(
        id, "{\"salary\":{\"min\":9000,\"max\":8000,\"unit\":\"monthly\"}}", "invalid_correction");
    assertRejected(id, "{\"salary\":{\"unit\":\"monthly\"}}", "invalid_correction");
    assertRejected(
        id,
        "{\"clearSalary\":true,\"salary\":{\"min\":1,\"max\":2,\"unit\":\"monthly\"}}",
        "invalid_correction");

    assertRejected(
        id, "{\"salary\":{\"min\":-1,\"max\":2,\"unit\":\"monthly\"}}", "validation_failed");
    assertRejected(id, "{\"salary\":{\"min\":1,\"max\":2}}", "validation_failed");
    assertRejected(id, "{\"title\":\"   \"}", "validation_failed");
    assertRejected(id, "{\"title\":\"" + "x".repeat(241) + "\"}", "validation_failed");
    assertRejected(id, "{\"companyName\":\"\"}", "validation_failed");
    assertRejected(id, "{\"companyName\":\"" + "x".repeat(161) + "\"}", "validation_failed");
    assertRejected(id, "{\"location\":\"" + "x".repeat(161) + "\"}", "validation_failed");
    assertRejected(
        id,
        "{\"workMode\":\"hybrid\",\"reason\":\"" + "x".repeat(501) + "\"}",
        "validation_failed");

    assertRejected(id, "{\"workMode\":\"sideways\"}", "malformed_request");
    assertRejected(id, "{\"contractType\":\"freelancer\"}", "malformed_request");
    assertRejected(id, "{\"availability\":\"maybe\"}", "malformed_request");
    assertRejected(id, "{\"salary\":{\"min\":1,\"unit\":\"weekly\"}}", "malformed_request");
    assertRejected(id, "{\"deadlineAt\":\"amanhã\"}", "malformed_request");
    assertRejected(id, "{\"publishedAt\":\"2026-13-40\"}", "malformed_request");
  }

  @Test
  void shouldReturnNotFoundForUnknownOpportunity() throws Exception {
    correct("nao-existe", "{\"availability\":\"open\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("not_found"));
    assertThat(
            count(
                "SELECT count(*) FROM opportunity_correction WHERE opportunity_id = ?",
                "nao-existe"))
        .isZero();
  }

  @Test
  void shouldRequireSessionAndCsrf() throws Exception {
    String id = newOpportunity();
    String path = "/api/opportunities/" + id + "/corrections";
    String body = "{\"availability\":\"closed\"}";

    mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(path)
                .session(session.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    var anonymous = TestSession.anonymous(mvc);
    mvc.perform(
            post(path)
                .session(anonymous.session())
                .header("X-CSRF-Token", anonymous.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized());
    assertThat(corrections(id)).isZero();
    assertThat(logs(id)).isZero();
  }

  @Test
  void shouldReplaySameIdempotencyKeyAndRejectDifferentPayload() throws Exception {
    String id = newOpportunity();
    String key = Ids.newId();
    String body = "{\"workMode\":\"hybrid\",\"reason\":\"Confirmado\"}";

    var first =
        mvc.perform(
                post("/api/opportunities/" + id + "/corrections")
                    .session(session.session())
                    .header("X-CSRF-Token", session.csrf())
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();
    var replay =
        mvc.perform(
                post("/api/opportunities/" + id + "/corrections")
                    .session(session.session())
                    .header("X-CSRF-Token", session.csrf())
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(replay.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
    assertThat(replay.getResponse().getContentAsString())
        .isEqualTo(first.getResponse().getContentAsString());
    assertThat((String) JsonPath.read(replay.getResponse().getContentAsString(), "$.workMode"))
        .isEqualTo("hybrid");
    assertThat(corrections(id)).isEqualTo(1);
    assertThat(logs(id)).isEqualTo(1);

    mvc.perform(
            post("/api/opportunities/" + id + "/corrections")
                .session(session.session())
                .header("X-CSRF-Token", session.csrf())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workMode\":\"onsite\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("idempotency_key_reused"));
    assertThat(corrections(id)).isEqualTo(1);
    assertThat(logs(id)).isEqualTo(1);
  }

  @Test
  void shouldReassessCompatibilityWhenTitleOrLocationChangeKeepingOriginalEvidence()
      throws Exception {
    String id = newOpportunity();
    var evidenceBefore = originalEvidence(id);
    mvc.perform(get("/api/opportunities/" + id).session(session.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.compatibility.current").value(true))
        .andExpect(jsonPath("$.compatibility.assessedAt").value("2026-11-14T15:00:00Z"));
    long revisionBefore = count("SELECT evidence_revision FROM opportunity WHERE id = ?", id);

    clock.setClock(Clock.fixed(Instant.parse("2026-11-15T09:30:00Z"), AppClock.ZONE));
    correct(id, "{\"title\":\"Software Engineer Java\",\"location\":\"Porto Alegre, RS\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Software Engineer Java"))
        .andExpect(jsonPath("$.compatibility.current").value(true))
        .andExpect(jsonPath("$.compatibility.assessedAt").value("2026-11-15T09:30:00Z"));

    assertThat(count("SELECT evidence_revision FROM opportunity WHERE id = ?", id))
        .isGreaterThan(revisionBefore);
    assertThat(originalEvidence(id)).isEqualTo(evidenceBefore);
    assertThat(
            count(
                "SELECT evidence_revision FROM opportunity_compatibility WHERE opportunity_id = ?",
                id))
        .isEqualTo(count("SELECT evidence_revision FROM opportunity WHERE id = ?", id));

    // Só datas não invalidam a avaliação.
    long revisionAfterTitle = count("SELECT evidence_revision FROM opportunity WHERE id = ?", id);
    correct(id, "{\"deadlineAt\":\"2026-12-01\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.compatibility.current").value(true))
        .andExpect(jsonPath("$.compatibility.assessedAt").value("2026-11-15T09:30:00Z"));
    assertThat(count("SELECT evidence_revision FROM opportunity WHERE id = ?", id))
        .isEqualTo(revisionAfterTitle);
  }

  // ---------------------------------------------------------------------------------------------
  // Etapa 1 — revisão: precedência da correção, reavaliação, vaga fundida e bordas.
  // ---------------------------------------------------------------------------------------------

  private JobSource.Item item(
      String key, String title, String location, String text, LocalDate publishedAt) {
    return new JobSource.Item(
        "lever:ciandt",
        key,
        "https://jobs.lever.co/ciandt/" + key,
        text,
        title,
        location,
        publishedAt,
        Completeness.complete,
        null);
  }

  private String uniqueKey() {
    return "review-" + SEQUENCE.incrementAndGet();
  }

  private String roleStatus(String id) throws Exception {
    return JsonPath.read(
        mvc.perform(get("/api/opportunities/" + id).session(session.session()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$.compatibility.roleStatus");
  }

  private Map<String, Object> row(String id) {
    return jdbc.sql("SELECT * FROM opportunity WHERE id = ?").param(id).query().singleRow();
  }

  @Test
  void shouldAssessCorrectedTitleAsUserCorrectionKeepingStoredEvidenceIntact() throws Exception {
    String id =
        ingestion.ingest(
            item(
                uniqueKey(),
                "Especialista em Vendas",
                "Remoto",
                "Requisitos: Java e Spring Boot\nTrabalho remoto",
                clock.localDate()),
            "CI&T");
    var evidenceBefore = originalEvidence(id);
    assertThat(roleStatus(id)).isEqualTo("mismatched");

    clock.setClock(Clock.fixed(Instant.parse("2026-11-16T09:30:00Z"), AppClock.ZONE));
    correct(id, "{\"title\":\"Desenvolvedor Backend Java\",\"reason\":\"Título real\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.compatibility.current").value(true))
        .andExpect(jsonPath("$.compatibility.roleStatus").value("matched"))
        .andExpect(jsonPath("$.compatibility.technologyStatus").value("matched"))
        .andExpect(
            jsonPath("$.compatibilityEvidence[?(@.source=='user_correction')].quote")
                .value(org.hamcrest.Matchers.hasItem("Desenvolvedor Backend Java")))
        .andExpect(
            jsonPath("$.compatibilityEvidence[?(@.source=='user_correction')].fetchedAt")
                .value(org.hamcrest.Matchers.hasItem("2026-11-16T09:30:00Z")))
        .andExpect(
            jsonPath("$.compatibilityEvidence[?(@.source=='user_correction')].sourceUrl")
                .value(org.hamcrest.Matchers.hasItem((Object) null)));

    assertThat(originalEvidence(id)).isEqualTo(evidenceBefore);
    assertThat(originalEvidence(id).getFirst()).containsEntry("title", "Especialista em Vendas");
    assertThat(
            count(
                "SELECT count(*) FROM opportunity_evidence WHERE opportunity_id = ?"
                    + " AND id LIKE 'user-correction-%'",
                id))
        .isZero();
  }

  @Test
  void shouldKeepDescriptionSignalsAsRoleConflictAgainstCorrectedTitle() throws Exception {
    String id =
        ingestion.ingest(
            item(
                uniqueKey(),
                "Especialista em Vendas",
                "Remoto",
                "Responsabilidades: gerir campanhas de marketing\nRequisitos: Java e Spring Boot",
                clock.localDate()),
            "CI&T");

    correct(id, "{\"title\":\"Desenvolvedor Backend Java\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.compatibility.roleStatus").value("unknown"))
        .andExpect(
            jsonPath("$.compatibility.reasonCodes")
                .value(org.hamcrest.Matchers.hasItem("role_conflict")))
        .andExpect(jsonPath("$.compatibility.technologyStatus").value("matched"));
  }

  @Test
  void shouldKeepHumanCorrectionsOverNewCollectionAndStillRecordSourceData() throws Exception {
    String key = uniqueKey();
    LocalDate original = clock.localDate().minusDays(5);
    String id =
        ingestion.ingest(
            item(key, "Desenvolvedor Backend Pleno", "Remoto", "Requisitos: Java", original),
            "CI&T");
    publications.record(id, original);

    correct(
            id,
            "{\"title\":\"Software Engineer Java\",\"location\":\"Porto Alegre, RS\","
                + "\"workMode\":\"onsite\",\"publishedAt\":\"2026-10-01\"}")
        .andExpect(status().isOk());

    String newText = "Requisitos: Java e Kafka\nTrabalho remoto";
    ingestion.ingest(
        item(key, "Desenvolvedor Backend Senior", "Curitiba, PR", newText, clock.localDate()),
        "CI&T Brasil");
    publications.record(id, clock.localDate());

    var after = row(id);
    assertThat(after)
        .containsEntry("title", "Software Engineer Java")
        .containsEntry("location", "Porto Alegre, RS")
        .containsEntry("work_mode", "onsite")
        .containsEntry("published_at", "2026-10-01")
        .containsEntry("company_name", "CI&T Brasil");
    assertThat(after.get("requirements_facts").toString()).contains("Kafka");

    var evidence = originalEvidence(id);
    assertThat(evidence).hasSize(1);
    assertThat(evidence.getFirst())
        .containsEntry("title", "Desenvolvedor Backend Senior")
        .containsEntry("description_text", newText);
    assertThat(sources(id)).hasSize(1);
  }

  @Test
  void shouldUpdateUncorrectedFieldsNormallyOnNewCollection() throws Exception {
    String key = uniqueKey();
    String id =
        ingestion.ingest(
            item(key, "Desenvolvedor Backend Pleno", "Remoto", "Requisitos: Java", null), "CI&T");
    correct(id, "{\"contractType\":\"clt\"}").andExpect(status().isOk());

    ingestion.ingest(
        item(key, "Desenvolvedor Backend Senior", "Curitiba, PR", "Requisitos: Java", null),
        "CI&T");

    assertThat(row(id))
        .containsEntry("title", "Desenvolvedor Backend Senior")
        .containsEntry("location", "Curitiba, PR")
        .containsEntry("contract_type", "clt");
  }

  @Test
  void shouldRejectCorrectionOfMergedOpportunityWithoutWritingAnything() throws Exception {
    String merged = newOpportunity();
    String survivor = newOpportunity();
    jdbc.sql("UPDATE opportunity SET merged_into = ? WHERE id = ?")
        .params(survivor, merged)
        .update();
    var snapshot = row(merged);

    correct(merged, "{\"workMode\":\"hybrid\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("opportunity_merged"))
        .andExpect(
            jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString(survivor)));

    assertThat(corrections(merged)).isZero();
    assertThat(logs(merged)).isZero();
    assertThat(row(merged)).isEqualTo(snapshot);
  }

  @Test
  void shouldClearDateOnlyWithNullOrEmptyStringAndRejectOtherJsonShapes() throws Exception {
    String id = newOpportunity();
    correct(id, "{\"deadlineAt\":\"2026-11-30\"}").andExpect(status().isOk());
    correct(id, "{\"deadlineAt\":\"\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deadlineAt").doesNotExist());
    correct(id, "{\"deadlineAt\":\"2026-11-30\"}").andExpect(status().isOk());

    assertRejected(id, "{\"deadlineAt\":\"   \"}", "malformed_request");
    assertRejected(id, "{\"deadlineAt\":0}", "malformed_request");
    assertRejected(id, "{\"deadlineAt\":true}", "malformed_request");
    assertRejected(id, "{\"deadlineAt\":[2026,11,30]}", "malformed_request");
    assertRejected(id, "{\"deadlineAt\":{\"year\":2026}}", "malformed_request");
    assertRejected(id, "{\"deadlineAt\":\"2026-11-30T10:00:00Z\"}", "malformed_request");
    assertRejected(id, "{\"publishedAt\":\"2026-1-3\"}", "malformed_request");
    assertThat(row(id)).containsEntry("deadline_at", "2026-11-30");
  }

  @Test
  void shouldRejectClearingSalaryThatDoesNotExist() throws Exception {
    String id = newOpportunity();
    assertRejected(id, "{\"clearSalary\":true}", "invalid_correction");
  }

  @Test
  void shouldNotRecordSpuriousCorrectionForBlankLocationOrPaddedTextAlreadyStored()
      throws Exception {
    String id = newOpportunity();
    jdbc.sql("UPDATE opportunity SET location = '' WHERE id = ?").param(id).update();
    assertRejected(id, "{\"location\":\"\"}", "invalid_correction");
    assertRejected(id, "{\"location\":\"   \"}", "invalid_correction");

    jdbc.sql("UPDATE opportunity SET location = '  Porto Alegre, RS ' WHERE id = ?")
        .param(id)
        .update();
    assertRejected(id, "{\"location\":\"Porto Alegre, RS\"}", "invalid_correction");

    jdbc.sql(
            "UPDATE opportunity SET title = '  Desenvolvedor Backend Pleno ',"
                + " company_name = ' CI&T ' WHERE id = ?")
        .param(id)
        .update();
    assertRejected(
        id,
        "{\"title\":\"Desenvolvedor Backend Pleno\",\"companyName\":\"CI&T\"}",
        "invalid_correction");
  }

  @Test
  void shouldRejectSalaryBeyondNineIntegerAndTwoFractionDigits() throws Exception {
    String id = newOpportunity();
    assertRejected(
        id, "{\"salary\":{\"min\":1234567890,\"unit\":\"monthly\"}}", "validation_failed");
    assertRejected(id, "{\"salary\":{\"max\":1000.001,\"unit\":\"monthly\"}}", "validation_failed");

    correct(id, "{\"salary\":{\"min\":999999999.99,\"max\":999999999.99,\"unit\":\"yearly\"}}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.salary.unit").value("yearly"));
  }

  @Test
  void shouldApplyDomainRulesWhenServiceIsCalledOutsideHttp() {
    String id = newOpportunity();
    var salary =
        (java.util.function.Function<BigDecimal, OpportunityCorrectionService.Command>)
            amount ->
                new OpportunityCorrectionService.Command(
                    null,
                    null,
                    null,
                    null,
                    null,
                    new OpportunityCorrectionService.Salary(amount, null, "monthly"),
                    false,
                    null,
                    null,
                    null,
                    null);

    assertThatThrownBy(() -> correctionService.correct(id, null))
        .isInstanceOf(InvalidCorrectionException.class);
    assertThatThrownBy(
            () -> correctionService.correct(id, salary.apply(new BigDecimal("1234567890"))))
        .isInstanceOf(InvalidCorrectionException.class)
        .hasMessageContaining("dígitos");
    assertThatThrownBy(() -> correctionService.correct(id, salary.apply(new BigDecimal("10.123"))))
        .isInstanceOf(InvalidCorrectionException.class);
    assertThatThrownBy(() -> correctionService.correct(id, salary.apply(new BigDecimal("-1"))))
        .isInstanceOf(InvalidCorrectionException.class);
    assertThat(corrections(id)).isZero();

    assertThatThrownBy(() -> correctionService.correct("nao-existe", salary.apply(BigDecimal.TEN)))
        .isInstanceOf(OpportunityNotFoundException.class);

    String survivor = newOpportunity();
    jdbc.sql("UPDATE opportunity SET merged_into = ? WHERE id = ?").params(survivor, id).update();
    assertThatThrownBy(() -> correctionService.correct(id, salary.apply(BigDecimal.TEN)))
        .isInstanceOfSatisfying(
            OpportunityMergedException.class, e -> assertThat(e.survivorId()).isEqualTo(survivor));
    assertThat(corrections(id)).isZero();
  }

  @Test
  void shouldKeepCorrectionWhenReassessmentFailsAndReturnOk(CapturedOutput output)
      throws Exception {
    String id = newOpportunity();
    var assessor = Mockito.mock(AssessOpportunityService.class);
    Mockito.when(assessor.assessAndSave(id))
        .thenThrow(
            new IllegalStateException("falha", new IllegalArgumentException("segredo-interno")));
    var service =
        new OpportunityCorrectionService(correctionRepository, writer, clock, activity, assessor);

    service.correct(
        id,
        new OpportunityCorrectionService.Command(
            "Software Engineer Java", null, null, null, null, null, false, null, null, null, null));

    Mockito.verify(assessor).assessAndSave(id);
    assertThat(row(id)).containsEntry("title", "Software Engineer Java");
    assertThat(corrections(id)).isEqualTo(1);
    assertThat(logs(id)).isEqualTo(1);
    assertThat(output.getAll())
        .contains("java.lang.IllegalStateException")
        .contains("java.lang.IllegalArgumentException")
        .doesNotContain("segredo-interno");
  }

  @Test
  void shouldRollbackOpportunityAndActivityLogWhenAppendFailsAfterApply() {
    String id = newOpportunity();
    var snapshot = row(id);
    var failing =
        Mockito.mock(
            OpportunityCorrectionRepository.class,
            AdditionalAnswers.delegatesTo(correctionRepository));
    Mockito.doThrow(new IllegalStateException("append falhou"))
        .when(failing)
        .append(Mockito.anyList());
    var service = new OpportunityCorrectionService(failing, writer, clock, activity, assessor);

    assertThatThrownBy(() ->
            service.correct(
                id,
                new OpportunityCorrectionService.Command(
                    "Software Engineer Java",
                    null,
                    null,
                    "hybrid",
                    null,
                    null,
                    false,
                    null,
                    null,
                    null,
                    null)))
        .isInstanceOf(IllegalStateException.class);

    Mockito.verify(failing).apply(Mockito.eq(id), Mockito.any(), Mockito.any());
    assertThat(row(id)).isEqualTo(snapshot);
    assertThat(corrections(id)).isZero();
    assertThat(logs(id)).isZero();
  }

  // ---------------------------------------------------------------------------------------------
  // Contrato `correctedFields` (detalhe, lista, lote) e `seniority.status = corrected_title`.
  // ---------------------------------------------------------------------------------------------

  private String correctedFieldsJson(String id) throws Exception {
    return mvc.perform(get("/api/opportunities/" + id).session(session.session()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void shouldReturnDistinctCorrectedFieldsInBatchOmittingUncorrectedOpportunities()
      throws Exception {
    String twice = newOpportunity();
    String once = newOpportunity();
    String never = newOpportunity();
    correct(twice, "{\"title\":\"Dev Backend Java Alfa\",\"location\":\"Porto Alegre, RS\"}")
        .andExpect(status().isOk());
    correct(twice, "{\"title\":\"Dev Backend Java Beta\"}").andExpect(status().isOk());
    correct(once, "{\"workMode\":\"hybrid\"}").andExpect(status().isOk());

    assertThat(correctionRepository.correctedFieldsOf(List.of(twice, once, never, "inexistente")))
        .containsOnly(
            Map.entry(
                twice,
                Set.of(CorrectableOpportunityField.title, CorrectableOpportunityField.location)),
            Map.entry(once, Set.of(CorrectableOpportunityField.work_mode)));
    assertThat(correctionRepository.correctedFieldsOf(List.of())).isEmpty();
  }

  @Test
  void shouldExposeCorrectedFieldsInDetailAndEachListItemInStableFieldOrder() throws Exception {
    String corrected = newOpportunity();
    String untouched = newOpportunity();
    correct(corrected, "{\"location\":\"Canoas, RS\",\"workMode\":\"hybrid\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correctedFields[0]").value("location"));
    correct(corrected, "{\"title\":\"Dev Backend Java Gama\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correctedFields.length()").value(3))
        .andExpect(jsonPath("$.correctedFields[0]").value("title"))
        .andExpect(jsonPath("$.correctedFields[1]").value("location"))
        .andExpect(jsonPath("$.correctedFields[2]").value("work_mode"));

    String detail = correctedFieldsJson(corrected);
    assertThat(JsonPath.<List<String>>read(detail, "$.correctedFields"))
        .containsExactly("title", "location", "work_mode");
    assertThat(JsonPath.<List<String>>read(detail, "$.corrections[*].fieldName"))
        .containsExactlyInAnyOrder("title", "location", "work_mode");
    assertThat(JsonPath.<List<String>>read(correctedFieldsJson(untouched), "$.correctedFields"))
        .isEmpty();

    for (String id : List.of(corrected, untouched)) {
      mvc.perform(
              post("/api/opportunities/" + id + "/discard")
                  .session(session.session())
                  .header("X-CSRF-Token", session.csrf()))
          .andExpect(status().isOk());
    }
    String list =
        mvc.perform(get("/api/opportunities?list=discarded").session(session.session()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(
            JsonPath.<List<List<String>>>read(
                list, "$.items[?(@.id=='" + corrected + "')].correctedFields"))
        .containsExactly(List.of("title", "location", "work_mode"));
    assertThat(
            JsonPath.<List<List<String>>>read(
                list, "$.items[?(@.id=='" + untouched + "')].correctedFields"))
        .containsExactly(List.of());
  }

  @Test
  void shouldMarkSeniorityFromPersonCorrectedTitleAsCorrectedTitle() throws Exception {
    String id = newOpportunity();
    String before = correctedFieldsJson(id);
    assertThat(JsonPath.<String>read(before, "$.seniority.status")).isEqualTo("fact");

    correct(id, "{\"title\":\"Desenvolvedor Backend Sênior\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.seniority.status").value("corrected_title"))
        .andExpect(jsonPath("$.seniority.label").value("Sênior"))
        .andExpect(jsonPath("$.seniority.reason").value(org.hamcrest.Matchers.nullValue()));
    mvc.perform(get("/api/opportunities/" + id).session(session.session()))
        .andExpect(jsonPath("$.seniority.status").value("corrected_title"))
        .andExpect(jsonPath("$.seniority.label").value("Sênior"));
  }

  @Test
  void shouldKeepFactWhenCorrectedTitleOnlyRepeatsLevelOfOriginalTitle() throws Exception {
    String id = newOpportunity();

    correct(id, "{\"title\":\"Desenvolvedor Backend Pleno Java\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correctedFields[0]").value("title"))
        .andExpect(jsonPath("$.seniority.status").value("fact"))
        .andExpect(jsonPath("$.seniority.label").value("Pleno"));
  }

  @Test
  void shouldKeepFactWhenLevelComesFromOriginalEvidenceNotFromCorrectedTitle() throws Exception {
    String fromFacts =
        ingestion.ingest(
            item(
                uniqueKey(),
                "Desenvolvedor Backend Java",
                "Remoto",
                "Requisitos: Java e Spring Boot",
                clock.localDate()),
            "CI&T");
    jdbc.sql("UPDATE opportunity SET requirements_facts = ? WHERE id = ?")
        .params("[\"Nível Pleno informado\"]", fromFacts)
        .update();
    correct(fromFacts, "{\"title\":\"Desenvolvedor Backend Java Spring\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correctedFields[0]").value("title"))
        .andExpect(jsonPath("$.seniority.status").value("fact"))
        .andExpect(jsonPath("$.seniority.label").value("Pleno"));

    String otherField = newOpportunity();
    correct(otherField, "{\"location\":\"Canoas, RS\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correctedFields[0]").value("location"))
        .andExpect(jsonPath("$.seniority.status").value("fact"))
        .andExpect(jsonPath("$.seniority.label").value("Pleno"));
  }

  @Test
  void shouldKeepInferredWhenCorrectedTitleHasNoLevelAndDescriptionHasClue() throws Exception {
    String id =
        ingestion.ingest(
            item(
                uniqueKey(),
                "Especialista em Vendas",
                "Remoto",
                "Requisitos: Java e Spring Boot. Exigimos 3+ anos de experiência.",
                clock.localDate()),
            "CI&T");

    correct(id, "{\"title\":\"Desenvolvedor Backend Java\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.seniority.status").value("inferred"))
        .andExpect(jsonPath("$.seniority.label").value("Pleno"));
  }
}
