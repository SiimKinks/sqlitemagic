package com.siimkinks.sqlitemagic.migration.export

import androidx.sqlite.db.SupportSQLiteDatabase
import com.siimkinks.sqlitemagic.migration.MigrationConfiguration
import com.siimkinks.sqlitemagic.migration.MigrationViewSchema
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaSnapshot
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/** Records generated schema creation without executing Android framework or SQLite operations. */
internal class SchemaRecordingDatabase : InvocationHandler {
  private var foreignKeysEnabled = false
  private var transactionActive = false
  private var transactionSuccessful = false
  private var transactionFailed = false
  val tableSql: List<String> field = mutableListOf()
  val views: List<MigrationViewSchema> field = mutableListOf()
  val indexSql: List<String> field = mutableListOf()
  val transactionEvents: List<String> field = mutableListOf()

  val database = Proxy.newProxyInstance(
    SupportSQLiteDatabase::class.java.classLoader,
    arrayOf(SupportSQLiteDatabase::class.java),
    this
  ) as SupportSQLiteDatabase

  override fun invoke(
    proxy: Any,
    method: Method,
    args: Array<out Any?>?
  ): Any? = when (method.name) {
    "toString" -> "SqliteMagic schema recording database"
    "hashCode" -> System.identityHashCode(proxy)
    "equals" -> proxy === args?.singleOrNull()
    "setForeignKeyConstraintsEnabled" -> {
      check(!transactionActive) { "Schema configuration must precede its transaction" }
      foreignKeysEnabled = args?.singleOrNull() as Boolean
      null
    }
    "beginTransaction" -> {
      check(!transactionActive) { "Nested schema transactions are unsupported" }
      transactionActive = true
      transactionSuccessful = false
      transactionEvents.add("begin")
      null
    }
    "setTransactionSuccessful" -> {
      check(transactionActive && !transactionSuccessful) { "No unfinished schema transaction to mark successful" }
      transactionSuccessful = true
      transactionEvents.add("successful")
      null
    }
    "endTransaction" -> {
      check(transactionActive) { "No schema transaction to end" }
      if (!transactionSuccessful) transactionFailed = true
      transactionActive = false
      transactionEvents.add("end")
      null
    }
    "execSQL" -> {
      require(method.parameterCount == 1) { "Generated schema SQL must not use bound arguments" }
      check(transactionActive) { "Generated schema SQL must execute inside its schema transaction" }
      recordSql(args?.singleOrNull() as String)
      null
    }
    else -> error("Unsupported generated schema database operation ${method.name}")
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
    val OPTIONS = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    val TABLE = Regex(pattern = "\\s*CREATE\\s+TABLE\\s+.+", options = OPTIONS)
    val VIEW = Regex(pattern = "\\s*CREATE\\s+VIEW\\s+.+", options = OPTIONS)
    val INDEX = Regex(pattern = "\\s*CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+.+", options = OPTIONS)
  }
}

/** Parses only the CREATE VIEW header emitted by the generated renderer, retaining defining SQL verbatim. */
private fun parseGeneratedViewSql(sql: String): MigrationViewSchema {
  val prefix = Regex(
    pattern = "^\\s*CREATE\\s+VIEW\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?",
    option = RegexOption.IGNORE_CASE
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
    pattern = "\\s+AS\\s+\\S.*",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
  )
  require(definition.matches(sql.substring(position))) { "Malformed generated view definition: $sql" }
  return MigrationViewSchema(
    name = name,
    createSql = sql
  )
}
