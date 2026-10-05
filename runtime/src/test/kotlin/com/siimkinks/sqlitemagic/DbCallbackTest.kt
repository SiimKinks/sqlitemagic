package com.siimkinks.sqlitemagic

import android.content.Context
import android.content.res.AssetManager
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Answer
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

internal class DbCallbackTest {
  @Test
  fun `downgrade forwards the database and versions to a custom callback`() {
    val db = mock<SupportSQLiteDatabase>()
    val invocations = mutableListOf<Triple<SupportSQLiteDatabase, Int, Int>>()
    val callback = DbCallback(
      context = mock<Context>(),
      version = 2,
      database = mock<GeneratedDatabase>(),
      downgrader = { database, oldVersion, newVersion ->
        invocations += Triple(
          first = database,
          second = oldVersion,
          third = newVersion
        )
      }
    )

    callback.onDowngrade(
      db = db,
      oldVersion = 7,
      newVersion = 2
    )

    assertThat(invocations)
      .containsExactly(
        Triple(
          first = db,
          second = 7,
          third = 2
        )
      )
  }

  @Test
  fun `open creates the temporary schema`() {
    val database = RecordingDatabase()
    val generatedDatabase = TestGeneratedDatabase(tableCount = 1)
    val callback = DbCallback(
      context = mock<Context>(),
      version = 1,
      database = generatedDatabase,
      downgrader = { _, _, _ -> }
    )

    callback.onOpen(database)

    assertThat(generatedDatabase.temporarySchemaDatabases)
      .containsExactly(database)
  }

  @Test
  fun `upgrade enumerates and closes views before all scripts and recreates once`() {
    val fixture = UpgradeFixture(
      files = mapOf(
        "2.views" to "IGNORED",
        "Feature2.sql" to "SUBMODULE_SQL_V2\n",
        "2.sql" to "MAIN_SQL_V2\n",
        "3.sql" to "MAIN_SQL_V3\n"
      ),
      objectTypes = mapOf(
        "sub_\"quoted\"" to "view",
        "retired_view" to "view",
        "intermediate_table" to "table"
      ),
      submoduleNames = listOf("Feature")
    )
    fixture.upgrade(
      oldVersion = 1,
      newVersion = 3
    )
    assertThat(fixture.events)
      .containsExactly(
        "lookup",
        "close",
        "exec:DROP VIEW IF EXISTS main.\"sub_\"\"quoted\"\"\"",
        "exec:DROP VIEW IF EXISTS main.\"retired_view\"",
        "open:Feature2.sql",
        "exec:SUBMODULE_SQL_V2",
        "open:2.sql",
        "exec:MAIN_SQL_V2",
        "open:Feature3.sql",
        "open:3.sql",
        "exec:MAIN_SQL_V3",
        "recreate"
      )
      .inOrder()
  }

  @Test
  fun `configure and create use generated database without reading metadata`() {
    val fixture = UpgradeFixture(files = mapOf(MANIFEST_ASSET to "broken metadata"))
    fixture.configure()
    fixture.create()
    assertThat(fixture.events)
      .containsExactly("configure:generated", "create:generated")
      .inOrder()
  }

  @Test
  fun `view drop failure stops before SQL and recreation`() {
    val failure = IllegalStateException("cannot drop view")
    val fixture = UpgradeFixture(
      files = mapOf("2.sql" to "MUST_NOT_RUN"),
      objectTypes = mapOf("retired_view" to "view"),
      executionFailures = mapOf("DROP VIEW IF EXISTS main.\"retired_view\"" to failure)
    )
    val exception = assertThrows(IllegalStateException::class.java) {
      fixture.upgrade(
        oldVersion = 1,
        newVersion = 2
      )
    }
    assertThat(exception.message).contains("retired_view")
    assertThat(exception.cause).isSameInstanceAs(failure)
    assertThat(fixture.events)
      .containsExactly("lookup", "close", "exec:DROP VIEW IF EXISTS main.\"retired_view\"")
      .inOrder()
  }

  @Test
  fun `upgrade ignores blank and full line comments while retaining physical line diagnostics`() {
    val failure = IllegalStateException("bad SQL")
    val fixture = UpgradeFixture(
      files = mapOf("2.sql" to "\n -- comment\nFIRST_SQL\n\nSECOND_SQL\n"),
      executionFailures = mapOf("SECOND_SQL" to failure)
    )
    val exception = assertThrows(IllegalStateException::class.java) {
      fixture.upgrade(
        oldVersion = 1,
        newVersion = 2
      )
    }
    assertThat(fixture.events)
      .containsExactly("lookup", "close", "open:2.sql", "exec:FIRST_SQL", "exec:SECOND_SQL")
      .inOrder()
    assertThat(exception.message).contains("line 5 (statement 2")
    assertThat(exception.cause).isSameInstanceAs(failure)
  }

  @Test
  fun `sql failure identifies asset line statement and migration range`() {
    val failure = IllegalStateException("bad SQL")
    val fixture = UpgradeFixture(
      files = mapOf("2.sql" to "FIRST_SQL\nSECOND_SQL\n"),
      executionFailures = mapOf("SECOND_SQL" to failure)
    )

    val exception = assertThrows(IllegalStateException::class.java) {
      fixture.upgrade(
        oldVersion = 1,
        newVersion = 2
      )
    }

    assertThat(exception)
      .hasMessageThat()
      .isEqualTo(
        "Error executing migration script 2.sql at line 2 " +
            "(statement 2, migration 1 -> 2, step 2): SECOND_SQL"
      )
    assertThat(exception)
      .hasCauseThat()
      .isSameInstanceAs(failure)
    assertThat(fixture.events)
      .doesNotContain("recreate")
  }
}

private class UpgradeFixture(
  private val files: Map<String, String>,
  private val objectTypes: Map<String, String> = emptyMap(),
  private val submoduleNames: List<String> = emptyList(),
  private val streams: Map<String, InputStream> = emptyMap(),
  private val openFailures: Map<String, IOException> = emptyMap(),
  private val executionFailures: Map<String, RuntimeException> = emptyMap(),
  private val generatedVersion: Int = 1
) {
  val events = mutableListOf<String>()
  private val assets = mock<AssetManager>()
  private val context = mock<Context>()
  val db = mock<SupportSQLiteDatabase>(
    defaultAnswer = Answer { invocation ->
      when (invocation.method.name) {
        "query" -> query(invocation)
        "setForeignKeyConstraintsEnabled" -> {
          events += "configure:${invocation.getArgument<Boolean>(0)}"
          null
        }
        "execSQL" -> {
          val sql = invocation.getArgument<String>(0)
          events += "exec:$sql"
          executionFailures[sql]?.let { throw it }
          null
        }
        else -> null
      }
    }
  )
  private val callback: DbCallback

  init {
    whenever(context.assets)
      .thenReturn(assets)
    whenever(assets.open(any()))
      .thenAnswer { invocation ->
        val fileName = invocation.getArgument<String>(0)
        events += "open:$fileName"
        openFailures[fileName]?.let { throw it }
        streams[fileName] ?: files[fileName]?.byteInputStream() ?: throw FileNotFoundException(fileName)
      }
    val generatedDatabase = object : GeneratedDatabase by TestGeneratedDatabase(tableCount = 0) {
      override val dbVersion = generatedVersion

      override fun createSchema(db: SupportSQLiteDatabase) {
        events += "create:generated"
      }

      override fun configureDatabase(db: SupportSQLiteDatabase) {
        events += "configure:generated"
      }

      override val submoduleNames
        get() = this@UpgradeFixture.submoduleNames
          .takeIf(List<String>::isNotEmpty)
          ?.toTypedArray()

      override fun migrateViews(db: SupportSQLiteDatabase) {
        events += "recreate"
      }
    }
    callback = DbCallback(
      context = context,
      version = 3,
      database = generatedDatabase,
      downgrader = { _, _, _ -> }
    )
  }

  fun configure() = callback.onConfigure(db)

  fun create() = callback.onCreate(db)

  fun upgrade(
    oldVersion: Int,
    newVersion: Int
  ) = callback.onUpgrade(
    db = db,
    oldVersion = oldVersion,
    newVersion = newVersion
  )

  private fun query(invocation: InvocationOnMock): Cursor {
    val sql = invocation.getArgument<String>(0)
    check(sql == "SELECT name FROM main.sqlite_master WHERE type = 'view'")
    events += "lookup"
    val names = objectTypes.filterValues { it == "view" }
      .keys
      .toList()
    var position = -1
    return mock<Cursor>(
      defaultAnswer = Answer { call ->
        when (call.method.name) {
          "moveToNext" -> ++position < names.size
          "getString" -> names[position]
          "close" -> {
            events += "close"
            null
          }
          else -> null
        }
      }
    )
  }
}

private const val MANIFEST_ASSET = "sqlitemagic/migrations/manifest.json"
