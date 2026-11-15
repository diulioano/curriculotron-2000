package dev.curriculotron.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdsTest {

  @Test
  void geraUuidVersao7VarianteRfc() {
    UUID id = UUID.fromString(Ids.newId());
    assertThat(id.version()).isEqualTo(7);
    assertThat(id.variant()).isEqualTo(2);
  }

  @Test
  void ordenavelPorTempo() {
    String a = Ids.uuidV7(Instant.parse("2026-11-14T10:00:00Z")).toString();
    String b = Ids.uuidV7(Instant.parse("2026-11-14T10:00:01Z")).toString();
    assertThat(a).isLessThan(b);
  }

  @Test
  void prefixoCodificaMilissegundos() {
    Instant t = Instant.parse("2026-11-14T10:00:00Z");
    UUID id = Ids.uuidV7(t);
    assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(t.toEpochMilli());
  }
}
