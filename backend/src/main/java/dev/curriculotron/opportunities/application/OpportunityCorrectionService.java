package dev.curriculotron.opportunities.application;

import dev.curriculotron.audit.application.ActivityLogService;
import dev.curriculotron.audit.domain.ActivityEntry;
import dev.curriculotron.opportunities.application.port.out.OpportunityCorrectionRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityCorrectionRepository.CorrectableState;
import dev.curriculotron.opportunities.application.port.out.OpportunityCorrectionRepository.CorrectedValues;
import dev.curriculotron.opportunities.domain.CorrectableOpportunityField;
import dev.curriculotron.opportunities.domain.OpportunityCorrectionEntry;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.Ids;
import dev.curriculotron.shared.persistence.WriteExecutor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Correção manual de campos da vaga a partir do bloco de evidência. A vaga recebe os novos valores
 * e cada mudança vira uma linha append-only; fontes e evidência original nunca são alteradas.
 */
@Service
public class OpportunityCorrectionService {
  private static final Logger log = LoggerFactory.getLogger(OpportunityCorrectionService.class);

  static final int TITLE_MAX = 240;
  static final int COMPANY_MAX = 160;
  static final int LOCATION_MAX = 160;
  static final int REASON_MAX = 500;
  static final int SALARY_INTEGER_DIGITS = 9;
  static final int SALARY_FRACTION_DIGITS = 2;
  private static final String AUTHOR = "user";
  private static final Set<String> WORK_MODES = Set.of("remote", "hybrid", "onsite", "unknown");
  private static final Set<String> CONTRACT_TYPES = Set.of("clt", "pj", "other", "unknown");
  private static final Set<String> AVAILABILITIES = Set.of("open", "closed", "unconfirmed");
  private static final Set<String> SALARY_UNITS = Set.of("monthly", "hourly", "yearly", "unknown");

  /** Intervalo salarial informado; {@code min} e {@code max} podem faltar, mas não ambos. */
  public record Salary(BigDecimal min, BigDecimal max, String unit) {}

  /** Distingue "não enviado" (mantém), "limpar" e "definir" para campos de data. */
  public record DateChange(boolean present, LocalDate value) {
    public static final DateChange UNCHANGED = new DateChange(false, null);
    public static final DateChange CLEAR = new DateChange(true, null);

    public static DateChange set(LocalDate value) {
      return new DateChange(true, Objects.requireNonNull(value));
    }
  }

  /** Campos nulos (ou {@link DateChange#UNCHANGED}) não alteram a vaga. */
  public record Command(
      String title,
      String companyName,
      String location,
      String workMode,
      String contractType,
      Salary salary,
      boolean clearSalary,
      String availability,
      DateChange deadlineAt,
      DateChange publishedAt,
      String reason) {
    public Command {
      deadlineAt = deadlineAt == null ? DateChange.UNCHANGED : deadlineAt;
      publishedAt = publishedAt == null ? DateChange.UNCHANGED : publishedAt;
    }
  }

  private record Normalized(
      String title,
      String companyName,
      boolean locationGiven,
      String location,
      String workMode,
      String contractType,
      Salary salary,
      boolean clearSalary,
      String availability,
      DateChange deadlineAt,
      DateChange publishedAt,
      String reason) {}

  private final OpportunityCorrectionRepository corrections;
  private final WriteExecutor writer;
  private final AppClock clock;
  private final ActivityLogService activity;
  private final AssessOpportunityService assessor;

  public OpportunityCorrectionService(
      OpportunityCorrectionRepository corrections,
      WriteExecutor writer,
      AppClock clock,
      ActivityLogService activity,
      AssessOpportunityService assessor) {
    this.corrections = corrections;
    this.writer = writer;
    this.clock = clock;
    this.activity = activity;
    this.assessor = assessor;
  }

  /**
   * @throws OpportunityNotFoundException se a vaga não existe
   * @throws OpportunityMergedException se a vaga foi fundida em outra
   * @throws InvalidCorrectionException se a correção é inválida ou não altera nenhum dado
   */
  public void correct(String opportunityId, Command command) {
    Normalized input = normalize(command);
    boolean reassess =
        writer.write(() -> {
          CorrectableState current =
              corrections
                  .currentState(opportunityId)
                  .orElseThrow(() -> new OpportunityNotFoundException(opportunityId));
          if (current.mergedInto() != null && !current.mergedInto().isBlank()) {
            throw new OpportunityMergedException(opportunityId, current.mergedInto());
          }
          Instant at = clock.now();
          var changes = new ArrayList<OpportunityCorrectionEntry>();
          CorrectedValues next = merge(opportunityId, current, input, at, changes);
          if (changes.isEmpty()) {
            throw new InvalidCorrectionException("Nenhum dado da vaga foi alterado.");
          }
          boolean affectsAssessment = corrections.apply(opportunityId, next, at);
          corrections.append(changes);
          activity.record(
              ActivityEntry.user("opportunity.corrected", "opportunity", opportunityId));
          return affectsAssessment;
        });
    // Fora da transação de escrita: a avaliação usa a evidência original preservada e os dados
    // corrigidos; se falhar, a correção segue válida e a compatibilidade fica como desatualizada.
    if (reassess) reassessQuietly(opportunityId);
  }

  public List<OpportunityCorrectionEntry> history(String opportunityId) {
    return corrections.forOpportunity(opportunityId);
  }

  private void reassessQuietly(String opportunityId) {
    try {
      if (!assessor.assessAndSave(opportunityId)) {
        log.warn("Avaliação não publicada após correção opportunityId={}", opportunityId);
      }
    } catch (RuntimeException failure) {
      Throwable cause = failure.getCause();
      log.warn(
          "Falha ao reavaliar após correção opportunityId={} tipo={} causa={}",
          opportunityId,
          failure.getClass().getName(),
          cause == null ? "-" : cause.getClass().getName());
    }
  }

  private CorrectedValues merge(
      String id,
      CorrectableState current,
      Normalized input,
      Instant at,
      List<OpportunityCorrectionEntry> changes) {
    var track = new Tracker(id, input.reason(), at, changes);

    String title = track.text(CorrectableOpportunityField.title, current.title(), input.title());
    String company =
        track.text(
            CorrectableOpportunityField.company_name, current.companyName(), input.companyName());
    String location =
        input.locationGiven()
            ? track.replace(
                CorrectableOpportunityField.location,
                current.location(),
                blankToNull(strip(current.location())),
                input.location())
            : current.location();
    String workMode =
        track.text(CorrectableOpportunityField.work_mode, current.workMode(), input.workMode());
    String contract =
        track.text(
            CorrectableOpportunityField.contract_type,
            current.contractType(),
            input.contractType());

    BigDecimal salaryMin = current.salaryMin();
    BigDecimal salaryMax = current.salaryMax();
    String salaryUnit = current.salaryUnit();
    if (input.clearSalary()) {
      salaryMin = null;
      salaryMax = null;
      salaryUnit = "unknown";
    } else if (input.salary() != null) {
      salaryMin = input.salary().min();
      salaryMax = input.salary().max();
      salaryUnit = input.salary().unit();
    }
    salaryMin =
        track.amount(CorrectableOpportunityField.salary_min, current.salaryMin(), salaryMin);
    salaryMax =
        track.amount(CorrectableOpportunityField.salary_max, current.salaryMax(), salaryMax);
    salaryUnit =
        track.text(CorrectableOpportunityField.salary_unit, current.salaryUnit(), salaryUnit);

    String availability =
        track.text(
            CorrectableOpportunityField.availability, current.availability(), input.availability());
    String deadline =
        track.date(
            CorrectableOpportunityField.deadline_at, current.deadlineAt(), input.deadlineAt());
    String published =
        track.date(
            CorrectableOpportunityField.published_at, current.publishedAt(), input.publishedAt());
    return new CorrectedValues(
        title,
        company,
        location,
        workMode,
        contract,
        salaryMin,
        salaryMax,
        salaryUnit,
        availability,
        deadline,
        published);
  }

  /** Acumula uma entrada para cada campo cujo valor efetivamente mudou. */
  private static final class Tracker {
    private final String opportunityId;
    private final String reason;
    private final Instant at;
    private final List<OpportunityCorrectionEntry> changes;

    Tracker(String opportunityId, String reason, Instant at, List<OpportunityCorrectionEntry> out) {
      this.opportunityId = opportunityId;
      this.reason = reason;
      this.at = at;
      this.changes = out;
    }

    /**
     * {@code next == null} mantém o valor atual; os demais substituem. A comparação ignora espaços
     * nas pontas do valor atual para não gravar correção espúria.
     */
    String text(CorrectableOpportunityField field, String current, String next) {
      if (next == null || Objects.equals(strip(current), next)) return current;
      record(field, current, next);
      return next;
    }

    /**
     * Substitui o valor, inclusive por nulo (campo limpo explicitamente). {@code comparable} é o
     * valor atual já normalizado, usado só na comparação.
     */
    String replace(
        CorrectableOpportunityField field, String current, String comparable, String next) {
      if (Objects.equals(comparable, next)) return current;
      record(field, current, next);
      return next;
    }

    /** Salário: aqui {@code next} nulo significa "sem valor" (já resolvido pelo chamador). */
    BigDecimal amount(CorrectableOpportunityField field, BigDecimal current, BigDecimal next) {
      boolean same = current == null ? next == null : next != null && current.compareTo(next) == 0;
      if (same) return current;
      record(field, current == null ? null : plain(current), next == null ? null : plain(next));
      return next;
    }

    String date(CorrectableOpportunityField field, String current, DateChange change) {
      if (!change.present()) return current;
      String next = change.value() == null ? null : change.value().toString();
      if (Objects.equals(current, next)) return current;
      record(field, current, next);
      return next;
    }

    private void record(CorrectableOpportunityField field, String previous, String corrected) {
      changes.add(
          new OpportunityCorrectionEntry(
              Ids.newId(), opportunityId, field, previous, corrected, reason, AUTHOR, at));
    }
  }

  private static String strip(String value) {
    return value == null ? null : value.strip();
  }

  private static String blankToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  private static String plain(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  private static Normalized normalize(Command c) {
    if (c == null) throw new InvalidCorrectionException("Informe ao menos um dado a corrigir.");
    String title = required(c.title(), TITLE_MAX, "Título");
    String company = required(c.companyName(), COMPANY_MAX, "Empresa");
    boolean locationGiven = c.location() != null;
    String location = null;
    if (locationGiven) {
      location = c.location().strip();
      if (location.length() > LOCATION_MAX)
        throw invalid("Local excede " + LOCATION_MAX + " caracteres.");
      if (location.isEmpty()) location = null;
    }
    String reason = null;
    if (c.reason() != null && !c.reason().isBlank()) {
      reason = c.reason().strip();
      if (reason.length() > REASON_MAX)
        throw invalid("Motivo excede " + REASON_MAX + " caracteres.");
    }
    if (c.clearSalary() && c.salary() != null) {
      throw invalid("Não é possível limpar e informar o salário na mesma correção.");
    }
    return new Normalized(
        title,
        company,
        locationGiven,
        location,
        oneOf(c.workMode(), WORK_MODES, "Modalidade"),
        oneOf(c.contractType(), CONTRACT_TYPES, "Contrato"),
        validSalary(c.salary()),
        c.clearSalary(),
        oneOf(c.availability(), AVAILABILITIES, "Disponibilidade"),
        c.deadlineAt(),
        c.publishedAt(),
        reason);
  }

  private static String required(String value, int max, String label) {
    if (value == null) return null;
    String text = value.strip();
    if (text.isEmpty()) throw invalid(label + " não pode ficar vazio.");
    if (text.length() > max) throw invalid(label + " excede " + max + " caracteres.");
    return text;
  }

  private static String oneOf(String value, Set<String> allowed, String label) {
    if (value != null && !allowed.contains(value)) throw invalid(label + " inválido.");
    return value;
  }

  private static Salary validSalary(Salary salary) {
    if (salary == null) return null;
    if (salary.min() == null && salary.max() == null) {
      throw invalid("Informe salário mínimo ou máximo.");
    }
    if ((salary.min() != null && salary.min().signum() < 0)
        || (salary.max() != null && salary.max().signum() < 0)) {
      throw invalid("Salário não pode ser negativo.");
    }
    if (!fitsScale(salary.min()) || !fitsScale(salary.max())) {
      throw invalid(
          "Salário aceita até " + SALARY_INTEGER_DIGITS + " dígitos inteiros e 2 casas decimais.");
    }
    if (salary.min() != null && salary.max() != null && salary.min().compareTo(salary.max()) > 0) {
      throw invalid("O salário mínimo não pode ser maior que o máximo.");
    }
    if (salary.unit() == null || !SALARY_UNITS.contains(salary.unit())) {
      throw invalid("Unidade do salário inválida.");
    }
    return salary;
  }

  private static boolean fitsScale(BigDecimal value) {
    if (value == null) return true;
    BigDecimal normalized = value.stripTrailingZeros();
    int fraction = Math.max(normalized.scale(), 0);
    int integer = normalized.precision() - normalized.scale();
    return fraction <= SALARY_FRACTION_DIGITS && integer <= SALARY_INTEGER_DIGITS;
  }

  private static InvalidCorrectionException invalid(String message) {
    return new InvalidCorrectionException(message);
  }
}
