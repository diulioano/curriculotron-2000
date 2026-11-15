package dev.curriculotron.shared.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.curriculotron.shared.persistence.WriteExecutor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/**
 * SQLite em arquivo no diretório de dados, modo WAL, chaves estrangeiras ligadas (D-03).
 *
 * <p>Leituras usam o pool; toda escrita passa por {@link WriteExecutor} para evitar {@code
 * SQLITE_BUSY}.
 */
@Configuration(proxyBeanMethods = false)
public class DataSourceConfig {

  @Bean(destroyMethod = "close")
  DataSource dataSource(CurriculotronProperties props) {
    try {
      Files.createDirectories(props.dataDir());
    } catch (IOException e) {
      throw new UncheckedIOException("Não foi possível criar o diretório de dados", e);
    }

    SQLiteConfig sqlite = new SQLiteConfig();
    sqlite.setJournalMode(SQLiteConfig.JournalMode.WAL);
    sqlite.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
    sqlite.enforceForeignKeys(true);
    sqlite.setBusyTimeout(5_000);

    SQLiteDataSource sqliteDs = new SQLiteDataSource(sqlite);
    sqliteDs.setUrl("jdbc:sqlite:" + props.databaseFile().toAbsolutePath());

    HikariConfig hikari = new HikariConfig();
    hikari.setPoolName("curriculotron-sqlite");
    hikari.setDataSource(sqliteDs);
    hikari.setMaximumPoolSize(4);
    hikari.setMinimumIdle(1);
    return new HikariDataSource(hikari);
  }
}
