package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.opportunities.domain.CorrectableOpportunityField;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OpportunityCorrectionJpaRepository
    extends JpaRepository<OpportunityCorrectionEntity, String> {
  List<OpportunityCorrectionEntity> findByOpportunityIdOrderByCorrectedAtDescIdDesc(
      String opportunityId);

  Optional<OpportunityCorrectionEntity>
      findFirstByOpportunityIdAndFieldNameOrderByCorrectedAtDescIdDesc(
          String opportunityId, CorrectableOpportunityField fieldName);

  @Query(
      "select distinct c.fieldName from OpportunityCorrectionEntity c "
          + "where c.opportunityId = :opportunityId")
  List<CorrectableOpportunityField> distinctFieldNames(
      @Param("opportunityId") String opportunityId);

  /** Projeção distinta (vaga, campo) para várias vagas em uma única consulta. */
  @Query(
      "select distinct new dev.curriculotron.opportunities.adapter.out.persistence."
          + "CorrectedFieldRow(c.opportunityId, c.fieldName) "
          + "from OpportunityCorrectionEntity c where c.opportunityId in :opportunityIds")
  List<CorrectedFieldRow> distinctFieldNamesByOpportunity(
      @Param("opportunityIds") Collection<String> opportunityIds);
}
