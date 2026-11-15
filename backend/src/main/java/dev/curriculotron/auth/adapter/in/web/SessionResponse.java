package dev.curriculotron.auth.adapter.in.web;

/** Estado da sessão para a SPA (contracts/panel-api.md §Sessão). */
public record SessionResponse(
    boolean authenticated, boolean setupRequired, String csrfToken, String authMode) {
  public SessionResponse(boolean authenticated, boolean setupRequired, String csrfToken) {
    this(authenticated, setupRequired, csrfToken, "password");
  }
}
