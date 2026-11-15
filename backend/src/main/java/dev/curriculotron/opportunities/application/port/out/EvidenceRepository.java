package dev.curriculotron.opportunities.application.port.out;

import dev.curriculotron.opportunities.domain.VacancyEvidence;
import java.util.List;

public interface EvidenceRepository {
  List<VacancyEvidence> forOpportunity(String opportunityId);

  long revision(String opportunityId);

  long upsert(VacancyEvidence evidence);

  void invalidate(String opportunityId);
}
