package dev.curriculotron.opportunities.application;

import dev.curriculotron.audit.application.ActivityLogService;
import dev.curriculotron.audit.domain.ActivityRecord;
import dev.curriculotron.opportunities.application.port.out.AppliedOpportunityRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityCorrectionRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunitySourceRepository;
import dev.curriculotron.opportunities.application.port.out.SelectionRepository;
import dev.curriculotron.opportunities.domain.CorrectableOpportunityField;
import dev.curriculotron.opportunities.domain.OpportunityCorrectionEntry;
import dev.curriculotron.opportunities.domain.OpportunityTimelineEvent;
import dev.curriculotron.opportunities.domain.OpportunityTimelineEvent.TimelineEventKind;
import dev.curriculotron.shared.AppClock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Linha do tempo da vaga, projetada em leitura a partir de fontes, seleção diária, correções e
 * registro de atividades, sem tabela própria. Ordem cronológica crescente; em empate de instante,
 * vale a ordem do ciclo de vida ({@link TimelineEventKind}) e, por fim, o id.
 *
 * <p>A correção aparece uma vez por operação, vinda de {@code opportunity_correction}; o evento
 * {@code opportunity.corrected} do {@code activity_log} é ignorado para não duplicá-la. A
 * candidatura vem da tabela {@code application} (o {@code activity_log} a registra sob o alvo
 * {@code application}, não sob a vaga).
 */
@Service
public class OpportunityTimelineService {
  static final String USER_LABEL = "você";
  static final String SYSTEM_LABEL = "sistema";
  static final String BOT_LABEL = "bot Busca de vagas";
  static final String MANUAL_LABEL = "entrada manual";
  static final String MANUAL_SOURCE = "manual";
  static final String EXTERNAL_OPENED_NOTE = "isso não registra candidatura";

  private static final String TARGET_TYPE = "opportunity";
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final Map<CorrectableOpportunityField, String> FIELD_LABELS = fieldLabels();

  private final OpportunitySourceRepository sources;
  private final SelectionRepository selections;
  private final OpportunityCorrectionRepository corrections;
  private final ActivityLogService activity;
  private final AppliedOpportunityRepository applications;

  public OpportunityTimelineService(
      OpportunitySourceRepository sources,
      SelectionRepository selections,
      OpportunityCorrectionRepository corrections,
      ActivityLogService activity,
      AppliedOpportunityRepository applications) {
    this.sources = sources;
    this.selections = selections;
    this.corrections = corrections;
    this.activity = activity;
    this.applications = applications;
  }

  @Transactional(readOnly = true)
  public List<OpportunityTimelineEvent> of(String opportunityId) {
    var events = new ArrayList<OpportunityTimelineEvent>();
    Instant firstDiscovery = addDiscoveries(opportunityId, events);
    addSelections(opportunityId, firstDiscovery, events);
    addCorrections(opportunityId, events);
    addActivities(opportunityId, events);
    applications
        .forOpportunity(opportunityId)
        .ifPresent(applied ->
            events.add(
                new OpportunityTimelineEvent(
                    "app-" + applied.applicationId(),
                    TimelineEventKind.applied,
                    USER_LABEL,
                    "Registrou candidatura.",
                    null,
                    applied.confirmedAt())));
    events.sort(
        Comparator.comparing(OpportunityTimelineEvent::at)
            .thenComparing(OpportunityTimelineEvent::kind)
            .thenComparing(OpportunityTimelineEvent::id));
    return List.copyOf(events);
  }

  private Instant addDiscoveries(String opportunityId, List<OpportunityTimelineEvent> out) {
    Instant first = null;
    for (var discovery : sources.discoveries(opportunityId)) {
      Instant at = Instant.parse(discovery.firstSeenAt());
      if (first == null || at.isBefore(first)) first = at;
      boolean manual = MANUAL_SOURCE.equals(discovery.source());
      out.add(
          new OpportunityTimelineEvent(
              "src-" + discovery.id(),
              TimelineEventKind.discovered,
              manual ? MANUAL_LABEL : BOT_LABEL,
              "Encontrada em " + discovery.source() + ".",
              null,
              at));
    }
    return first;
  }

  /**
   * Um evento por linha de {@code daily_selection} da vaga (contrato: "derivada de {@code
   * daily_selection} quando presente"), em ordem de dia. {@code daily_selection} guarda só o dia
   * local; o instante é o início desse dia, nunca antes da primeira descoberta (a seleção só ocorre
   * depois que a vaga existe).
   */
  private void addSelections(
      String opportunityId, Instant firstDiscovery, List<OpportunityTimelineEvent> out) {
    for (LocalDate day : selections.selectedDays(opportunityId)) {
      Instant at = startOf(day);
      if (firstDiscovery != null && at.isBefore(firstDiscovery)) at = firstDiscovery;
      out.add(
          new OpportunityTimelineEvent(
              "sel-" + opportunityId + "-" + day,
              TimelineEventKind.selected_today,
              SYSTEM_LABEL,
              "Selecionada para a revisão do dia " + DAY.format(day) + ".",
              null,
              at));
    }
  }

  private void addCorrections(String opportunityId, List<OpportunityTimelineEvent> out) {
    var byOperation = new LinkedHashMap<Instant, List<OpportunityCorrectionEntry>>();
    for (var entry : corrections.forOpportunity(opportunityId)) {
      byOperation.computeIfAbsent(entry.correctedAt(), at -> new ArrayList<>()).add(entry);
    }
    byOperation.forEach((at, entries) -> {
      var fields =
          new EnumMap<CorrectableOpportunityField, String>(CorrectableOpportunityField.class);
      String id = null;
      String reason = null;
      for (var entry : entries) {
        fields.put(entry.fieldName(), FIELD_LABELS.get(entry.fieldName()));
        if (id == null || entry.id().compareTo(id) < 0) id = entry.id();
        if (reason == null && entry.reason() != null && !entry.reason().isBlank()) {
          reason = entry.reason();
        }
      }
      out.add(
          new OpportunityTimelineEvent(
              "cor-" + id,
              TimelineEventKind.corrected,
              USER_LABEL,
              "Corrigiu " + joinLabels(List.copyOf(fields.values())) + ".",
              reason,
              at));
    });
  }

  private void addActivities(String opportunityId, List<OpportunityTimelineEvent> out) {
    for (ActivityRecord record : activity.forTarget(TARGET_TYPE, opportunityId)) {
      TimelineEventKind kind = kindOf(record.action());
      if (kind == null) continue;
      out.add(
          new OpportunityTimelineEvent(
              "act-" + record.id(),
              kind,
              USER_LABEL,
              switch (kind) {
                case external_opened -> "Abriu o link da vaga.";
                case discarded -> "Descartou a vaga.";
                default -> "Restaurou a vaga.";
              },
              kind == TimelineEventKind.external_opened ? EXTERNAL_OPENED_NOTE : null,
              record.at()));
    }
  }

  /**
   * {@code opportunity.none} é o que a restauração grava; {@code opportunity.corrected} é omitido.
   */
  private static TimelineEventKind kindOf(String action) {
    return switch (action) {
      case "opportunity.external_opened" -> TimelineEventKind.external_opened;
      case "opportunity.discarded" -> TimelineEventKind.discarded;
      case "opportunity.none", "opportunity.restored" -> TimelineEventKind.restored;
      default -> null;
    };
  }

  private static Instant startOf(LocalDate day) {
    return day.atStartOfDay(AppClock.ZONE).toInstant();
  }

  private static String joinLabels(List<String> labels) {
    if (labels.size() == 1) return labels.getFirst();
    return labels.subList(0, labels.size() - 1).stream().collect(Collectors.joining(", "))
        + " e "
        + labels.getLast();
  }

  private static Map<CorrectableOpportunityField, String> fieldLabels() {
    var labels =
        new EnumMap<CorrectableOpportunityField, String>(CorrectableOpportunityField.class);
    labels.put(CorrectableOpportunityField.title, "título");
    labels.put(CorrectableOpportunityField.company_name, "empresa");
    labels.put(CorrectableOpportunityField.location, "local");
    labels.put(CorrectableOpportunityField.work_mode, "modalidade");
    labels.put(CorrectableOpportunityField.contract_type, "contrato");
    labels.put(CorrectableOpportunityField.salary_min, "salário mínimo");
    labels.put(CorrectableOpportunityField.salary_max, "salário máximo");
    labels.put(CorrectableOpportunityField.salary_unit, "unidade do salário");
    labels.put(CorrectableOpportunityField.availability, "disponibilidade");
    labels.put(CorrectableOpportunityField.deadline_at, "prazo");
    labels.put(CorrectableOpportunityField.published_at, "data de publicação");
    return labels;
  }
}
