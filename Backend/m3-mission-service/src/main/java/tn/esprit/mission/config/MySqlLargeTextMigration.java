package tn.esprit.mission.config;

import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Narrow, idempotent widening for existing MySQL schemas that Hibernate does not resize. */
@Component
@Order(0)
public class MySqlLargeTextMigration implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(
    MySqlLargeTextMigration.class
  );
  private static final Map<String, List<String>> COLUMNS = Map.of(
    "mission_steps",
    List.of("input_mapping"),
    "mission_executions",
    List.of(
      "final_result",
      "error_message",
      "orchestrator_request",
      "orchestrator_state",
      "candidate_result",
      "monitoring_diagnostic",
      "monitoring_error"
    ),
    "mission_step_executions",
    List.of("output", "error_message"),
    "mission_step_attempts",
    List.of("output", "error_message")
  );
  private final DataSource dataSource;

  public MySqlLargeTextMigration(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public void run(ApplicationArguments arguments) throws Exception {
    try (var connection = dataSource.getConnection()) {
      var metadata = connection.getMetaData();
      String product = metadata
        .getDatabaseProductName()
        .toLowerCase(Locale.ROOT);
      if (!product.contains("mysql") && !product.contains("mariadb")) return;
      for (var table : COLUMNS.entrySet()) {
        for (String column : table.getValue()) {
          boolean widen = false;
          boolean nullable = true;
          try (
            var info = metadata.getColumns(
              connection.getCatalog(),
              null,
              table.getKey(),
              column
            )
          ) {
            if (info.next()) {
              String type = info
                .getString("TYPE_NAME")
                .toUpperCase(Locale.ROOT);
              widen = List.of("TINYTEXT", "TEXT", "MEDIUMTEXT").contains(type);
              nullable =
                info.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
            }
          }
          if (widen) {
            // Both identifiers come only from the fixed allowlist above, never from input.
            String ddl =
              "ALTER TABLE `" +
              table.getKey() +
              "` MODIFY COLUMN `" +
              column +
              "` LONGTEXT " +
              (nullable ? "NULL" : "NOT NULL");
            try (var statement = connection.createStatement()) {
              statement.executeUpdate(ddl);
            }
            log.info(
              "Widened M3 column {}.{} to LONGTEXT",
              table.getKey(),
              column
            );
          }
        }
      }
    }
  }
}
