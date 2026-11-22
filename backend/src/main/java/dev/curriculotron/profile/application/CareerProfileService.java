package dev.curriculotron.profile.application;

import dev.curriculotron.audit.application.ActivityLogService;
import dev.curriculotron.audit.domain.ActivityEntry;
import dev.curriculotron.opportunities.application.CompatibilityStatus;
import dev.curriculotron.opportunities.application.CompatibilityStatusService;
import dev.curriculotron.profile.application.port.out.CareerProfileRepository;
import dev.curriculotron.profile.domain.CareerProfile;
import dev.curriculotron.profile.domain.ProfileDraft;
import dev.curriculotron.profile.domain.ProfileMarkdownParser;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.sources.CriteriaService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfil de carreira (US006). Importação revisada e edição manual usam o mesmo {@link #save};
 * salvar alinha cargo e tecnologias nos critérios e marca a compatibilidade para reavaliação.
 */
@Service
public class CareerProfileService {
  private final CareerProfileRepository profiles;
  private final CriteriaService criteria;
  private final CompatibilityStatusService status;
  private final WriteExecutor writer;
  private final AppClock clock;
  private final ActivityLogService activity;

  public CareerProfileService(
      CareerProfileRepository profiles,
      CriteriaService criteria,
      CompatibilityStatusService status,
      WriteExecutor writer,
      AppClock clock,
      ActivityLogService activity) {
    this.profiles = profiles;
    this.criteria = criteria;
    this.status = status;
    this.writer = writer;
    this.clock = clock;
    this.activity = activity;
  }

  /** {@code profile} é nulo enquanto nada foi salvo. */
  public record View(
      CareerProfileRepository.Stored profile,
      CompatibilityStatus assessment,
      List<String> priorityCompanies) {}

  @Transactional(readOnly = true)
  public View view() {
    return new View(
        profiles.current().orElse(null), status.current(), criteria.priorityCompanies());
  }

  public ProfileDraft.Preview preview(String markdown) {
    return ProfileMarkdownParser.parse(markdown);
  }

  /**
   * @param expectedVersion versão atual conhecida pela pessoa; {@code 0} quando ainda não há perfil
   * @throws ProfileVersionConflictException se outra edição foi salva antes
   */
  public View save(long expectedVersion, CareerProfile requested) {
    return writer.write(() -> {
      var current = profiles.current().map(CareerProfileRepository.Stored::profile);
      long version = current.map(CareerProfile::version).orElse(0L);
      if (version != expectedVersion) throw new ProfileVersionConflictException();
      if (current.isEmpty() || !current.get().sameContent(requested)) {
        var updated = requested.withVersion(version + 1);
        profiles.save(updated, clock.now());
        criteria.alignWithProfile(
            List.of(updated.role()), updated.technologies(), updated.priorities());
        activity.record(ActivityEntry.user("profile.update", "career_profile", "1"));
      }
      return new View(
          profiles.current().orElse(null), status.current(), criteria.priorityCompanies());
    });
  }
}
