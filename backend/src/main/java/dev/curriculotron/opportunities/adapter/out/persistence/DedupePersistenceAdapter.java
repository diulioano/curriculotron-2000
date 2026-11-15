package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.opportunities.application.port.out.DedupeRepository;
import dev.curriculotron.shared.Ids;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class DedupePersistenceAdapter implements DedupeRepository {
  private final EntityManager em;

  DedupePersistenceAdapter(EntityManager em) {
    this.em = em;
  }

  public Optional<String> byExternalId(String source, String externalId) {
    if (externalId == null) return Optional.empty();
    return em.createNativeQuery(
            "SELECT opportunity_id FROM opportunity_external_identity WHERE source=:source AND external_id=:external",
            String.class)
        .setParameter("source", source)
        .setParameter("external", externalId)
        .getResultStream()
        .findFirst();
  }

  public Optional<String> byUrl(String url) {
    return em.createNativeQuery(
            "SELECT opportunity_id FROM opportunity_url_identity WHERE canonical_url=:url",
            String.class)
        .setParameter("url", url)
        .getResultStream()
        .findFirst();
  }

  public void attach(
      String id, String source, String externalId, String url, String excerpt, Instant at) {
    em.createNativeQuery(
            "INSERT INTO opportunity_url_identity(canonical_url,opportunity_id) VALUES (:url,:id) ON CONFLICT DO NOTHING")
        .setParameter("url", url)
        .setParameter("id", id)
        .executeUpdate();
    if (externalId != null) {
      em.createNativeQuery(
              "INSERT INTO opportunity_external_identity(source,external_id,opportunity_id) VALUES (:source,:external,:id) ON CONFLICT DO NOTHING")
          .setParameter("source", source)
          .setParameter("external", externalId)
          .setParameter("id", id)
          .executeUpdate();
    }
    // An existing identity keeps its original evidence and user decision intact.
    em.createNativeQuery(
            """
            INSERT INTO opportunity_source(id,opportunity_id,source,external_id,canonical_url,fetched_at,raw_excerpt,created_at,updated_at)
            VALUES (:id,:opportunity,:source,:external,:url,:at,:excerpt,:at,:at)
            ON CONFLICT DO NOTHING
            """)
        .setParameter("id", Ids.newId())
        .setParameter("opportunity", id)
        .setParameter("source", source)
        .setParameter("external", externalId)
        .setParameter("url", url)
        .setParameter("at", at.toString())
        .setParameter("excerpt", excerpt)
        .executeUpdate();
  }

  public List<String> similar(String id) {
    return em.createNativeQuery(
            """
            SELECT b.id FROM opportunity a JOIN opportunity b
            ON lower(trim(a.title))=lower(trim(b.title)) AND lower(trim(a.company_name))=lower(trim(b.company_name))
            AND lower(trim(coalesce(a.location,'')))=lower(trim(coalesce(b.location,'')))
            WHERE a.id=:id AND b.id<>a.id
            AND a.merged_into IS NULL AND b.merged_into IS NULL
            """,
            String.class)
        .setParameter("id", id)
        .getResultList();
  }

  public void suggest(String a, String b, Instant at) {
    String first = a.compareTo(b) < 0 ? a : b;
    String second = a.compareTo(b) < 0 ? b : a;
    em.createNativeQuery(
            """
            INSERT INTO opportunity_match_candidate(opportunity_a,opportunity_b,signal,state,created_at,updated_at)
            VALUES (:a,:b,'Identidades conflitantes ou título e empresa coincidentes','open',:at,:at)
            ON CONFLICT DO NOTHING
            """)
        .setParameter("a", first)
        .setParameter("b", second)
        .setParameter("at", at.toString())
        .executeUpdate();
  }
}
