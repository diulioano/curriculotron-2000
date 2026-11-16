package dev.curriculotron.sources;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.curriculotron.opportunities.domain.VacancyEvidence.Completeness;
import org.junit.jupiter.api.Test;

class CareersSourcesTest {
  private final PublicSourceHttp http = mock(PublicSourceHttp.class);

  @Test
  void leverKeepsTitleAndLocationLiterally() throws Exception {
    var source = new AtsBoardSource("lever", "bv", http);
    when(http.get(source.endpoint()))
        .thenReturn(
            "[{\"id\":\"a1\",\"text\":\"Desenvolvedor Java Pleno\",\"hostedUrl\":\"https://jobs.lever.co/bv/a1\","
                + "\"descriptionPlain\":\"Spring Boot\",\"workplaceType\":\"remote\",\"categories\":{\"location\":\"São Paulo\"}}]");
    var item = source.fetch().items().getFirst();
    assertThat(item.title()).isEqualTo("Desenvolvedor Java Pleno");
    assertThat(item.location()).isEqualTo("São Paulo · remote");
    assertThat(item.source()).isEqualTo("lever:bv");
  }

  @Test
  void oracleRecruitingListsRequisitionsAndRejectsArbitraryHosts() throws Exception {
    var source = new OracleRecruitingSource("epdm.fa.la1/CX_3001", http);
    assertThat(source.endpoint().getHost()).isEqualTo("epdm.fa.la1.oraclecloud.com");
    when(http.get(source.endpoint()))
        .thenReturn(
            "{\"items\":[{\"requisitionList\":[{\"Id\":\"11967\",\"Title\":\"Software Engineer Java\","
                + "\"PrimaryLocation\":\"SAO PAULO\",\"WorkplaceType\":\"Remoto\",\"ShortDescriptionStr\":\"Kafka\"}]}]}");
    var result = source.fetch();
    assertThat(result.failures()).isEmpty();
    var item = result.items().getFirst();
    assertThat(item.url())
        .isEqualTo(
            "https://epdm.fa.la1.oraclecloud.com/hcmUI/CandidateExperience/pt-BR/sites/CX_3001/job/11967");
    assertThat(item.location()).isEqualTo("SAO PAULO · Remoto");
    assertThat(item.completeness()).isEqualTo(Completeness.partial);
    assertThat(item.completenessReason()).isEqualTo("listing_summary");
    assertThatThrownBy(() -> new OracleRecruitingSource("evil.com/x", http))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void gupyReadsEmbeddedJobList() throws Exception {
    var source = new GupySource("empresa", http);
    when(http.get(source.endpoint()))
        .thenReturn(
            "<html><script id=\"__NEXT_DATA__\" type=\"application/json\">"
                + "{\"props\":{\"pageProps\":{\"jobs\":[{\"id\":42,\"title\":\"Dev Backend Java\",\"department\":\"TI\","
                + "\"workplace\":{\"address\":{\"city\":\"Porto Alegre\",\"stateShortName\":\"RS\"},\"workplaceType\":\"hybrid\"}}]}}}"
                + "</script></html>");
    var item = source.fetch().items().getFirst();
    assertThat(item.url()).isEqualTo("https://empresa.gupy.io/jobs/42");
    assertThat(item.location()).isEqualTo("Porto Alegre - RS · hybrid");
    assertThat(item.completeness()).isEqualTo(Completeness.partial);
    when(http.get(any())).thenReturn("<html>sem dados</html>");
    assertThat(source.fetch().failures()).hasSize(1);
    assertThatThrownBy(() -> new GupySource("../x", http))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void cwiReadsOpportunityCards() throws Exception {
    when(http.get(CwiCareersSource.ENDPOINT))
        .thenReturn(
            "<a href=\"https://cwi.com.br/talentos/oportunidade/desenvolvedor-a-java-268\" class=\"opportunity-card__link\">"
                + "<div><span class=\"opportunity-card__text\">Desenvolvedor(a) Java</span>"
                + "<div class=\"pills\"><span class=\"pills__pill\">Remoto</span><span class=\"pills__pill\">São Leopoldo - RS</span></div></div></a>");
    var result = new CwiCareersSource(http).fetch();
    var item = result.items().getFirst();
    assertThat(item.externalId()).isEqualTo("268");
    assertThat(item.title()).isEqualTo("Desenvolvedor(a) Java");
    assertThat(item.location()).isEqualTo("Remoto · São Leopoldo - RS");
    assertThat(item.completeness()).isEqualTo(Completeness.partial);
    when(http.get(CwiCareersSource.ENDPOINT)).thenReturn("<html></html>");
    assertThat(new CwiCareersSource(http).fetch().failures()).hasSize(1);
  }

  @Test
  void factoryAcceptsOnlyKnownAdapters() {
    var factory = new JobSourceFactory();
    assertThat(factory.forAdapter("lever:ciandt")).isInstanceOf(AtsBoardSource.class);
    assertThat(factory.forAdapter("oracle:epdm.fa.la1/CX_3001"))
        .isInstanceOf(OracleRecruitingSource.class);
    assertThat(factory.forAdapter("cwi:oportunidades")).isInstanceOf(CwiCareersSource.class);
    assertThat(factory.forAdapter("gupy:empresa")).isInstanceOf(GupySource.class);
    assertThatThrownBy(() -> factory.forAdapter("https://example.com"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> factory.forAdapter("cwi:outra"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
