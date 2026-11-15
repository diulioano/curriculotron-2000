package dev.curriculotron.shared;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/** Gerador de UUIDv7 (RFC 9562): ordenável por tempo, usado como {@code id} em texto. */
public final class Ids {

  private static final SecureRandom RANDOM = new SecureRandom();

  private Ids() {}

  public static String newId() {
    return uuidV7(Instant.now()).toString();
  }

  static UUID uuidV7(Instant at) {
    long millis = at.toEpochMilli();
    byte[] rand = new byte[10];
    RANDOM.nextBytes(rand);

    long msb = (millis & 0xFFFF_FFFF_FFFFL) << 16;
    msb |= 0x7000L; // versão 7
    msb |= ((rand[0] & 0x0FL) << 8) | (rand[1] & 0xFFL);

    long lsb = 0x8000_0000_0000_0000L; // variante RFC 4122
    lsb |= (rand[2] & 0x3FL) << 56;
    for (int i = 3; i < 10; i++) {
      lsb |= (rand[i] & 0xFFL) << (8 * (9 - i));
    }
    return new UUID(msb, lsb);
  }
}
