package dev.curriculotron.shared.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HTTPS na porta 8443 com certificado PEM gerado por mkcert (D-06). O Spring Boot lê {@code
 * server.ssl.certificate} e {@code server.ssl.certificate-private-key}; esta classe só falha cedo,
 * antes do servidor subir, com uma mensagem útil quando os arquivos não existem.
 */
@Configuration(proxyBeanMethods = false)
public class TlsConfig {

  static void requireCertificates(boolean tlsEnabled, CurriculotronProperties props) {
    if (!tlsEnabled) {
      return;
    }
    Path cert = props.tls().certificate();
    Path key = props.tls().privateKey();
    if (!Files.isRegularFile(cert) || !Files.isRegularFile(key)) {
      throw new IllegalStateException(
          "Certificado HTTPS não encontrado. Rode scripts/setup-lan-https.ps1 ou defina"
              + " CURRICULOTRON_TLS_CERT e CURRICULOTRON_TLS_KEY. Procurado em: "
              + cert
              + " e "
              + key);
    }
  }

  @Bean
  WebServerFactoryCustomizer<ConfigurableWebServerFactory> tlsCertificateCheck(
      @Value("${server.ssl.enabled:false}") boolean tlsEnabled, CurriculotronProperties props) {
    return factory -> requireCertificates(tlsEnabled, props);
  }
}
