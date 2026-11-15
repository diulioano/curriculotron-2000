package dev.curriculotron.shared.persistence;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executor único de escrita (D-03): serializa todas as transações de escrita em uma thread, o que
 * evita {@code SQLITE_BUSY} e torna verificações do tipo "lê e grava" atômicas.
 *
 * <p>Chamadas feitas de dentro da própria thread de escrita rodam diretamente (reentrância), na
 * transação já aberta. Os serviços de aplicação usam este executor; adaptadores não.
 */
@Component
public class WriteExecutor implements AutoCloseable {

  private static final String THREAD_NAME = "curriculotron-writer";

  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(r -> Thread.ofPlatform().name(THREAD_NAME).unstarted(r));
  private final TransactionTemplate tx;

  public WriteExecutor(PlatformTransactionManager txManager) {
    this.tx = new TransactionTemplate(txManager);
  }

  public <T> T write(Supplier<T> work) {
    if (Thread.currentThread().getName().equals(THREAD_NAME)) {
      return work.get();
    }
    Future<T> future = executor.submit(() -> tx.execute(status -> work.get()));
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Escrita interrompida", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RuntimeException re) {
        throw re;
      }
      if (e.getCause() instanceof Error err) {
        throw err;
      }
      throw new IllegalStateException(e.getCause());
    }
  }

  public void run(Runnable work) {
    write(() -> {
      work.run();
      return null;
    });
  }

  @Override
  public void close() {
    executor.shutdown();
  }
}
