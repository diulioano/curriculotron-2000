package dev.curriculotron.shared.web;

import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

/** Arquivo existente → ele; rota da SPA (sem extensão) → {@code index.html}; resto → 404. */
class SpaResourceResolver extends PathResourceResolver {

  @Override
  protected Resource getResource(String path, Resource location) throws IOException {
    if (path.startsWith("api/") || path.equals("api")) {
      return null;
    }
    Resource requested = location.createRelative(path);
    if (requested.exists() && requested.isReadable()) {
      return requested;
    }
    if (path.contains(".")) {
      return null;
    }
    Resource index = new ClassPathResource("/static/index.html");
    return index.exists() ? index : null;
  }
}
