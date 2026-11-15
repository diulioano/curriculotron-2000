package dev.curriculotron.salary.application;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** One lookup per profile in this installation's JVM, shared by manual and scheduled work. */
@Component
public class SalaryLookupCoordinator {
  public record Lookup(String task, CompletableFuture<String> result) {}

  public record Claim(Lookup lookup, boolean owner) {}

  private final Map<String, Lookup> active = new HashMap<>();

  public synchronized Claim reserve(String profile, Supplier<String> task) {
    var existing = active.get(profile);
    if (existing != null) return new Claim(existing, false);
    var lookup = new Lookup(task.get(), new CompletableFuture<>());
    active.put(profile, lookup);
    return new Claim(lookup, true);
  }

  public synchronized void release(String profile, Lookup lookup) {
    active.remove(profile, lookup);
  }
}
