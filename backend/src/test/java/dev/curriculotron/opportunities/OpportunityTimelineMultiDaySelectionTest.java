package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.curriculotron.audit.application.ActivityLogService;
import dev.curriculotron.opportunities.application.OpportunityTimelineService;
import dev.curriculotron.opportunities.application.port.out.AppliedOpportunityRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityCorrectionRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunitySourceRepository;
import dev.curriculotron.opportunities.application.port.out.SelectionRepository;
import dev.curriculotron.opportunities.domain.OpportunityTimelineEvent;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpportunityTimelineMultiDaySelectionTest {
  @Test
  void shouldProjectEachSelectedDayInChronologicalOrder() {
    var selections = mock(SelectionRepository.class);
    var timeline =
        new OpportunityTimelineService(
            mock(OpportunitySourceRepository.class),
            selections,
            mock(OpportunityCorrectionRepository.class),
            mock(ActivityLogService.class),
            mock(AppliedOpportunityRepository.class));
    when(selections.selectedDays("vaga"))
        .thenReturn(List.of(LocalDate.parse("2026-11-16"), LocalDate.parse("2026-11-14")));

    assertThat(timeline.of("vaga"))
        .extracting(OpportunityTimelineEvent::id)
        .containsExactly("sel-vaga-2026-11-14", "sel-vaga-2026-11-16");
  }
}
