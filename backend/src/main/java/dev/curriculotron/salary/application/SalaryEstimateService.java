package dev.curriculotron.salary.application;

import dev.curriculotron.audit.application.*;
import dev.curriculotron.audit.domain.*;
import dev.curriculotron.opportunities.application.port.out.OpportunityRepository;
import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.salary.adapter.out.persistence.SalaryEstimatePersistenceAdapter;
import dev.curriculotron.salary.application.port.out.SalaryReferenceSource;
import dev.curriculotron.salary.domain.*;
import dev.curriculotron.shared.*;
import dev.curriculotron.shared.persistence.WriteExecutor;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class SalaryEstimateService implements AutoCloseable {
  private final java.util.concurrent.ExecutorService lookups =
      java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
  private final SalaryEstimatePersistenceAdapter store;
  private final WriteExecutor writer;
  private final AppClock clock;
  private final SalaryReferenceSource source;
  private final ActivityLogService activity;
  private final IntegrationStatusService integrations;
  private final OpportunityRepository jobs;
  private final SalaryLookupCoordinator coordinator;

  public SalaryEstimateService(
      SalaryEstimatePersistenceAdapter store,
      WriteExecutor writer,
      AppClock clock,
      SalaryReferenceSource source,
      ActivityLogService activity,
      IntegrationStatusService integrations,
      OpportunityRepository jobs,
      SalaryLookupCoordinator coordinator) {
    this.store = store;
    this.writer = writer;
    this.clock = clock;
    this.source = source;
    this.activity = activity;
    this.integrations = integrations;
    this.jobs = jobs;
    this.coordinator = coordinator;
  }

  public static SalaryProfile profile(Opportunity job) {
    String title = SalaryProfile.normalize(job.title());
    String seniority =
        title.matches(".*\\b(pleno|plena|pl)\\b.*")
            ? "pleno"
            : title.matches(".*\\b(sênior|senior|sr)\\b.*")
                ? "senior"
                : title.matches(".*\\b(júnior|junior|jr)\\b.*") ? "junior" : "";
    // Only canonicalize an explicit Java developer title; do not guess unrelated roles.
    String role =
        title.contains("java")
                && !title.contains("javascript")
                && title.matches(".*(desenvolv|developer|programador).*")
            ? "desenvolvedor java"
            : title
                .replaceAll("\\b(pleno|plena|sênior|senior|júnior|junior|pl|sr|jr)\\b", "")
                .trim();
    return new SalaryProfile(
        job.companyName(),
        role,
        seniority,
        job.workMode().equals("remote") ? "remote" : job.location(),
        job.workMode(),
        job.contractType());
  }

  private Optional<SalaryEstimate> find(SalaryProfile p) {
    var company = store.latest(p);
    return company.isPresent() ? company : store.latest(p.market());
  }

  public SalaryReference reference(Opportunity job) {
    if (job.salary() != null) return null;
    var p = profile(job);
    var found = p.complete() ? find(p) : Optional.<SalaryEstimate>empty();
    if (found.isEmpty())
      return new SalaryReference(
          "unavailable", p.complete() ? "not_found" : "insufficient_profile", null);
    var e = found.get();
    return new SalaryReference(
        SalaryNormalizer.monthly(e.originalMin(), e.originalUnit()) == null
            ? "non_monthly"
            : "available",
        null,
        view(e));
  }

  public SalaryObservation view(SalaryEstimate e) {
    return new SalaryObservation(
        e.id(),
        e.criteria(),
        e.currency(),
        e.originalMin(),
        e.originalMax(),
        e.originalUnit(),
        SalaryNormalizer.monthly(e.originalMin(), e.originalUnit()),
        SalaryNormalizer.monthly(e.originalMax(), e.originalUnit()),
        e.originalUnit().equals("yearly"),
        e.sampleSize(),
        e.source(),
        e.sourceUrl(),
        e.consultedAt(),
        e.expiresAt(),
        e.stale(clock.now()),
        e.sourceUpdatedOn(),
        e.criteria().company().isBlank() ? "market" : "company");
  }

  public List<SalaryObservation> observations() {
    return store.all().stream().map(this::view).toList();
  }

  public String glassdoorUrl(Opportunity job) {
    var p = profile(job);
    var found = find(p);
    if (found.isEmpty())
      found =
          store.latest(
              new SalaryProfile(
                  p.company(), p.role(), p.seniority(), "brasil", "unknown", "unknown"));
    return found.map(SalaryEstimate::sourceUrl).orElseGet(() -> GlassdoorLink.search(job.title()));
  }

  public void importEstimate(SalaryEstimate e) {
    if (e.consultedAt().isAfter(clock.now().plusSeconds(300)))
      throw new IllegalArgumentException("Data futura.");
    writer.run(() -> {
      store.append(e);
      if (e.criteria().complete()) {
        for (String id : jobs.activeIds())
          jobs.find(id)
              .ifPresent(job -> {
                var p = profile(job);
                if (job.salary() == null
                    && (p.equals(e.criteria()) || p.market().equals(e.criteria()))) {
                  // A market import must not displace an existing company-specific reference.
                  find(p).ifPresent(best -> store.link(id, best, clock.now().toString()));
                }
              });
      }
      activity.record(ActivityEntry.user("salary.import", "salary_estimate", e.id()));
    });
  }

  public void importEstimates(List<SalaryEstimate> estimates) {
    writer.run(() -> estimates.forEach(this::importEstimate));
  }

  public String enrich(Opportunity job, String task, boolean force) {
    if (job.salary() != null) return "salary_declared";
    var p = profile(job);
    if (!p.complete()) return "insufficient_profile";
    var claim = coordinator.reserve(p.key(), () -> task);
    if (claim.owner()) return enrichReserved(job, claim.lookup(), force);
    String result = claim.lookup().result().join();
    find(p).ifPresent(e -> writer.run(() -> store.link(job.id(), e, clock.now().toString())));
    return result;
  }

  public String enrichReserved(
      Opportunity job, SalaryLookupCoordinator.Lookup lookup, boolean force) {
    try {
      String result = lookup(job, lookup.task(), force);
      lookup.result().complete(result);
      return result;
    } catch (RuntimeException | Error error) {
      lookup.result().completeExceptionally(error);
      throw error;
    } finally {
      coordinator.release(profile(job).key(), lookup);
    }
  }

  private String lookup(Opportunity job, String task, boolean force) {
    if (job.salary() != null) return "salary_declared";
    var p = profile(job);
    if (!p.complete()) return "insufficient_profile";
    var previous = find(p);
    if (!force && previous.isPresent() && !previous.get().stale(clock.now())) {
      writer.run(() -> store.link(job.id(), previous.get(), clock.now().toString()));
      return "succeeded";
    }
    if (store.attempted(task, p.key())) {
      if (previous.isPresent())
        writer.run(() -> store.link(job.id(), previous.get(), clock.now().toString()));
      return "already_attempted";
    }
    String id = Ids.newId(), now = clock.now().toString();
    writer.run(() -> store.startAttempt(id, p.key(), task, now));
    SalaryReferenceSource.Result outcome;
    var pending = lookups.submit(() -> source.lookup(p));
    try {
      outcome = pending.get(30, java.util.concurrent.TimeUnit.SECONDS);
      if (outcome == null) throw new IllegalArgumentException("Resposta ausente.");
      if (outcome.estimate() != null) {
        var criteria = outcome.estimate().criteria();
        if (!"found".equals(outcome.state())
            || (!criteria.equals(p) && !criteria.equals(p.market())))
          outcome = new SalaryReferenceSource.Result("invalid", null);
        else outcome = new SalaryReferenceSource.Result("succeeded", outcome.estimate());
      } else if (!Set.of(
              "not_configured", "not_found", "invalid", "blocked", "unavailable", "provider_limit")
          .contains(outcome.state())) {
        outcome = new SalaryReferenceSource.Result("invalid", null);
      }
    } catch (Exception e) {
      pending.cancel(true);
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      outcome = new SalaryReferenceSource.Result("unavailable", null);
    }
    var answer = outcome;
    writer.run(() -> {
      if (answer.estimate() != null) {
        var e = answer.estimate();
        store.append(e);
        store.link(job.id(), e, clock.now().toString());
        integrations.recordSuccess("source:salary", "Referência salarial consultada.");
      } else {
        if (previous.isPresent()) store.link(job.id(), previous.get(), clock.now().toString());
        integrations.recordFailure(
            "source:salary",
            IntegrationState.degraded,
            "Consulta assistida pendente: " + answer.state());
      }
      store.completeAttempt(
          id,
          answer.state(),
          clock.now().toString(),
          answer.estimate() == null ? null : answer.estimate().id());
      activity.record(
          new ActivityEntry(
              task,
              "system",
              "salary.lookup",
              "opportunity",
              job.id(),
              null,
              answer.estimate() == null ? ActivityResult.skipped : ActivityResult.ok,
              answer.estimate() == null ? answer.state() : null));
    });
    return answer.state();
  }

  public void enrichIds(Set<String> ids, String task) {
    for (String id : ids) {
      try {
        var job = jobs.find(id);
        if (job.isPresent() && "provider_limit".equals(enrich(job.get(), task, false))) break;
      } catch (Exception e) {
        activity.record(
            ActivityEntry.system(
                "salary.lookup",
                "opportunity",
                id,
                ActivityResult.failed,
                "Não foi possível enriquecer a referência."));
      }
    }
  }

  public void close() {
    lookups.shutdownNow();
  }
}
