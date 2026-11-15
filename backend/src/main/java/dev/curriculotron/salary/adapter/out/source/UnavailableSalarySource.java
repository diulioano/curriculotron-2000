package dev.curriculotron.salary.adapter.out.source;

import dev.curriculotron.salary.application.port.out.SalaryReferenceSource;
import dev.curriculotron.salary.domain.SalaryProfile;
import org.springframework.stereotype.Component;

/** Web collection is assisted: this process has no endpoint for the chat's web tool. */
@Component
public class UnavailableSalarySource implements SalaryReferenceSource {
  public Result lookup(SalaryProfile profile) {
    return new Result("not_configured", null);
  }
}
