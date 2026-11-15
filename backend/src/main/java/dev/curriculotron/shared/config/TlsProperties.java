package dev.curriculotron.shared.config;

import java.nio.file.Path;

/** {@code curriculotron.tls.*}: certificado PEM gerado por mkcert (D-06). */
public record TlsProperties(Path certificate, Path privateKey) {}
