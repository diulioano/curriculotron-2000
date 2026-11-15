package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.opportunities.application.port.out.CompatibilityRepository;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Axis;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class CompatibilityPersistenceAdapter implements CompatibilityRepository {
  private final CompatibilityJpaRepository assessments;

  CompatibilityPersistenceAdapter(CompatibilityJpaRepository assessments) {
    this.assessments = assessments;
  }

  public Optional<CompatibilityAssessment> find(String id) {
    return assessments
        .findById(id)
        .map(a ->
            new CompatibilityAssessment(
                a.opportunityId,
                a.criteriaVersion,
                a.evaluatorVersion,
                a.evidenceRevision,
                Instant.parse(a.assessedAt),
                Axis.valueOf(a.roleStatus),
                Axis.valueOf(a.technologyStatus),
                CompatibilityJson.strings(a.reasonCodes),
                CompatibilityJson.criteria(a.criteriaSnapshot),
                CompatibilityJson.evidence(a.evidenceItems),
                a.fit,
                a.fitScore,
                CompatibilityJson.strings(a.fitReasons)));
  }

  public boolean saveIfCurrent(CompatibilityAssessment value) {
    if (value.evaluatorVersion() != CompatibilityAssessment.EVALUATOR_VERSION) {
      return false;
    }
    if (assessments.currentInput(
            value.opportunityId(), value.evidenceRevision(), value.criteriaVersion())
        != 1) return false;
    var a = new OpportunityCompatibilityEntity();
    a.opportunityId = value.opportunityId();
    a.criteriaVersion = value.criteriaVersion();
    a.evaluatorVersion = value.evaluatorVersion();
    a.evidenceRevision = value.evidenceRevision();
    a.assessedAt = value.assessedAt().toString();
    a.roleStatus = value.roleStatus().name();
    a.technologyStatus = value.technologyStatus().name();
    a.reasonCodes = CompatibilityJson.write(value.reasonCodes());
    a.criteriaSnapshot = CompatibilityJson.write(value.criteriaSnapshot());
    a.evidenceItems = CompatibilityJson.write(value.evidenceItems());
    a.fit = value.fit();
    a.fitScore = value.fitScore();
    a.fitReasons = CompatibilityJson.write(value.fitReasons());
    assessments.saveAndFlush(a);
    assessments.updateFit(value.opportunityId(), value.fit(), value.fitScore(), value.fitReasons());
    return true;
  }

  public List<String> pending(long version, int evaluator, String after, int limit) {
    return assessments.pending(version, evaluator, after, PageRequest.of(0, limit));
  }

  public long pendingCount(long version, int evaluator) {
    return assessments.pendingCount(version, evaluator);
  }
}
