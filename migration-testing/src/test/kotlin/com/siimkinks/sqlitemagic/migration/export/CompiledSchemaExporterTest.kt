package com.siimkinks.sqlitemagic.migration.export

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.GeneratedDatabase
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import com.siimkinks.sqlitemagic.migration.MigrationConfiguration
import com.siimkinks.sqlitemagic.migration.MigrationViewSchema
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaSnapshot
import org.junit.Assert.assertThrows
import org.junit.Test

internal class CompiledSchemaExporterTest {
  @Test
  fun `exports the selected manager and restores library state`() {
    val previousLogging = SqliteMagic.loggingEnabled
    val schema = CompiledSchemaExporter.export(
      generatedDatabaseClassName = ExportFixtureDatabase::class.java.name,
      databaseId = "main",
      releaseVariant = "paidRelease"
    )
    assertThat(schema).isEqualTo(
      ReleaseSchemaSnapshot(
        formatVersion = 1,
        databaseId = "main",
        releaseVariant = "paidRelease",
        version = 7,
        modules = listOf("Feature", "main"),
        configuration = MigrationConfiguration(foreignKeysEnabled = true),
        tableSql = listOf("CREATE TABLE books (id INTEGER PRIMARY KEY)"),
        views = listOf(
          MigrationViewSchema(
            name = "titles",
            createSql = "CREATE VIEW titles AS SELECT id FROM books"
          )
        ),
        indexSql = emptyList()
      )
    )
    assertThat(SqliteMagic.loggingEnabled).isEqualTo(previousLogging)
    assertThat(ExportRuntimeState.defaultConnection).isNull()
  }

  @Test
  fun `initialization failure preserves the cause and selected declaration`() {
    val failure = assertThrows(IllegalStateException::class.java) {
      CompiledSchemaExporter.export(
        generatedDatabaseClassName = FailingExportFixtureDatabase::class.java.name,
        databaseId = "main",
        releaseVariant = "release"
      )
    }
    assertThat(failure).hasMessageThat()
      .contains(FailingExportFixtureDatabase::class.java.name)
    assertThat(failure.cause).isInstanceOf(UnsupportedOperationException::class.java)
    assertThat(failure.cause).hasMessageThat()
      .contains("query owner requires application services")
    assertThat(ExportRuntimeState.defaultConnection).isNull()
  }

  @Test
  fun `fatal constructor error propagates unwrapped and restores library state`() {
    val previousLogging = SqliteMagic.loggingEnabled
    assertThrows(ExportFatalVmError::class.java) {
      CompiledSchemaExporter.export(
        generatedDatabaseClassName = FatalVmExportFixtureDatabase::class.java.name,
        databaseId = "main",
        releaseVariant = "release"
      )
    }
    assertThat(SqliteMagic.loggingEnabled).isEqualTo(previousLogging)
  }

  @Test
  fun `linkage failure preserves the cause and release context`() {
    val failure = assertThrows(IllegalStateException::class.java) {
      CompiledSchemaExporter.export(
        generatedDatabaseClassName = LinkageExportFixtureDatabase::class.java.name,
        databaseId = "main",
        releaseVariant = "paidRelease"
      )
    }
    assertThat(failure).hasMessageThat()
      .contains("paidRelease")
    assertThat(failure.cause).isInstanceOf(NoClassDefFoundError::class.java)
  }
}

/** Exporter contract fixture; generated-source JVM feasibility is covered by consumer integration. */
class ExportFixtureDatabase : GeneratedDatabase {
  override val dbName = "books"
  override val dbVersion = 7
  override val submoduleNames = arrayOf("Feature")
  override val isDebug = false

  override fun configureDatabase(db: SupportSQLiteDatabase) = db.setForeignKeyConstraintsEnabled(true)

  override fun createSchema(db: SupportSQLiteDatabase) {
    check(ExportRuntimeState.defaultConnection == null)
    check(!SqliteMagic.loggingEnabled)
    db.beginTransaction()
    try {
      db.execSQL("CREATE TABLE books (id INTEGER PRIMARY KEY)")
      db.execSQL("CREATE VIEW titles AS SELECT id FROM books")
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  override fun clearData(db: SupportSQLiteDatabase) = MutableScatterSet<String>()
  override fun migrateViews(db: SupportSQLiteDatabase) = Unit
  override fun getNrOfTables(moduleName: String?) = 1
}

class FailingExportFixtureDatabase : GeneratedDatabase by ExportFixtureDatabase() {
  init {
    throw UnsupportedOperationException("query owner requires application services")
  }
}

class ExportFatalVmError : VirtualMachineError("fatal export initialization")

class FatalVmExportFixtureDatabase : GeneratedDatabase by ExportFixtureDatabase() {
  init {
    throw ExportFatalVmError()
  }
}

class LinkageExportFixtureDatabase : GeneratedDatabase by ExportFixtureDatabase() {
  init {
    throw NoClassDefFoundError("query provider dependency missing")
  }
}
