package com.siimkinks.sqlitemagic.migration.export

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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
  fun `initialization and linkage failures preserve the cause, selected declaration and release context`() {
    data class Outcome(
      val message: String?,
      val cause: Class<out Throwable>?,
      val causeMessage: String?,
      val defaultConnection: Any? = null
    )

    data class Case(
      val label: String,
      val generatedDatabase: Class<out GeneratedDatabase>,
      val releaseVariant: String,
      val expected: Outcome
    )

    val guidance =
      "Generated schema and query owners must initialize without application services or Android execution."
    listOf(
      Case(
        label = "initialization failure",
        generatedDatabase = FailingExportFixtureDatabase::class.java,
        releaseVariant = "release",
        expected = Outcome(
          message = "Cannot export selected release 'release' from " +
              "${FailingExportFixtureDatabase::class.java.name}. $guidance",
          cause = UnsupportedOperationException::class.java,
          causeMessage = "query owner requires application services"
        )
      ),
      Case(
        label = "linkage failure",
        generatedDatabase = LinkageExportFixtureDatabase::class.java,
        releaseVariant = "paidRelease",
        expected = Outcome(
          message = "Cannot export selected release 'paidRelease' from " +
              "${LinkageExportFixtureDatabase::class.java.name}. $guidance",
          cause = NoClassDefFoundError::class.java,
          causeMessage = "query provider dependency missing"
        )
      )
    ).forEach { case ->
      val failure = assertThrows(IllegalStateException::class.java) {
        CompiledSchemaExporter.export(
          generatedDatabaseClassName = case.generatedDatabase.name,
          databaseId = "main",
          releaseVariant = case.releaseVariant
        )
      }

      assertWithMessage(case.label)
        .that(
          Outcome(
            message = failure.message,
            cause = failure.cause?.javaClass,
            causeMessage = failure.cause?.message,
            defaultConnection = ExportRuntimeState.defaultConnection
          )
        )
        .isEqualTo(case.expected)
    }
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
}

internal class ExportFixtureDatabase : GeneratedDatabase {
  override val dbName = "books"
  override val dbVersion = 7
  override val submoduleNames = arrayOf("Feature")
  override val isDebug = false

  override fun applySchemaPragmas(connection: SQLiteConnection) = connection.execSQL("PRAGMA foreign_keys = ON")

  override fun createSchema(connection: SQLiteConnection) {
    check(ExportRuntimeState.defaultConnection == null)
    check(!SqliteMagic.loggingEnabled)
    connection.execSQL("BEGIN IMMEDIATE")
    try {
      connection.execSQL("CREATE TABLE books (id INTEGER PRIMARY KEY)")
      connection.execSQL("CREATE VIEW titles AS SELECT id FROM books")
      connection.execSQL("COMMIT")
    } catch (failure: Throwable) {
      connection.execSQL("ROLLBACK")
      throw failure
    }
  }

  override fun clearData(connection: SQLiteConnection) = MutableScatterSet<String>()
  override fun migrateViews(connection: SQLiteConnection) = Unit
  override fun getNrOfTables(moduleName: String?) = 1
}

internal class FailingExportFixtureDatabase : GeneratedDatabase by ExportFixtureDatabase() {
  init {
    throw UnsupportedOperationException("query owner requires application services")
  }
}

internal class ExportFatalVmError : VirtualMachineError("fatal export initialization")

internal class FatalVmExportFixtureDatabase : GeneratedDatabase by ExportFixtureDatabase() {
  init {
    throw ExportFatalVmError()
  }
}

internal class LinkageExportFixtureDatabase : GeneratedDatabase by ExportFixtureDatabase() {
  init {
    throw NoClassDefFoundError("query provider dependency missing")
  }
}
