package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;

import dev.curriculotron.opportunities.application.AssessOpportunityService;
import dev.curriculotron.opportunities.application.port.out.CompatibilityRepository;
import dev.curriculotron.opportunities.application.port.out.EvidenceRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityRepository;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.support.AppTestBase;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AssessOpportunityPersistenceTest extends AppTestBase {
  @Autowired AssessOpportunityService service;
  @Autowired OpportunityRepository jobs;
  @Autowired EvidenceRepository evidence;
  @Autowired CompatibilityRepository assessments;
  @Autowired WriteExecutor writer;

  @Test
  void shouldPublishAssessmentFromConsistentStoredEvidence() {
    String id =
        writer.write(() -> jobs.createManual("Backend Developer", Instant.EPOCH.toString()));
    writer.run(() ->
        evidence.upsert(
            new VacancyEvidence(
                "manual-" + id,
                id,
                null,
                "manual",
                "Backend Developer",
                null,
                "Requirements: Java. Remote",
                VacancyEvidence.Completeness.complete,
                null,
                1,
                Instant.EPOCH,
                null)));
    assertThat(service.assessAndSave(id)).isTrue();
    var result = assessments.find(id).orElseThrow();
    assertThat(result.current(1, evidence.revision(id))).isTrue();
    assertThat(result.confirmed()).isTrue();
    assertThat(result.fit()).isEqualTo("strong");
    assertThat(jobs.find(id).orElseThrow().userDecision()).isEqualTo("none");
  }
}
