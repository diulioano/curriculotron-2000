package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;

import dev.curriculotron.opportunities.application.DailySelectionService;
import dev.curriculotron.opportunities.application.OpportunityService;
import dev.curriculotron.opportunities.application.port.out.CompatibilityRepository;
import dev.curriculotron.opportunities.application.port.out.SelectionRepository;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Axis;
import dev.curriculotron.opportunities.domain.RuleBasedRanker;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.sources.SearchCriteriaRepository;
import dev.curriculotron.support.AppTestBase;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DailySelectionServiceTest extends AppTestBase {
  @Autowired DailySelectionService service;
  @Autowired OpportunityService opportunities;
  @Autowired SelectionRepository selectionRepository;
  @Autowired CompatibilityRepository compatibilityRepository;
  @Autowired SearchCriteriaRepository criteria;
  @Autowired WriteExecutor writer;
  @Autowired EntityManager em;
  @Autowired AppClock clock;

  @BeforeEach
  void reset() {
    clock.setClock(Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), AppClock.ZONE));
    writer.run(() -> {
      em.createNativeQuery("DELETE FROM daily_selection").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_compatibility").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_evidence").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_source").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity").executeUpdate();
    });
  }

  @Test
  void selectsTenAndKeepsFiveSecondaryAcrossRepeatedSearches() {
    seed(15, "strong", 80, "Empresa", "none", "open", null, Axis.matched, Axis.matched);
    assertThat(service.selectToday()).hasSize(10);
    assertThat(service.selectToday()).hasSize(10);
    assertThat(service.secondary()).hasSize(5);
  }

  @Test
  void doesNotFillWithWeakOrPreviouslySelectedOrDecidedJobs() {
    seed(3, "strong", 80, "Empresa", "none", "open", null, Axis.matched, Axis.matched);
    seed(7, "weak", 0, "Empresa", "none", "open", null, Axis.unknown, Axis.unknown);
    seed(2, "strong", 80, "Empresa", "discarded", "open", null, Axis.matched, Axis.matched);
    seed(2, "strong", 80, "Empresa", "applied", "open", null, Axis.matched, Axis.matched);
    assertThat(service.selectToday()).hasSize(3);
    clock.setClock(Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), AppClock.ZONE));
    assertThat(service.selectToday()).isEmpty();
    assertThat(service.secondary()).hasSize(7);
  }

  @Test
  void concurrentSearchesCannotExceedDailyLimit() throws Exception {
    seed(25, "strong", 80, "Empresa", "none", "open", null, Axis.matched, Axis.matched);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = pool.submit(service::selectToday);
      var second = pool.submit(service::selectToday);
      assertThat(first.get()).hasSize(10);
      assertThat(second.get()).hasSize(10);
    }
    assertThat(service.secondary()).hasSize(15);
  }

  @Test
  void excludesMismatchedStaleUnassessedClosedAndMergedEvenWithPriorityCompanyOrHighScore() {
    seed(2, "strong", 100, "CWI", "none", "open", null, Axis.mismatched, Axis.matched);
    seed(2, "strong", 100, "CI&T", "none", "open", null, Axis.matched, Axis.mismatched);
    seedUnassessed(2, "strong", 95, "PicPay");
    String staleCriteria =
        seed(1, "strong", 95, "CWI", "none", "open", null, Axis.matched, Axis.matched).getFirst();
    String staleEvidence =
        seed(1, "strong", 95, "CWI", "none", "open", null, Axis.matched, Axis.matched).getFirst();
    String survivor =
        seed(1, "strong", 95, "CWI", "none", "closed", null, Axis.matched, Axis.matched).getFirst();
    seed(1, "strong", 95, "CWI", "none", "open", survivor, Axis.matched, Axis.matched);
    writer.run(() -> {
      em.createNativeQuery(
              "UPDATE opportunity_compatibility SET criteria_version = criteria_version + 99 WHERE opportunity_id = :id")
          .setParameter("id", staleCriteria)
          .executeUpdate();
      em.createNativeQuery(
              "UPDATE opportunity SET evidence_revision = evidence_revision + 1 WHERE id = :id")
          .setParameter("id", staleEvidence)
          .executeUpdate();
    });

    assertThat(service.selectToday()).isEmpty();
    assertThat(service.secondary()).isEmpty();
  }

  @Test
  void allowsUnrestrictedTechnologyInSelectionAndKeepsUnknownOnlyInSecondary() {
    String unrestricted = seedUnrestricted("strong", 80, "Empresa");
    String unknownRole =
        seed(1, "weak", 0, "CI&T", "none", "open", null, Axis.unknown, Axis.matched).getFirst();
    String unknownTech =
        seed(1, "weak", 0, "CWI", "none", "open", null, Axis.matched, Axis.unknown).getFirst();

    assertThat(service.selectToday()).containsExactly(unrestricted);
    assertThat(service.secondary()).containsExactlyInAnyOrder(unknownRole, unknownTech);
  }

  @Test
  void preservesRawSelectedHistoryAndDailyLimitWhenSelectedJobsBecomeStaleOrMismatched() {
    List<String> initial =
        seed(10, "strong", 80, "Empresa", "none", "open", null, Axis.matched, Axis.matched);
    assertThat(service.selectToday()).hasSize(10);

    // 4 selected items become stale/mismatched/closed/discarded; 5 new compatible items arrive.
    writer.run(() -> {
      em.createNativeQuery(
              "UPDATE opportunity SET evidence_revision = evidence_revision + 1 WHERE id = :id")
          .setParameter("id", initial.get(0))
          .executeUpdate();
      em.createNativeQuery(
              "UPDATE opportunity_compatibility SET role_status = 'mismatched' WHERE opportunity_id = :id")
          .setParameter("id", initial.get(1))
          .executeUpdate();
      em.createNativeQuery("UPDATE opportunity SET availability = 'closed' WHERE id = :id")
          .setParameter("id", initial.get(2))
          .executeUpdate();
      em.createNativeQuery("UPDATE opportunity SET user_decision = 'discarded' WHERE id = :id")
          .setParameter("id", initial.get(3))
          .executeUpdate();
    });
    List<String> newer =
        seed(5, "strong", 90, "CWI", "none", "open", null, Axis.matched, Axis.matched);

    // Daily limit of 10 remains consumed without deleting historical selections.
    assertThat(service.selectToday()).hasSize(10);
    assertThat(selectionRepository.selected(clock.localDate()))
        .containsExactlyElementsOf(initial.stream().sorted().toList());
    assertThat(opportunities.list("selection", clock.localDate()))
        .extracting(o -> o.id())
        .containsExactlyInAnyOrderElementsOf(initial.subList(4, 10));
    assertThat(service.secondary()).containsExactlyInAnyOrderElementsOf(newer);
  }

  private List<String> seed(
      int count,
      String fit,
      int score,
      String company,
      String decision,
      String availability,
      String mergedInto,
      Axis roleStatus,
      Axis technologyStatus) {
    var settings = criteria.current();
    var snapshot =
        new CompatibilityAssessment.CriteriaSnapshot(
            settings.criteria(), settings.cltMinimum(), settings.pjMinimum());
    return writer.write(() -> {
      var ids = new java.util.ArrayList<String>(count);
      for (int i = 0; i < count; i++) {
        String id = UUID.randomUUID().toString();
        insertOpportunity(id, fit, score, company, decision, availability, mergedInto);
        compatibilityRepository.saveIfCurrent(
            new CompatibilityAssessment(
                id,
                settings.version(),
                CompatibilityAssessment.EVALUATOR_VERSION,
                1,
                clock.now(),
                roleStatus,
                technologyStatus,
                List.of(),
                snapshot,
                List.of(),
                fit,
                score,
                List.of("Critério avaliado")));
        ids.add(id);
      }
      return List.copyOf(ids);
    });
  }

  private String seedUnrestricted(String fit, int score, String company) {
    var settings = criteria.current();
    var emptyStackCriteria =
        new RuleBasedRanker.Criteria(
            settings.criteria().roles(),
            Set.of(),
            settings.criteria().hybridRegions(),
            settings.criteria().priorityCompanies(),
            settings.criteria().remotePreferred(),
            settings.criteria().acceptJunior());
    var snapshot =
        new CompatibilityAssessment.CriteriaSnapshot(
            emptyStackCriteria, settings.cltMinimum(), settings.pjMinimum());
    return writer.write(() -> {
      String id = UUID.randomUUID().toString();
      insertOpportunity(id, fit, score, company, "none", "open", null);
      compatibilityRepository.saveIfCurrent(
          new CompatibilityAssessment(
              id,
              settings.version(),
              CompatibilityAssessment.EVALUATOR_VERSION,
              1,
              clock.now(),
              Axis.matched,
              Axis.unrestricted,
              List.of("technology_unrestricted"),
              snapshot,
              List.of(),
              fit,
              score,
              List.of("Critérios sem restrição tecnológica.")));
      return id;
    });
  }

  private void seedUnassessed(int count, String fit, int score, String company) {
    writer.run(() -> {
      for (int i = 0; i < count; i++) {
        insertOpportunity(UUID.randomUUID().toString(), fit, score, company, "none", "open", null);
      }
    });
  }

  private void insertOpportunity(
      String id,
      String fit,
      int score,
      String company,
      String decision,
      String availability,
      String mergedInto) {
    em.createNativeQuery(
            """
            INSERT INTO opportunity(
              id,title,company_name,fit,fit_score,user_decision,availability,merged_into,created_at,updated_at)
            VALUES (:id,'Java pleno',:company,:fit,:score,:decision,:availability,:mergedInto,:now,:now)
            """)
        .setParameter("id", id)
        .setParameter("company", company)
        .setParameter("fit", fit)
        .setParameter("score", score)
        .setParameter("decision", decision)
        .setParameter("availability", availability)
        .setParameter("mergedInto", mergedInto)
        .setParameter("now", clock.now().toString())
        .executeUpdate();
  }
}
