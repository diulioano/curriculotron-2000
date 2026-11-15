package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.*;

import dev.curriculotron.opportunities.application.DedupeService;
import dev.curriculotron.shared.Ids;
import dev.curriculotron.support.AppTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DedupeServiceTest extends AppTestBase {
  @Autowired DedupeService dedupe;
  @Autowired EntityManager em;

  @Test
  void weakIdentityIncludesLocation() {
    String key = Ids.newId();
    String first =
        dedupe.ingest(
            "manual", null, "https://location.test/a/" + key, "", () -> createAt("Porto Alegre"));
    String second =
        dedupe.ingest(
            "manual", null, "https://location.test/b/" + key, "", () -> createAt("Recife"));
    Number count =
        (Number)
            em.createNativeQuery(
                    "SELECT count(*) FROM opportunity_match_candidate WHERE (opportunity_a=:a AND opportunity_b=:b) OR (opportunity_a=:b AND opportunity_b=:a)")
                .setParameter("a", first)
                .setParameter("b", second)
                .getSingleResult();
    assertThat(count.intValue()).isZero();
  }

  private String createAt(String location) {
    String id = create("none");
    em.createNativeQuery("UPDATE opportunity SET location=:location WHERE id=:id")
        .setParameter("location", location)
        .setParameter("id", id)
        .executeUpdate();
    return id;
  }

  @Test
  void canonicalIdentityDoesNotRecreateDiscardedOrAppliedJobs() {
    for (String decision : new String[] {"discarded", "applied"}) {
      String url = "https://example.com/" + decision;
      String id = dedupe.ingest("manual", null, url, "original", () -> create(decision));
      assertThat(
              dedupe.ingest(
                  "manual",
                  null,
                  url + "?utm_source=mail",
                  "repeat",
                  () -> {
                    throw new AssertionError("Duplicated vacancy");
                  }))
          .isEqualTo(id);
      assertThat(
              em.createNativeQuery(
                      "SELECT user_decision FROM opportunity WHERE id=:id", String.class)
                  .setParameter("id", id)
                  .getSingleResult())
          .isEqualTo(decision);
    }
  }

  @Test
  void strongExternalIdentityReusesJobWhileWeakIdentityOnlySuggests() {
    String external = Ids.newId();
    String first =
        dedupe.ingest(
            "board", external, "https://board.test/" + external, "", () -> create("none"));
    assertThat(
            dedupe.ingest(
                "board",
                external,
                "https://board.test/changed/" + external,
                "",
                () -> {
                  throw new AssertionError();
                }))
        .isEqualTo(first);
    String second =
        dedupe.ingest("other", null, "https://other.test/" + external, "", () -> create("none"));
    assertThat(second).isNotEqualTo(first);
    Number count =
        (Number)
            em.createNativeQuery(
                    "SELECT count(*) FROM opportunity_match_candidate WHERE (opportunity_a=:a AND opportunity_b=:b) OR (opportunity_a=:b AND opportunity_b=:a)")
                .setParameter("a", first)
                .setParameter("b", second)
                .getSingleResult();
    assertThat(count.intValue()).isEqualTo(1);
  }

  @Test
  void remembersAdditionalUrlsAndExternalIdsWithoutReplacingEvidence() {
    String key = Ids.newId();
    String url = "https://aliases.test/" + key;
    String id = dedupe.ingest("board", key, url, "original", () -> create("discarded"));
    dedupe.ingest(
        "board",
        key,
        url + "/new",
        "new evidence",
        () -> {
          throw new AssertionError();
        });
    assertThat(
            dedupe.ingest(
                "manual",
                null,
                url + "/new",
                "",
                () -> {
                  throw new AssertionError("Lost URL alias");
                }))
        .isEqualTo(id);
    dedupe.ingest(
        "second-board",
        key,
        url,
        "",
        () -> {
          throw new AssertionError();
        });
    assertThat(
            dedupe.ingest(
                "second-board",
                key,
                url + "/third",
                "",
                () -> {
                  throw new AssertionError("Lost external identity");
                }))
        .isEqualTo(id);
    assertThat(
            em.createNativeQuery(
                    "SELECT raw_excerpt FROM opportunity_source WHERE canonical_url=:url",
                    String.class)
                .setParameter("url", url)
                .getSingleResult())
        .isEqualTo("original");
  }

  private String create(String decision) {
    String id = Ids.newId();
    em.createNativeQuery(
            """
            INSERT INTO opportunity(id,title,company_name,user_decision,created_at,updated_at)
            VALUES (:id,'Java pleno','Empresa',:decision,'2026-11-14T15:00:00Z','2026-11-14T15:00:00Z')
            """)
        .setParameter("id", id)
        .setParameter("decision", decision)
        .executeUpdate();
    return id;
  }
}
