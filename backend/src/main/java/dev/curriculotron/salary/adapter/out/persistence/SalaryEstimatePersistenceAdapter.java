package dev.curriculotron.salary.adapter.out.persistence;

import dev.curriculotron.salary.application.port.out.SalaryEstimateRepository;
import dev.curriculotron.salary.domain.SalaryEstimate;
import dev.curriculotron.salary.domain.SalaryProfile;
import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class SalaryEstimatePersistenceAdapter implements SalaryEstimateRepository {
  private final SalaryEstimateJpaRepository estimates;
  private final OpportunitySalaryLinkJpaRepository links;
  private final SalaryLookupAttemptJpaRepository attempts;
  private final SalaryRefreshRequestJpaRepository refreshRequests;
  private final EntityManager entityManager;

  public SalaryEstimatePersistenceAdapter(
      SalaryEstimateJpaRepository estimates,
      OpportunitySalaryLinkJpaRepository links,
      SalaryLookupAttemptJpaRepository attempts,
      SalaryRefreshRequestJpaRepository refreshRequests,
      EntityManager entityManager) {
    this.estimates = estimates;
    this.links = links;
    this.attempts = attempts;
    this.refreshRequests = refreshRequests;
    this.entityManager = entityManager;
  }

  @Override
  public Optional<SalaryEstimate> latest(SalaryProfile profile) {
    return estimates.findByProfileKey(profile.key()).stream()
        .max(
            Comparator.comparing(SalaryEstimateEntity::consultedInstant)
                .thenComparing(SalaryEstimateEntity::id))
        .map(SalaryEstimateEntity::toDomain);
  }

  @Override
  public List<SalaryEstimate> all() {
    return estimates.findAll().stream()
        .sorted(
            Comparator.comparing(SalaryEstimateEntity::consultedInstant)
                .thenComparing(SalaryEstimateEntity::id)
                .reversed())
        .map(SalaryEstimateEntity::toDomain)
        .toList();
  }

  @Override
  public void append(SalaryEstimate estimate) {
    entityManager.persist(new SalaryEstimateEntity(estimate));
    entityManager.flush();
  }

  @Override
  public void link(String opportunityId, SalaryEstimate estimate, String at) {
    List<OpportunitySalaryLinkEntity> current =
        links.findByOpportunityIdAndSupersededAtIsNull(opportunityId);
    if (current.stream().anyMatch(link -> estimate.id().equals(link.estimateId()))) return;
    links.supersedeCurrent(opportunityId, at);
    links.save(
        new OpportunitySalaryLinkEntity(
            UUID.randomUUID().toString(), opportunityId, estimate.id(), at));
  }

  public void startAttempt(String id, String profileKey, String taskRunId, String at) {
    attempts.save(new SalaryLookupAttemptEntity(id, profileKey, taskRunId, at));
  }

  public void completeAttempt(String id, String state, String at, String estimateId) {
    if (attempts.complete(id, state, at, estimateId) != 1) {
      throw new IllegalStateException("Tentativa salarial não encontrada.");
    }
  }

  public void interruptRunningAttempts(String at) {
    attempts.interruptRunning(at);
  }

  public boolean attempted(String task, String key) {
    return attempts.existsByTaskRunIdAndProfileKey(task, key);
  }

  public Optional<String> refreshTask(String opportunity, String key) {
    return refreshRequests
        .findByOpportunityIdAndRequestKey(opportunity, key)
        .map(SalaryRefreshRequestEntity::taskRunId);
  }

  public void rememberRefresh(String opportunity, String key, String task) {
    refreshRequests.save(new SalaryRefreshRequestEntity(opportunity, key, task));
  }
}
