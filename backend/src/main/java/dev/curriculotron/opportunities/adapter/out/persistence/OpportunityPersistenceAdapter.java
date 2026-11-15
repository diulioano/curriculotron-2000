package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.opportunities.application.port.out.OpportunityRepository;
import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.shared.Ids;
import dev.curriculotron.shared.persistence.StringListJsonConverter;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class OpportunityPersistenceAdapter implements OpportunityRepository {
  private final EntityManager em;
  private final StringListJsonConverter json = new StringListJsonConverter();
  private static final String COLUMNS =
      "SELECT id,title,company_name,location,work_mode,contract_type,salary_min,salary_max,salary_unit,requirements_facts,requirements_inferred,fit_reasons,user_decision,availability,merged_into FROM opportunity ";

  OpportunityPersistenceAdapter(EntityManager em) {
    this.em = em;
  }

  public Optional<Opportunity> find(String id) {
    List<?> rows =
        em.createNativeQuery(COLUMNS + "WHERE id=:id").setParameter("id", id).getResultList();
    return rows.stream().findFirst().map(row -> map((Object[]) row));
  }

  public List<Opportunity> discarded() {
    List<?> rows =
        em.createNativeQuery(
                COLUMNS
                    + "WHERE merged_into IS NULL AND user_decision='discarded' ORDER BY updated_at DESC,id")
            .getResultList();
    return rows.stream().map(row -> map((Object[]) row)).toList();
  }

  public String createManual(String text, String at) {
    String id = Ids.newId();
    String title =
        text == null || text.isBlank() ? "Vaga a revisar" : text.lines().findFirst().orElseThrow();
    if (title.length() > 200) title = title.substring(0, 200);
    em.createNativeQuery(
            "INSERT INTO opportunity(id,title,company_name,requirements_facts,created_at,updated_at) VALUES (:id,:title,'Empresa a confirmar',:facts,:at,:at)")
        .setParameter("id", id)
        .setParameter("title", title)
        .setParameter(
            "facts", json.convertToDatabaseColumn(text == null ? List.of() : List.of(text)))
        .setParameter("at", at)
        .executeUpdate();
    return id;
  }

  public String createFromSource(SourcedOpportunity o, String at) {
    String id = Ids.newId();
    em.createNativeQuery(
            "INSERT INTO opportunity(id,title,company_name,location,work_mode,requirements_facts,availability,fit,fit_score,fit_reasons,created_at,updated_at) VALUES (:id,:title,:company,:location,:mode,:facts,'open',:fit,:score,:reasons,:at,:at)")
        .setParameter("id", id)
        .setParameter("title", o.title())
        .setParameter("company", o.company() == null ? "Empresa a confirmar" : o.company())
        .setParameter("location", o.location())
        .setParameter("mode", o.workMode())
        .setParameter("facts", json.convertToDatabaseColumn(o.requirementFacts()))
        .setParameter("fit", o.fit())
        .setParameter("score", o.fitScore())
        .setParameter("reasons", json.convertToDatabaseColumn(o.fitReasons()))
        .setParameter("at", at)
        .executeUpdate();
    return id;
  }

  public boolean setDecision(String id, String decision, String at) {
    if (!List.of("none", "discarded").contains(decision))
      throw new IllegalArgumentException("Decisão inválida");
    return em.createNativeQuery(
                "UPDATE opportunity SET user_decision=:decision,updated_at=:at,decision_reverted_at=CASE WHEN :decision='none' THEN :at ELSE decision_reverted_at END WHERE id=:id AND merged_into IS NULL AND user_decision<>'applied'")
            .setParameter("decision", decision)
            .setParameter("at", at)
            .setParameter("id", id)
            .executeUpdate()
        == 1;
  }

  private Opportunity map(Object[] r) {
    return new Opportunity(
        (String) r[0],
        (String) r[1],
        (String) r[2],
        (String) r[3],
        (String) r[4],
        (String) r[5],
        r[6] == null && r[7] == null
            ? null
            : new Opportunity.Salary(decimal(r[6]), decimal(r[7]), (String) r[8]),
        json.convertToEntityAttribute((String) r[9]),
        json.convertToEntityAttribute((String) r[10]),
        json.convertToEntityAttribute((String) r[11]),
        (String) r[12],
        (String) r[13],
        (String) r[14]);
  }

  private BigDecimal decimal(Object value) {
    return value == null ? null : new BigDecimal(value.toString());
  }
}
