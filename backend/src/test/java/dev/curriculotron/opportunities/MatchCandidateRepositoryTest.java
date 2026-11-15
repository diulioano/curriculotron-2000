package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.*;

import dev.curriculotron.opportunities.application.*;
import dev.curriculotron.opportunities.application.port.out.*;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.support.AppTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MatchCandidateRepositoryTest extends AppTestBase {
  @Autowired OpportunityService jobs;
  @Autowired MatchCandidateRepository candidates;
  @Autowired OpportunitySourceRepository sources;
  @Autowired WriteExecutor writer;

  @Test
  void explicitMergePreservesDiscardAndAllSources() {
    var a = jobs.manual("https://one.test/job", "Java remoto");
    var b = jobs.manual("https://two.test/job", "Java remoto");
    jobs.decide(b.id(), "discarded");
    var candidate = candidates.open().getFirst();
    assertThat(
            writer.write(() ->
                candidates.merge(
                    candidate.opportunityA(), candidate.opportunityB(), "2026-11-14T12:00:00Z")))
        .isTrue();
    assertThat(jobs.get(candidate.opportunityA()).userDecision()).isEqualTo("discarded");
    assertThat(jobs.get(candidate.opportunityB()).mergedInto()).isEqualTo(candidate.opportunityA());
    assertThat(sources.forOpportunity(candidate.opportunityA())).hasSize(2);
    assertThat(candidates.open()).isEmpty();
    assertThat(jobs.manual("https://two.test/job", "repeated").id())
        .isEqualTo(candidate.opportunityA());
    assertThat(jobs.list("discarded", java.time.LocalDate.now())).hasSize(1);
  }
}
