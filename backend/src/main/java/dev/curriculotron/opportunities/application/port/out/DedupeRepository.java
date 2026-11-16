package dev.curriculotron.opportunities.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DedupeRepository {
  Optional<String> byExternalId(String source, String externalId);

  Optional<String> byUrl(String url);

  boolean attach(
      String opportunityId,
      String source,
      String externalId,
      String url,
      String excerpt,
      Instant at);

  List<String> similar(String opportunityId);

  void suggest(String a, String b, Instant at);
}
