package dev.curriculotron.opportunities.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpportunityEntityLifecycleTest {
  @Test
  void incrementsEvidenceRevisionOnlyOnRelevantChanges() {
    var entity = OpportunityEntity.manual("o1", "Java remoto", "Backend", "2026-11-14T10:00:00Z");
    assertThat(entity.evidenceRevision()).isEqualTo(1);

    // No-op refresh does not bump revision.
    entity.refreshDetails(
        "Backend",
        "Empresa a confirmar",
        null,
        "unknown",
        List.of("Java remoto"),
        "2026-11-14T10:01:00Z");
    assertThat(entity.evidenceRevision()).isEqualTo(1);

    // Updating company, location, workMode, or facts bumps revision once.
    entity.refreshDetails(
        "Backend",
        "CI&T",
        "Remoto",
        "remote",
        List.of("Menciona Java", "Modalidade informada: remote"),
        "2026-11-14T10:02:00Z");
    assertThat(entity.evidenceRevision()).isEqualTo(2);

    // Compensation change bumps revision; identical repeat does not.
    entity.updateCompensation(
        "clt", new BigDecimal("8000"), new BigDecimal("10000"), "monthly", "2026-11-14T10:03:00Z");
    assertThat(entity.evidenceRevision()).isEqualTo(3);
    entity.updateCompensation(
        "clt", new BigDecimal("8000"), new BigDecimal("10000"), "monthly", "2026-11-14T10:04:00Z");
    assertThat(entity.evidenceRevision()).isEqualTo(3);

    // Availability change bumps revision; identical repeat does not.
    entity.updateAvailability("open", "2026-11-14T10:05:00Z");
    assertThat(entity.evidenceRevision()).isEqualTo(4);
    entity.updateAvailability("open", "2026-11-14T10:06:00Z");
    assertThat(entity.evidenceRevision()).isEqualTo(4);

    // Discarding does not bump revision; restoring from discarded does.
    entity.setDecision("discarded", "2026-11-14T10:07:00Z");
    assertThat(entity.evidenceRevision()).isEqualTo(4);
    entity.setDecision("none", "2026-11-14T10:08:00Z");
    assertThat(entity.evidenceRevision()).isEqualTo(5);
  }
}
