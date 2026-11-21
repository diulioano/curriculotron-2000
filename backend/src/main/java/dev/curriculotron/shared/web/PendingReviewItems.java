package dev.curriculotron.shared.web;

import dev.curriculotron.opportunities.application.OpportunityNotFoundException;
import dev.curriculotron.opportunities.application.OpportunityService;
import dev.curriculotron.opportunities.application.port.out.MatchCandidateRepository.Candidate;
import dev.curriculotron.opportunities.application.port.out.OpportunitySourceRepository;
import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.orchestration.domain.MissedRoutine;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.web.HomeController.MailSummary;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Monta as pendências individuais de "Precisa de você". Há um item por duplicata aberta, por
 * mensagem que exige atenção e por rotina não executada, de modo que a contagem coincide com {@code
 * reviewQueueCount + mailNeedingAttention + missedRoutines} (paridade com a página inicial).
 */
@Component
public class PendingReviewItems {
  static final String REVIEW_ROUTE = "/revisao";
  static final String ACTIVITIES_ROUTE = "/atividades";
  private static final String DEDUPE_LABEL = "DUPLICATA · revisão";
  private static final String MAIL_LABEL = "MENSAGEM · e-mail";
  private static final String MISSED_LABEL = "ROTINA NÃO EXECUTADA";
  private static final DateTimeFormatter WHEN =
      DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm").withZone(AppClock.ZONE);

  private final OpportunityService jobs;
  private final OpportunitySourceRepository sources;

  public PendingReviewItems(OpportunityService jobs, OpportunitySourceRepository sources) {
    this.jobs = jobs;
    this.sources = sources;
  }

  public List<PendingReviewItem> of(
      List<Candidate> openCandidates, List<MailSummary> mail, List<MissedRoutine> missed) {
    var items = new ArrayList<PendingReviewItem>();
    for (Candidate candidate : openCandidates) items.add(dedupe(candidate));
    for (MailSummary message : mail) {
      items.add(
          new PendingReviewItem(
              "mail-" + message.id(),
              PendingReviewKind.email_attention,
              MAIL_LABEL,
              "“" + message.subject() + "”",
              "Mensagem aguardando leitura e classificação.",
              REVIEW_ROUTE));
    }
    for (MissedRoutine routine : missed) items.add(missed(routine));
    return List.copyOf(items);
  }

  private PendingReviewItem dedupe(Candidate candidate) {
    String title = "Possível vaga duplicada";
    try {
      Opportunity first = jobs.get(candidate.opportunityA());
      title += ": " + first.title() + " · " + first.companyName();
    } catch (OpportunityNotFoundException e) {
      // Mantém o título genérico: a pendência continua acionável na fila de revisão.
    }
    var names = new LinkedHashSet<String>();
    for (String id : List.of(candidate.opportunityA(), candidate.opportunityB())) {
      sources.discoveries(id).forEach(found -> names.add(found.source()));
    }
    String summary = candidate.signal();
    if (!names.isEmpty()) summary += ". Fontes: " + String.join(", ", names) + ".";
    return new PendingReviewItem(
        "cand-" + candidate.id(),
        PendingReviewKind.dedupe_candidate,
        DEDUPE_LABEL,
        title,
        summary,
        REVIEW_ROUTE);
  }

  private static PendingReviewItem missed(MissedRoutine routine) {
    return new PendingReviewItem(
        "missed-" + routine.routineId() + "-" + routine.scheduledFor(),
        PendingReviewKind.missed_routine,
        MISSED_LABEL,
        "Rotina de " + purposeLabel(routine.purpose()) + " não rodou",
        "Prevista para "
            + WHEN.format(routine.scheduledFor())
            + ". O computador pode ter ficado desligado.",
        ACTIVITIES_ROUTE);
  }

  private static String purposeLabel(String purpose) {
    return "search".equals(purpose) ? "busca de vagas" : purpose;
  }
}
