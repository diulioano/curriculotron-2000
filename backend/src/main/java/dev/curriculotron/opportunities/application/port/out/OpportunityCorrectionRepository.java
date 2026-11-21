package dev.curriculotron.opportunities.application.port.out;

import dev.curriculotron.opportunities.domain.CorrectableOpportunityField;
import dev.curriculotron.opportunities.domain.OpportunityCorrectionEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface OpportunityCorrectionRepository {
  /** Valores atuais dos campos corrigíveis da vaga. */
  Optional<CorrectableState> currentState(String opportunityId);

  /**
   * Aplica os novos valores à vaga. Nunca toca em fontes nem na evidência original; devolve se a
   * mudança afeta a avaliação de compatibilidade (e portanto invalidou a revisão da evidência).
   */
  boolean apply(String opportunityId, CorrectedValues values, Instant at);

  /** Prazo de inscrição atual (data ISO) ou vazio quando não informado. */
  Optional<String> deadlineAt(String opportunityId);

  /** Prazos de inscrição em lote; vagas sem prazo informado não aparecem no mapa. */
  Map<String, String> deadlinesAt(Collection<String> opportunityIds);

  void append(List<OpportunityCorrectionEntry> entries);

  /** Campos que a pessoa já corrigiu na vaga; a coleta nunca deve sobrescrevê-los. */
  Set<CorrectableOpportunityField> correctedFields(String opportunityId);

  /**
   * Campos já corrigidos por vaga, em uma única consulta; vagas sem correção não aparecem no mapa.
   */
  Map<String, Set<CorrectableOpportunityField>> correctedFieldsOf(
      Collection<String> opportunityIds);

  /** Correção mais recente do campo, se houver. */
  Optional<OpportunityCorrectionEntry> latestCorrection(
      String opportunityId, CorrectableOpportunityField field);

  /** Correções da vaga, da mais recente para a mais antiga. */
  List<OpportunityCorrectionEntry> forOpportunity(String opportunityId);

  record CorrectableState(
      String title,
      String companyName,
      String location,
      String workMode,
      String contractType,
      BigDecimal salaryMin,
      BigDecimal salaryMax,
      String salaryUnit,
      String availability,
      String deadlineAt,
      String publishedAt,
      String mergedInto) {}

  /** Estado final de todos os campos corrigíveis após a correção (valores já normalizados). */
  record CorrectedValues(
      String title,
      String companyName,
      String location,
      String workMode,
      String contractType,
      BigDecimal salaryMin,
      BigDecimal salaryMax,
      String salaryUnit,
      String availability,
      String deadlineAt,
      String publishedAt) {

    public static CorrectedValues from(CorrectableState state) {
      return new CorrectedValues(
          state.title(),
          state.companyName(),
          state.location(),
          state.workMode(),
          state.contractType(),
          state.salaryMin(),
          state.salaryMax(),
          state.salaryUnit(),
          state.availability(),
          state.deadlineAt(),
          state.publishedAt());
    }
  }
}
