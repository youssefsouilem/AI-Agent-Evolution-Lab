package tn.esprit.mission;

import static org.mockito.Mockito.*;

import java.sql.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import tn.esprit.mission.config.MySqlLargeTextMigration;

class MySqlLargeTextMigrationTest {

  @Test
  void nonMysqlDatabaseIsNeverAltered() throws Exception {
    var source = mock(DataSource.class);
    var connection = mock(Connection.class);
    var metadata = mock(DatabaseMetaData.class);
    when(source.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(metadata);
    when(metadata.getDatabaseProductName()).thenReturn("H2");
    new MySqlLargeTextMigration(source).run(null);
    verify(connection, never()).createStatement();
  }

  @Test
  void onlyLegacyTextIsWidenedAndSecondRunDoesNothing() throws Exception {
    var source = mock(DataSource.class);
    var connection = mock(Connection.class);
    var metadata = mock(DatabaseMetaData.class);
    var statement = mock(Statement.class);
    var widened = new AtomicBoolean();
    when(source.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(metadata);
    when(connection.getCatalog()).thenReturn("m3_test");
    when(metadata.getDatabaseProductName()).thenReturn("MySQL");
    when(connection.createStatement()).thenReturn(statement);
    when(
      metadata.getColumns(eq("m3_test"), isNull(), anyString(), anyString())
    ).thenAnswer(call -> {
      var columns = mock(ResultSet.class);
      when(columns.next()).thenReturn(true);
      boolean target =
        "mission_steps".equals(call.getArgument(2)) &&
        "input_mapping".equals(call.getArgument(3));
      when(columns.getString("TYPE_NAME")).thenReturn(
        target && !widened.get() ? "TEXT" : "LONGTEXT"
      );
      when(columns.getInt("NULLABLE")).thenReturn(
        DatabaseMetaData.columnNullable
      );
      return columns;
    });
    when(statement.executeUpdate(anyString())).thenAnswer(call -> {
      widened.set(true);
      return 0;
    });
    var migration = new MySqlLargeTextMigration(source);
    migration.run(null);
    migration.run(null);
    verify(statement, times(1)).executeUpdate(
      "ALTER TABLE `mission_steps` MODIFY COLUMN `input_mapping` LONGTEXT NULL"
    );
  }
}
