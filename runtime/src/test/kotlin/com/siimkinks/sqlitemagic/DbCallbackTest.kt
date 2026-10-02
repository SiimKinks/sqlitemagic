package com.siimkinks.sqlitemagic

import android.content.Context
import android.content.res.AssetManager
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteProgram
import androidx.sqlite.db.SupportSQLiteQuery
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

private val VIEW_TYPE_PREDICATE = Regex(
  pattern = """\btype\s*=\s*'view'""",
  option = RegexOption.IGNORE_CASE
)

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
  fun `upgrade removes all planned views before scripts and recreates after skipped versions`() {
    val fixture = UpgradeFixture(
      files = mapOf(
        "Feature2.views" to "sub_\"quoted\"\n",
        "2.views" to "retired_view\nintermediate_table\n",
        "3.views" to "renamed_view\n",
        "Feature2.sql" to "SUBMODULE_SQL_V2\n",
        "2.sql" to "MAIN_SQL_V2\n",
        "3.sql" to "MAIN_SQL_V3\n"
      ),
      objectTypes = mapOf(
        """sub_"quoted"""" to "view",
        "retired_view" to "view",
        "intermediate_table" to "table",
        "renamed_view" to "view"
      ),
      submoduleNames = listOf("Feature")
    )

    fixture.upgrade(
      oldVersion = 1,
      newVersion = 3
    )

    assertThat(fixture.events)
      .containsExactly(
        "open:Feature2.views",
        """lookup:sub_"quoted"""",
        "exec:DROP VIEW IF EXISTS main.\"sub_\"\"quoted\"\"\"",
        "open:2.views",
        "lookup:retired_view",
        """exec:DROP VIEW IF EXISTS main."retired_view"""",
        "lookup:intermediate_table",
        "open:Feature3.views",
        "open:3.views",
        "lookup:renamed_view",
        """exec:DROP VIEW IF EXISTS main."renamed_view"""",
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
  fun `same-name trigger does not hide an owned view`() {
    val fixture = UpgradeFixture(
      files = mapOf("2.views" to "shared_name\n"),
      schemaRows = mapOf("shared_name" to listOf("trigger", "view"))
    )

    fixture.upgrade(
      oldVersion = 1,
      newVersion = 2
    )

    assertThat(fixture.events)
      .containsExactly(
        "open:2.views",
        "lookup:shared_name",
        """exec:DROP VIEW IF EXISTS main."shared_name"""",
        "open:2.sql",
        "recreate"
      )
      .inOrder()
    assertThat(fixture.lookupBindings.single())
      .containsExactly("shared_name")
    assertThat(VIEW_TYPE_PREDICATE.containsMatchIn(fixture.lookupSql.single()))
      .isTrue()
  }

  @Test
  fun `view plan open failure identifies asset and stops before scripts`() {
    val failure = IOException("cannot open plan")
    val fixture = UpgradeFixture(
      files = mapOf("2.sql" to "MUST_NOT_RUN\n"),
      openFailures = mapOf("2.views" to failure)
    )

    val exception = assertThrows(IllegalStateException::class.java) {
      fixture.upgrade(
        oldVersion = 1,
        newVersion = 2
      )
    }

    assertThat(exception)
      .hasMessageThat()
      .contains("2.views")
    assertThat(exception)
      .hasCauseThat()
      .isSameInstanceAs(failure)
    assertThat(fixture.events)
      .containsExactly("open:2.views")
  }

  @Test
  fun `view plan read failure identifies asset and line and stops before scripts`() {
    val failure = IOException("cannot read plan")
    val stream = object : InputStream() {
      private val bytes = "first_view\n".toByteArray()
      private var position = 0

      override fun read(): Int = when {
        position < bytes.size -> bytes[position++].toInt() and 0xff
        else -> throw failure
      }

      override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (position == bytes.size) throw failure
        val count = length.coerceAtMost(bytes.size - position)
        bytes.copyInto(
          destination = buffer,
          destinationOffset = offset,
          startIndex = position,
          endIndex = position + count
        )
        position += count
        return count
      }
    }
    val fixture = UpgradeFixture(
      files = mapOf("2.sql" to "MUST_NOT_RUN\n"),
      objectTypes = mapOf("first_view" to "view"),
      streams = mapOf("2.views" to stream)
    )

    val exception = assertThrows(IllegalStateException::class.java) {
      fixture.upgrade(
        oldVersion = 1,
        newVersion = 2
      )
    }

    assertThat(exception)
      .hasMessageThat()
      .contains("2.views")
    assertThat(exception)
      .hasMessageThat()
      .contains("line 2")
    assertThat(exception)
      .hasCauseThat()
      .isSameInstanceAs(failure)
    assertThat(fixture.events)
      .doesNotContain("open:2.sql")
    assertThat(fixture.events)
      .doesNotContain("recreate")
  }

  @Test
  fun `view drop failure identifies asset and line and stops before scripts`() {
    val failure = IllegalStateException("cannot drop view")
    val fixture = UpgradeFixture(
      files = mapOf(
        "2.views" to "first_view\nsecond_view\n",
        "2.sql" to "MUST_NOT_RUN\n"
      ),
      objectTypes = mapOf(
        "first_view" to "view",
        "second_view" to "view"
      ),
      executionFailures = mapOf("""DROP VIEW IF EXISTS main."second_view"""" to failure)
    )

    val exception = assertThrows(IllegalStateException::class.java) {
      fixture.upgrade(
        oldVersion = 1,
        newVersion = 2
      )
    }

    assertThat(exception)
      .hasMessageThat()
      .contains("2.views")
    assertThat(exception)
      .hasMessageThat()
      .contains("line 2")
    assertThat(exception)
      .hasCauseThat()
      .isSameInstanceAs(failure)
    assertThat(fixture.events)
      .doesNotContain("open:2.sql")
    assertThat(fixture.events)
      .doesNotContain("recreate")
  }

  @Test
  fun `existing sql failure message remains unchanged`() {
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
      .isEqualTo("Error executing migration script 2.sql at line 2")
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
  private val schemaRows: Map<String, List<String>> = emptyMap(),
  private val submoduleNames: List<String> = emptyList(),
  private val streams: Map<String, InputStream> = emptyMap(),
  private val openFailures: Map<String, IOException> = emptyMap(),
  private val executionFailures: Map<String, RuntimeException> = emptyMap()
) {
  val events = mutableListOf<String>()
  val lookupSql = mutableListOf<String>()
  val lookupBindings = mutableListOf<List<String>>()
  private val assets = mock<AssetManager>()
  private val context = mock<Context>()
  val db = mock<SupportSQLiteDatabase>(
    defaultAnswer = Answer { invocation ->
      when (invocation.method.name) {
        "query" -> query(invocation)
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
    val delegate = TestGeneratedDatabase(tableCount = 0)
    val generatedDatabase = object : GeneratedDatabase by delegate {
      override fun createTemporarySchema(db: SupportSQLiteDatabase) = delegate.createTemporarySchema(db)

      override fun getSubmoduleNames() = this@UpgradeFixture.submoduleNames
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

  fun upgrade(
    oldVersion: Int,
    newVersion: Int
  ) = callback.onUpgrade(db, oldVersion, newVersion)

  private fun query(invocation: InvocationOnMock): Cursor {
    val input = invocation.arguments.first()
    val sql = when (input) {
      is SupportSQLiteQuery -> input.sql
      is String -> input
      else -> error("Unsupported schema query: $input")
    }
    val args = when (input) {
      is SupportSQLiteQuery -> buildList {
        val program = mock<SupportSQLiteProgram>(
          defaultAnswer = Answer { binding ->
            if (binding.method.name == "bindString") add(binding.getArgument(1))
            null
          }
        )
        input.bindTo(program)
      }
      else -> (invocation.arguments.getOrNull(1) as? Array<*>)
        ?.filterIsInstance<String>()
        .orEmpty()
    }
    val normalizedSql = sql.lowercase()
    check(
      "main" in normalizedSql &&
          "name" in normalizedSql &&
          ("sqlite_master" in normalizedSql || "sqlite_schema" in normalizedSql)
    ) {
      "Schema lookup must target one exact main-schema name: $sql"
    }
    val name = (objectTypes.keys + schemaRows.keys)
      .distinct()
      .singleOrNull { candidate ->
        val escapedName = candidate.replace(
          oldValue = "\"",
          newValue = "\"\""
        )
        candidate in args || escapedName in sql
      }
      ?: error("Schema lookup did not target one planned name: $sql; args=$args")
    events += "lookup:$name"
    lookupSql += sql
    lookupBindings += args
    val filtersViews = VIEW_TYPE_PREDICATE.containsMatchIn(sql)
    val types = schemaRows[name] ?: listOf(objectTypes.getValue(name))
    val type = types.firstOrNull { !filtersViews || it == "view" }
    val cursor = mock<Cursor>()
    whenever(cursor.moveToFirst())
      .thenReturn(type != null)
    whenever(cursor.moveToNext())
      .thenReturn(type != null, false)
    if (type != null) {
      whenever(cursor.getString(any()))
        .thenReturn(type)
    }
    whenever(cursor.count)
      .thenReturn(if (type != null) 1 else 0)
    return cursor
  }
}
