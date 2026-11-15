package dev.curriculotron.opportunities.application.port.out;

import dev.curriculotron.opportunities.domain.VacancyEvidence;
import java.util.List;
import java.util.Optional;

public interface EvidenceRepository {
  Optional<String> sourceId(String source, String externalId, String canonicalUrl);

  List<VacancyEvidence> forOpportunity(String opportunityId);

  long revision(String opportunityId);

  long upsert(VacancyEvidence evidence);

  void invalidate(String opportunityId);
}
