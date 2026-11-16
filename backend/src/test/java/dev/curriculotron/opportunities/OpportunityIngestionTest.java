package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;

import dev.curriculotron.opportunities.application.OpportunityIngestionService;
import dev.curriculotron.opportunities.application.port.out.CompatibilityRepository;
import dev.curriculotron.opportunities.application.port.out.EvidenceRepository;
import dev.curriculotron.opportunities.application.port.out.MatchCandidateRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunitySourceRepository;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.sources.JobSource;
import dev.curriculotron.support.AppTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OpportunityIngestionTest extends AppTestBase {
  @Autowired OpportunityIngestionService ingestion;
  @Autowired OpportunityRepository jobs;
  @Autowired EvidenceRepository evidence;
  @Autowired CompatibilityRepository assessments;

  @Autowired MatchCandidateRepository candidates;
  @Autowired OpportunitySourceRepository sources;
  @Autowired AppClock clock;
  @Autowired WriteExecutor writer;

  @Test
  void shouldPreserveBothJobsAndConflictSuggestionWhenExternalIdAndUrlDisagree() {
    String a =
        ingestion.ingest(
            new JobSource.Item(
                "conflict",
                "a",
                "https://conflict.test/a",
                "Requirements: Java",
                "Backend Developer A",
                "Remote"),
            "X");
    String b =
        ingestion.ingest(
            new JobSource.Item(
                "conflict",
                "b",
                "https://conflict.test/b",
                "Requirements: Python",
                "Backend Developer B",
                "Remote"),
            "X");
    long aRevision = evidence.revision(a);
    long bRevision = evidence.revision(b);
    assertThat(
            ingestion.ingest(
                new JobSource.Item(
                    "conflict",
                    "a",
                    "https://conflict.test/b",
                    "Requirements: Rust",
                    "Conflicting title",
                    "Remote"),
                "X"))
        .isEqualTo(a);
    assertThat(evidence.revision(a)).isEqualTo(aRevision);
    assertThat(evidence.revision(b)).isEqualTo(bRevision);
    assertThat(evidence.forOpportunity(a).getFirst().text()).isEqualTo("Requirements: Java");
    assertThat(evidence.forOpportunity(b).getFirst().text()).isEqualTo("Requirements: Python");
    assertThat(jobs.find(a).orElseThrow().title()).isEqualTo("Backend Developer A");
    assertThat(candidates.open())
        .anyMatch(c ->
            java.util.Set.of(c.opportunityA(), c.opportunityB()).equals(java.util.Set.of(a, b)));
  }

  @Test
  void shouldKeepSourceEvidenceAndTitleWhenManualTextReusesBoardUrl() {
    String id =
        ingestion.ingest(
            new JobSource.Item(
                "board",
                "verified",
                "https://verified.test/job",
                "Requirements: Java",
                "Backend Developer",
                "Remote"),
            "X");

    assertThat(
            ingestion.ingest(
                new JobSource.Item(
                    "manual", null, "https://verified.test/job", "Vaga colada\nPython"),
                "Empresa a confirmar"))
        .isEqualTo(id);

    assertThat(jobs.find(id).orElseThrow().title()).isEqualTo("Backend Developer");
    var stored = evidence.forOpportunity(id);
    assertThat(stored).hasSize(2);
    assertThat(stored)
        .filteredOn(e -> e.sourceId() != null)
        .singleElement()
        .satisfies(e -> {
          assertThat(e.sourceKind()).isEqualTo("board");
          assertThat(e.text()).isEqualTo("Requirements: Java");
        });
    assertThat(stored)
        .filteredOn(e -> e.sourceId() == null)
        .singleElement()
        .satisfies(e -> assertThat(e.id()).isEqualTo("manual-" + id));
  }

  @Test
  void shouldReingestMergedVacancyIntoSurvivor() {
    String first = ingestion.ingest(mergeItem("merge-a", "Requirements: Java"), "Merge Co");
    String second = ingestion.ingest(mergeItem("merge-b", "Requirements: Java"), "Merge Co");
    var candidate =
        candidates.open().stream()
            .filter(c ->
                java.util.Set.of(c.opportunityA(), c.opportunityB())
                    .equals(java.util.Set.of(first, second)))
            .findFirst()
            .orElseThrow();
    String survivor = candidate.opportunityA();
    String merged = candidate.opportunityB();
    String mergedExternalId = merged.equals(first) ? "merge-a" : "merge-b";
    long survivorRevision = evidence.revision(survivor);

    assertThat(writer.write(() -> candidates.merge(survivor, merged, clock.now().toString())))
        .isTrue();
    assertThat(evidence.revision(survivor)).isGreaterThan(survivorRevision);
    assertThat(evidence.forOpportunity(survivor)).hasSize(2);

    assertThat(ingestion.ingest(mergeItem(mergedExternalId, "Requirements: Python"), "Merge Co"))
        .isEqualTo(survivor);
    assertThat(evidence.forOpportunity(survivor))
        .anyMatch(e -> "Requirements: Python".equals(e.text()));
    assertThat(evidence.forOpportunity(merged)).isEmpty();
  }

  private static JobSource.Item mergeItem(String externalId, String text) {
    return new JobSource.Item(
        "board",
        externalId,
        "https://merge.test/" + externalId,
        text,
        "Backend Developer Merge",
        "Remote");
  }

  @Test
  void shouldNotRefreshExternalConsultationTimeForManualUrlOnly() {
    try {
      clock.setClock(
          java.time.Clock.fixed(
              java.time.Instant.parse("2030-01-01T00:00:00Z"),
              dev.curriculotron.shared.AppClock.ZONE));
      String id =
          ingestion.ingest(
              new JobSource.Item(
                  "board",
                  "dated",
                  "https://dated.test/job",
                  "Requirements: Java",
                  "Backend Developer",
                  "Remote"),
              "X");
      String fetched = sources.forOpportunity(id).getFirst().fetchedAt();
      clock.setClock(
          java.time.Clock.fixed(
              java.time.Instant.parse("2031-01-01T00:00:00Z"),
              dev.curriculotron.shared.AppClock.ZONE));
      ingestion.ingest(
          new JobSource.Item("manual", null, "https://dated.test/job", null),
          "Empresa a confirmar");
      assertThat(sources.forOpportunity(id).getFirst().fetchedAt()).isEqualTo(fetched);
    } finally {
      clock.setClock(java.time.Clock.system(dev.curriculotron.shared.AppClock.ZONE));
    }
  }

  @Test
  void shouldUpdateEvidenceForExistingIdentityAndKeepManualWithoutUrl() {
    var first =
        new JobSource.Item(
            "test",
            "external",
            "https://example.test/job",
            "Requirements: Java",
            "Backend Developer",
            "Remote",
            null,
            VacancyEvidence.Completeness.complete,
            null);
    String id = ingestion.ingest(first, "X");
    long initialRevision = evidence.revision(id);
    assertThat(assessments.find(id).orElseThrow().technologyStatus().name()).isEqualTo("matched");
    var changed =
        new JobSource.Item(
            "test",
            "external",
            "https://example.test/job",
            "Requirements: Python",
            "Backend Developer",
            "Remote",
            null,
            VacancyEvidence.Completeness.complete,
            null);
    assertThat(ingestion.ingest(changed, "X")).isEqualTo(id);
    assertThat(evidence.revision(id)).isGreaterThan(initialRevision);
    assertThat(evidence.forOpportunity(id)).hasSize(1);
    assertThat(evidence.forOpportunity(id).getFirst().text()).isEqualTo("Requirements: Python");
    assertThat(
            ingestion.ingest(
                new JobSource.Item("manual", null, "https://example.test/job", null),
                "Empresa a confirmar"))
        .isEqualTo(id);
    assertThat(evidence.forOpportunity(id).getFirst().text()).isEqualTo("Requirements: Python");
    assertThat(assessments.find(id).orElseThrow().technologyStatus().name())
        .isEqualTo("mismatched");
    assertThat(jobs.find(id).orElseThrow().userDecision()).isEqualTo("none");
    String manual =
        ingestion.ingest(
            new JobSource.Item("manual", null, null, "Backend Developer Java"),
            "Empresa a confirmar");
    assertThat(evidence.forOpportunity(manual).getFirst().sourceId()).isNull();
    assertThat(assessments.find(manual)).isPresent();
  }
}
