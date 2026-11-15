package dev.curriculotron.auth.adapter.in.web;

import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Explicit local mode; never enables password bypass on a LAN listener. */
@Component
class LocalAccess {
  private final boolean enabled;

  LocalAccess(Environment env) {
    enabled = env.acceptsProfiles(Profiles.of("local"));
    if (enabled && !List.of("127.0.0.1", "::1").contains(env.getProperty("server.address")))
      throw new IllegalStateException("O perfil local exige server.address em loopback.");
  }

  boolean enabled() {
    return enabled;
  }

  void authenticate() {
    if (enabled) {
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(
          UsernamePasswordAuthenticationToken.authenticated(
              "local-owner", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
      SecurityContextHolder.setContext(context);
    }
  }
}
