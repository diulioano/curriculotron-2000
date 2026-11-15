package dev.curriculotron.auth.adapter.in.web;

import dev.curriculotron.auth.application.LoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sessão (contracts/panel-api.md §Sessão). */
@RestController
@RequestMapping("/api")
class SessionController {

  private final LoginService login;
  private final SecurityContextRepository contextRepository;
  private final CsrfTokenRepository csrfRepository;

  SessionController(
      LoginService login,
      SecurityContextRepository contextRepository,
      CsrfTokenRepository csrfRepository) {
    this.login = login;
    this.contextRepository = contextRepository;
    this.csrfRepository = csrfRepository;
  }

  @PostMapping("/setup")
  ResponseEntity<Void> setup(@RequestBody PasswordRequest body) {
    login.setup(body.password());
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @GetMapping("/session")
  SessionResponse session(HttpServletRequest request) {
    CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    return new SessionResponse(
        isAuthenticated(), !login.isSetupDone(), token == null ? null : token.getToken());
  }

  @PostMapping("/session")
  SessionResponse create(
      @RequestBody PasswordRequest body, HttpServletRequest request, HttpServletResponse response) {
    login.authenticate(body.password());

    // Nova sessão a cada login (fixação de sessão) e novo token CSRF.
    HttpSession old = request.getSession(false);
    if (old != null) {
      old.invalidate();
    }
    request.getSession(true);

    Authentication auth =
        UsernamePasswordAuthenticationToken.authenticated(
            "owner", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);
    contextRepository.saveContext(context, request, response);

    CsrfToken csrf = csrfRepository.generateToken(request);
    csrfRepository.saveToken(csrf, request, response);
    return new SessionResponse(true, false, csrf.getToken());
  }

  @DeleteMapping("/session")
  ResponseEntity<Void> delete(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
    return ResponseEntity.noContent().build();
  }

  private static boolean isAuthenticated() {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    return a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken);
  }
}
