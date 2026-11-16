package dev.curriculotron.opportunities.application;

import dev.curriculotron.opportunities.application.port.out.DedupeRepository;
import dev.curriculotron.opportunities.application.port.out.EvidenceRepository;
import dev.curriculotron.opportunities.domain.CanonicalUrl;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class DedupeService {
  public record IngestResult(String opportunityId, boolean sourceConflict) {}

  private final DedupeRepository repository;
  private final EvidenceRepository evidence;
  private final WriteExecutor writer;
  private final AppClock clock;

  public DedupeService(
      DedupeRepository repository,
      EvidenceRepository evidence,
      WriteExecutor writer,
      AppClock clock) {
    this.repository = repository;
    this.evidence = evidence;
    this.writer = writer;
    this.clock = clock;
  }

  /**
   * The factory persists a new vacancy inside this transaction only when no strong identity exists.
   */
  public String ingest(
      String source, String externalId, String url, String excerpt, Supplier<String> create) {
    return ingestResult(source, externalId, url, excerpt, create).opportunityId();
  }

  public IngestResult ingestResult(
      String source, String externalId, String url, String excerpt, Supplier<String> create) {
    if (source == null || source.isBlank())
      throw new IllegalArgumentException("Fonte obrigatória.");
    String canonical = CanonicalUrl.normalize(url);
    String external = externalId == null || externalId.isBlank() ? null : externalId;
    return writer.write(() -> {
      var byExternal = repository.byExternalId(source, external);
      var byUrl = repository.byUrl(canonical);
      if (byExternal.isPresent() && byUrl.isPresent() && !byExternal.get().equals(byUrl.get())) {
        repository.suggest(byExternal.get(), byUrl.get(), clock.now());
        return new IngestResult(byExternal.get(), true);
      }
      var matched = byExternal.or(() -> byUrl);
      boolean existed = matched.isPresent();
      String id = matched.orElseGet(create);
      boolean attachedNew =
          repository.attach(id, source, external, canonical, excerpt, clock.now());
      if (existed && attachedNew) {
        evidence.invalidate(id);
      }
      for (String similar : repository.similar(id)) repository.suggest(id, similar, clock.now());
      return new IngestResult(id, false);
    });
  }
}
