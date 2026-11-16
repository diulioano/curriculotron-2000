package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;

import dev.curriculotron.opportunities.application.port.out.CompatibilityRepository;
import dev.curriculotron.opportunities.application.port.out.EvidenceRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityRepository;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Axis;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.EvidenceItem;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Field;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.MentionKind;
import dev.curriculotron.opportunities.domain.RuleBasedRanker;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.support.AppTestBase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CompatibilityRepositoryTest extends AppTestBase {
  @Autowired OpportunityRepository jobs;
  @Autowired CompatibilityRepository assessments;
  @Autowired EvidenceRepository evidence;
  @Autowired WriteExecutor writer;

  @Test
  void shouldRoundtripTypedAssessmentAndRejectStaleEvidenceOrCriteria() {
    String id = writer.write(() -> jobs.createManual("Backend Java", Instant.EPOCH.toString()));
    var item =
        new VacancyEvidence(
            "manual-" + id,
            id,
            null,
            "manual",
            "Backend Java",
            null,
            "Requisitos: Java",
            VacancyEvidence.Completeness.unknown,
            null,
            1,
            Instant.EPOCH,
            null);
    long revision = writer.write(() -> evidence.upsert(item));
    assertThat(evidence.forOpportunity(id)).hasSize(1);
    assertThat(writer.write(() -> evidence.upsert(item))).isEqualTo(revision);
    var criteria =
        new RuleBasedRanker.Criteria(
            Set.of("Backend"), Set.of("Java"), Set.of(), Set.of(), true, true);
    var snapshot =
        new CompatibilityAssessment.CriteriaSnapshot(
            criteria, new BigDecimal("6000"), new BigDecimal("9000"));
    var proof =
        new EvidenceItem(
            item.id(),
            "manual",
            null,
            Instant.EPOCH,
            Field.description,
            "Requisitos",
            "Requisitos: Java",
            "Java",
            "Java",
            MentionKind.required,
            null,
            null);
    var value =
        new CompatibilityAssessment(
            id,
            1,
            1,
            revision,
            Instant.EPOCH,
            Axis.matched,
            Axis.matched,
            List.of("technology_match"),
            snapshot,
            List.of(proof),
            "strong",
            60,
            List.of("Java"));
    assertThat(writer.write(() -> assessments.saveIfCurrent(value))).isTrue();
    assertThat(assessments.find(id)).contains(value);
    assertThat(assessments.pending(1, 1, "", 100)).doesNotContain(id);
    writer.run(() -> evidence.invalidate(id));
    assertThat(writer.write(() -> assessments.saveIfCurrent(value))).isFalse();
    assertThat(assessments.pending(1, 1, "", 100)).contains(id);
    var wrongCriteria =
        new CompatibilityAssessment(
            id,
            2,
            1,
            evidence.revision(id),
            Instant.EPOCH,
            Axis.matched,
            Axis.matched,
            List.of(),
            snapshot,
            List.of(),
            "strong",
            60,
            List.of());
    assertThat(writer.write(() -> assessments.saveIfCurrent(wrongCriteria))).isFalse();
    assertThat(evidence.forOpportunity(id).getFirst().completeness())
        .isEqualTo(VacancyEvidence.Completeness.unknown);
  }
}
