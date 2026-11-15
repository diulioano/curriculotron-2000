package dev.curriculotron.auth.adapter.in.web;

import dev.curriculotron.idempotency.adapter.in.web.IdempotencyFilter;
import dev.curriculotron.idempotency.application.IdempotencyService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Acesso privado (NFR-001, D-06): toda rota {@code /api/**} exige sessão, exceto saúde, consulta de
 * sessão, login e setup. Mutações exigem {@code X-CSRF-Token} (token sincronizado na sessão).
 * Falhas devolvem JSON sem dados privados.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

  public static final String CSRF_HEADER = "X-CSRF-Token";

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  CsrfTokenRepository csrfTokenRepository() {
    var repo = new HttpSessionCsrfTokenRepository();
    repo.setHeaderName(CSRF_HEADER);
    return repo;
  }

  /** Sem usuários do Spring: a autenticação é feita por {@code LoginService}. */
  @Bean
  UserDetailsService noUsers() {
    return username -> {
      throw new UsernameNotFoundException("sem usuários");
    };
  }

  @Bean
  IdempotencyFilter idempotencyFilter(IdempotencyService idempotency) {
    return new IdempotencyFilter(idempotency);
  }

  /** Evita que o filtro de idempotência também seja registrado fora da cadeia de segurança. */
  @Bean
  FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration(
      IdempotencyFilter filter) {
    var reg = new FilterRegistrationBean<>(filter);
    reg.setEnabled(false);
    return reg;
  }

  @Bean
  SecurityFilterChain filterChain(
      HttpSecurity http,
      SecurityContextRepository contextRepository,
      CsrfTokenRepository csrfRepository,
      IdempotencyFilter idempotencyFilter)
      throws Exception {
    http.csrf(csrf ->
            csrf.csrfTokenRepository(csrfRepository)
                // Token sem máscara: a SPA lê de GET /api/session e reenvia no cabeçalho.
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .securityContext(sc -> sc.securityContextRepository(contextRepository))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(HttpMethod.GET, "/api/health", "/api/session")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/session", "/api/setup")
                .permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/session")
                .permitAll()
                .requestMatchers("/api/**")
                .authenticated()
                // Estáticos da SPA não contêm dados privados.
                .anyRequest()
                .permitAll())
        .exceptionHandling(ex ->
            ex.authenticationEntryPoint((req, res, e) ->
                    json(res, 401, "unauthorized", "Entre com sua senha para continuar."))
                .accessDeniedHandler((req, res, e) ->
                    json(res, 403, "forbidden", "Requisição recusada. Recarregue a página.")))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .headers(h ->
            h.contentSecurityPolicy(csp ->
                    csp.policyDirectives(
                        "default-src 'self'; img-src 'self' data:; style-src 'self'"
                            + " 'unsafe-inline'; font-src 'self'; connect-src 'self';"
                            + " frame-ancestors 'none'; base-uri 'self'; form-action"
                            + " 'self'"))
                .referrerPolicy(
                    r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
        .addFilterAfter(idempotencyFilter, AuthorizationFilter.class);
    return http.build();
  }

  private static void json(HttpServletResponse res, int status, String code, String message)
      throws IOException {
    res.setStatus(status);
    res.setContentType("application/json;charset=UTF-8");
    res.getWriter()
        .write("{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}}");
  }
}
