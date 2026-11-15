package dev.curriculotron.opportunities.application;

import dev.curriculotron.opportunities.application.port.out.CompatibilityRepository;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Axis;
import dev.curriculotron.opportunities.domain.CompatibilityEvaluator;
import dev.curriculotron.opportunities.domain.JobFactExtractor;
import dev.curriculotron.opportunities.domain.ObjectiveFilter;
import dev.curriculotron.opportunities.domain.RuleBasedRanker;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public final class AssessOpportunityService {
  private final AssessmentInputService inputs;
  private final CompatibilityRepository assessments;
  private final WriteExecutor writer;
  private final AppClock clock;
  private final CompatibilityEvaluator evaluator = new CompatibilityEvaluator();
  private final RuleBasedRanker ranker = new RuleBasedRanker();
  private final JobFactExtractor extractor = new JobFactExtractor();
  private final ObjectiveFilter salaryFilter = new ObjectiveFilter();

  public AssessOpportunityService(
      AssessmentInputService inputs,
      CompatibilityRepository assessments,
      WriteExecutor writer,
      AppClock clock) {
    this.inputs = inputs;
    this.assessments = assessments;
    this.writer = writer;
    this.clock = clock;
  }

  public boolean assessAndSave(String id) {
    for (int attempt = 0; attempt < 3; attempt++) {
      var snapshot = inputs.snapshot(id);
      if (snapshot.isEmpty()) return false;
      var assessment = calculate(snapshot.get());
      if (writer.write(() -> assessments.saveIfCurrent(assessment))) return true;
    }
    return false;
  }

  public CompatibilityAssessment calculate(AssessmentInputService.Input input) {
    var job = input.job();
    var settings = input.settings();
    var criteria = settings.criteria();
    List<VacancyEvidence> evidence = input.evidence();
    if (evidence.isEmpty())
      evidence =
          List.of(
              new VacancyEvidence(
                  "stored-" + job.id(),
                  job.id(),
                  null,
                  "stored",
                  job.title(),
                  job.location(),
                  null,
                  VacancyEvidence.Completeness.unknown,
                  "missing_description",
                  1,
                  null,
                  null));
    var comparison =
        evaluator.evaluate(
            evidence, new ArrayList<>(criteria.roles()), new ArrayList<>(criteria.stack()));
    var salary = job.salary();
    var salaryResult =
        salaryFilter.evaluate(
            job.contractType(),
            salary == null ? "unknown" : salary.unit(),
            salary == null ? null : salary.min(),
            salary == null ? null : salary.max(),
            settings.cltMinimum(),
            settings.pjMinimum());
    String text =
        String.join("\n", evidence.stream().map(e -> e.text() == null ? "" : e.text()).toList());
    var facts =
        extractor.extract(
            job.title(),
            job.companyName(),
            job.location(),
            text,
            criteria.stack(),
            criteria.hybridRegions());
    var ranked =
        ranker.rank(
            new RuleBasedRanker.Input(
                job.title(),
                job.companyName(),
                job.workMode().equals("unknown") ? facts.workMode() : job.workMode(),
                facts.region(),
                facts.seniority(),
                input.juniorJustification(),
                Set.of(),
                salaryResult),
            criteria,
            comparison);
    var reasons = new ArrayList<>(ranked.reasons());
    if (comparison.role().status() == Axis.unknown)
      reasons.add("Função compatível ainda não confirmada.");
    if (comparison.role().status() == Axis.mismatched)
      reasons.add("Função incompatível com os cargos configurados.");
    if (comparison.technology().status() == Axis.unknown)
      reasons.add("Compatibilidade tecnológica não confirmada.");
    if (comparison.technology().status() == Axis.mismatched)
      reasons.add("Tecnologias incompatíveis com o perfil.");
    var codes = new ArrayList<>(comparison.role().reasons());
    codes.addAll(comparison.technology().reasons());
    var proof = new ArrayList<>(comparison.role().evidence());
    proof.addAll(comparison.technology().evidence());
    return new CompatibilityAssessment(
        job.id(),
        settings.version(),
        CompatibilityAssessment.EVALUATOR_VERSION,
        input.evidenceRevision(),
        clock.now(),
        comparison.role().status(),
        comparison.technology().status(),
        codes,
        new CompatibilityAssessment.CriteriaSnapshot(
            criteria, settings.cltMinimum(), settings.pjMinimum()),
        proof,
        ranked.fit(),
        ranked.score(),
        reasons);
  }
}
