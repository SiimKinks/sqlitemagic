package com.siimkinks.sqlitemagic.migration.export

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.migration.MigrationConfiguration
import com.siimkinks.sqlitemagic.migration.MigrationViewSchema
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaSnapshot
import kotlin.text.RegexOption.DOT_MATCHES_ALL
import kotlin.text.RegexOption.IGNORE_CASE

internal class SchemaRecordingDatabase : SQLiteConnection {
  private var foreignKeysEnabled = false
  private var transactionActive = false
  private var transactionFailed = false
  val tableSql: List<String> field = mutableListOf()
  val views: List<MigrationViewSchema> field = mutableListOf()
  val indexSql: List<String> field = mutableListOf()
  val transactionEvents: List<String> field = mutableListOf()

  override fun prepare(sql: String): SQLiteStatement = RecordedStatement(sql)

  override fun inTransaction() = transactionActive

  override fun close() = Unit

  override fun toString() = "SqliteMagic schema recording database"

  private fun execute(sql: String) {
    when {
      FOREIGN_KEYS.matches(sql) -> {
        check(!transactionActive) { "Schema configuration must precede its transaction" }
        foreignKeysEnabled = true
      }
      BEGIN.matches(sql) -> {
        check(!transactionActive) { "Nested schema transactions are unsupported" }
        transactionActive = true
        transactionEvents.add("begin")
      }
      COMMIT.matches(sql) -> {
        check(transactionActive) { "No schema transaction to commit" }
        transactionActive = false
        transactionEvents.add("commit")
      }
      ROLLBACK.matches(sql) -> {
        check(transactionActive) { "No schema transaction to roll back" }
        transactionActive = false
        transactionFailed = true
        transactionEvents.add("rollback")
      }
      else -> {
        check(transactionActive) { "Generated schema SQL must execute inside its schema transaction" }
        recordSql(sql)
      }
    }
  }

  fun snapshot(
    databaseId: String,
    releaseVariant: String,
    version: Int,
    modules: List<String>
  ): ReleaseSchemaSnapshot {
    check(!transactionActive && !transactionFailed) {
      "Generated schema transaction did not complete successfully"
    }
    return ReleaseSchemaSnapshot(
      formatVersion = 1,
      databaseId = databaseId,
      releaseVariant = releaseVariant,
      version = version,
      modules = modules,
      configuration = MigrationConfiguration(foreignKeysEnabled),
      tableSql = tableSql.toList(),
      views = views.toList(),
      indexSql = indexSql.toList()
    )
  }

  private fun recordSql(sql: String) {
    when {
      TABLE.matches(sql) -> tableSql.add(sql)
      VIEW.matches(sql) -> views.add(parseGeneratedViewSql(sql))
      INDEX.matches(sql) -> indexSql.add(sql)
      else -> error("Unsupported persistent generated schema SQL: $sql")
    }
  }

  private companion object {
    val OPTIONS = setOf(IGNORE_CASE, DOT_MATCHES_ALL)
    val FOREIGN_KEYS = Regex(
      pattern = """\s*PRAGMA\s+foreign_keys\s*=\s*ON\s*""",
      options = OPTIONS
    )
    val BEGIN = Regex(
      pattern = """\s*BEGIN\b.*""",
      options = OPTIONS
    )
    val COMMIT = Regex(
      pattern = """\s*COMMIT\s*""",
      options = OPTIONS
    )
    val ROLLBACK = Regex(
      pattern = """\s*ROLLBACK\s*""",
      options = OPTIONS
    )
    val TABLE = Regex(
      pattern = """\s*CREATE\s+TABLE\s+.+""",
      options = OPTIONS
    )
    val VIEW = Regex(
      pattern = """\s*CREATE\s+VIEW\s+.+""",
      options = OPTIONS
    )
    val INDEX = Regex(
      pattern = """\s*CREATE\s+(?:UNIQUE\s+)?INDEX\s+.+""",
      options = OPTIONS
    )
  }

  private inner class RecordedStatement(private val sql: String) : SQLiteStatement {
    override fun step(): Boolean {
      execute(sql)
      return false
    }

    override fun reset() = Unit

    override fun clearBindings() = Unit

    override fun close() = Unit

    override fun bindBlob(index: Int, value: ByteArray) = boundArgument()

    override fun bindDouble(index: Int, value: Double) = boundArgument()

    override fun bindLong(index: Int, value: Long) = boundArgument()

    override fun bindText(index: Int, value: String) = boundArgument()

    override fun bindNull(index: Int) = boundArgument()

    override fun getBlob(index: Int) = unsupported()

    override fun getDouble(index: Int) = unsupported()

    override fun getLong(index: Int) = unsupported()

    override fun getText(index: Int) = unsupported()

    override fun isNull(index: Int) = unsupported()

    override fun getColumnCount() = 0

    override fun getColumnName(index: Int) = unsupported()

    override fun getColumnType(index: Int) = unsupported()

    private fun boundArgument(): Nothing =
      throw IllegalArgumentException("Generated schema SQL must not use bound arguments")

    private fun unsupported(): Nothing = error("Unsupported generated schema database operation on $sql")
  }
}

private fun parseGeneratedViewSql(sql: String): MigrationViewSchema {
  val prefix = Regex(
    pattern = """^\s*CREATE\s+VIEW\s+(?:IF\s+NOT\s+EXISTS\s+)?""",
    option = IGNORE_CASE
  ).find(sql) ?: error("Malformed generated view creation SQL: $sql")
  var position = prefix.range.last + 1
  val name = buildString {
    when {
      sql.getOrNull(position) == '"' -> {
        position++
        var closed = false
        while (position < sql.length) {
          val character = sql[position++]
          when {
            character != '"' -> append(character)
            sql.getOrNull(position) == '"' -> {
              append('"')
              position++
            }
            else -> {
              closed = true
              break
            }
          }
        }
        require(closed) { "Unterminated generated view identifier: $sql" }
      }
      else -> {
        while (position < sql.length && !sql[position].isWhitespace()) append(sql[position++])
        require(toString().matches(Regex("[A-Za-z_][A-Za-z_0-9]*"))) {
          "Unsupported generated view identifier: $sql"
        }
      }
    }
  }
  val definition = Regex(
    pattern = """\s+AS\s+\S.*""",
    options = setOf(IGNORE_CASE, DOT_MATCHES_ALL)
  )
  require(definition.matches(sql.substring(position))) { "Malformed generated view definition: $sql" }
  return MigrationViewSchema(
    name = name,
    createSql = sql
  )
}
