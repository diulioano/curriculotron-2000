package dev.curriculotron.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class LocalAccessTest {
  @Test
  void refusesLocalProfileOnLanListener() {
    var env = new MockEnvironment().withProperty("server.address", "0.0.0.0");
    env.setActiveProfiles("local");
    assertThatThrownBy(() -> new LocalAccess(env)).isInstanceOf(IllegalStateException.class);
    assertThat(new LocalAccess(env.withProperty("server.address", "127.0.0.1")).enabled()).isTrue();
  }
}
