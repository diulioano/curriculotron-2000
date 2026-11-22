package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompatibilityMigrationTest {
  @TempDir Path temporary;

  @Test
  void shouldMigrateFreshDatabaseWithPendingAssessment() throws Exception {
    String url = "jdbc:sqlite:" + temporary.resolve("fresh.db");
    migrate(url, null);
    try (var connection = DriverManager.getConnection(url);
        var sql = connection.createStatement()) {
      try (var rows =
          sql.executeQuery(
              "select state, target_criteria_version, evaluator_version from compatibility_refresh where id=1")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).isEqualTo("pending");
        assertThat(rows.getInt(2)).isEqualTo(1);
        assertThat(rows.getInt(3))
            .isEqualTo(
                dev.curriculotron.opportunities.domain.CompatibilityAssessment.EVALUATOR_VERSION);
      }
    }
  }

  @Test
  void shouldPreserveLegacySelectionAndMarkExcerptAsUnknown() throws Exception {
    String url = "jdbc:sqlite:" + temporary.resolve("legacy.db");
    migrate(url, "12");
    try (var connection = DriverManager.getConnection(url);
        var sql = connection.createStatement()) {
      sql.executeUpdate(
          "insert into opportunity(id,title,company_name,created_at,updated_at) values('legacy','Backend Developer','X','2026-11-15T00:00:00Z','2026-11-15T00:00:00Z')");
      sql.executeUpdate(
          "insert into opportunity_source(id,opportunity_id,source,canonical_url,fetched_at,raw_excerpt,created_at,updated_at) values('source','legacy','test','https://example.test/job','2026-11-15T00:00:00Z','Requisitos: Java','2026-11-15T00:00:00Z','2026-11-15T00:00:00Z')");
      sql.executeUpdate("insert into daily_selection values('2026-11-15','legacy',1,'historic')");
    }
    migrate(url, null);
    try (var connection = DriverManager.getConnection(url);
        var sql = connection.createStatement()) {
      try (var rows =
          sql.executeQuery(
              "select description_text,description_completeness,completeness_reason,content_revision from opportunity_evidence where opportunity_id='legacy'")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).isEqualTo("Requisitos: Java");
        assertThat(rows.getString(2)).isEqualTo("unknown");
        assertThat(rows.getString(3)).isEqualTo("legacy_excerpt");
        assertThat(rows.getInt(4)).isEqualTo(1);
      }
      try (var rows =
          sql.executeQuery("select rank from daily_selection where opportunity_id='legacy'")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getInt(1)).isEqualTo(1);
      }
      assertThatThrownBy(() ->
              sql.executeUpdate(
                  "insert into compatibility_refresh(id,target_criteria_version,evaluator_version,state) values(2,1,1,'pending')"))
          .isInstanceOf(java.sql.SQLException.class);
      assertThatThrownBy(() ->
              sql.executeUpdate(
                  "update opportunity_evidence set description_completeness='invented'"))
          .isInstanceOf(java.sql.SQLException.class);
    }
  }

  @Test
  void replacesReadyLegacyEvaluatorWithPendingCurrentEvaluator() throws Exception {
    String url = "jdbc:sqlite:" + temporary.resolve("v16.db");
    migrate(url, "16");
    try (var connection = DriverManager.getConnection(url);
        var sql = connection.createStatement()) {
      sql.executeUpdate(
          "UPDATE compatibility_refresh SET evaluator_version=1, state='ready', assessed_count=3");
    }
    migrate(url, null);
    try (var connection = DriverManager.getConnection(url);
        var sql = connection.createStatement();
        var result =
            sql.executeQuery(
                "SELECT state,evaluator_version,assessed_count FROM compatibility_refresh WHERE id=1")) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString(1)).isEqualTo("pending");
      assertThat(result.getInt(2))
          .isEqualTo(
              dev.curriculotron.opportunities.domain.CompatibilityAssessment.EVALUATOR_VERSION);
      assertThat(result.getInt(3)).isZero();
    }
  }

  private void migrate(String url, String target) {
    var config = Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration");
    if (target != null) config.target(target);
    config.load().migrate();
  }
}
