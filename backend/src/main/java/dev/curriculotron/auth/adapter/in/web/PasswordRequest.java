package dev.curriculotron.auth.adapter.in.web;

/** Corpo de {@code POST /api/setup} e {@code POST /api/session}. */
public record PasswordRequest(String password) {}
