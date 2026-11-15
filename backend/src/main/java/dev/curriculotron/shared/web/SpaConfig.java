package dev.curriculotron.shared.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serve a SPA (frontend/dist copiado para {@code static/}). Rotas do cliente sem arquivo
 * correspondente devolvem {@code index.html}; {@code /api/**} nunca cai aqui.
 */
@Configuration(proxyBeanMethods = false)
public class SpaConfig implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/**")
        .addResourceLocations("classpath:/static/")
        .resourceChain(true)
        .addResolver(new SpaResourceResolver());
  }
}
