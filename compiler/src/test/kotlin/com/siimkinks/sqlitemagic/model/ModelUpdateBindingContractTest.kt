package com.siimkinks.sqlitemagic.model

import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.isOk
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ModelUpdateBindingContractTest {
  private val compiler = SqliteMagicCompiler(::modelProcessingSteps)

  @Test
  fun `generated multi-key binders preserve ordered evaluation and typed binding`() {
    val compilation = compiler
      .compile(
        SourceFile.kotlin(
          name = "UpdateBindingRuntime.kt",
          contents = """
            package $PACKAGE

            import androidx.sqlite.db.SupportSQLiteStatement
            import com.siimkinks.sqlitemagic.BindingNoIdTable
            import com.siimkinks.sqlitemagic.BindingOneUniqueTable
            import com.siimkinks.sqlitemagic.BindingRecordTable
            import com.siimkinks.sqlitemagic.SqliteMagic_BindingNoId_Dao
            import com.siimkinks.sqlitemagic.SqliteMagic_BindingOneUnique_Dao
            import com.siimkinks.sqlitemagic.SqliteMagic_BindingRecord_Dao
            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.Unique
            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue
            import java.lang.reflect.Proxy

            object BindingTrace {
              val events = mutableListOf<String>()
            }

            data class BindingToken(val text: String)

            object BindingTokenTransformer {
              @ObjectToDbValue
              fun serialize(value: BindingToken): String {
                BindingTrace.events.add("transform:" + value.text)
                return value.text
              }

              @DbValueToObject
              fun deserialize(value: String) = BindingToken(value)
            }

            class BindingDetails {
              var leaf: String? = "leaf"
                get() {
                  BindingTrace.events.add("get:leaf")
                  return field
                }
              var extra: Long = 61L
                get() {
                  BindingTrace.events.add("get:extra")
                  return field
                }
            }

            @Table
            class BindingTarget {
              @Id var id: Long? = 41L
                get() {
                  BindingTrace.events.add("get:targetId")
                  return field
                }
            }

            @Table
            class BindingRecord {
              @Id var id: Long? = 17L
                get() {
                  BindingTrace.events.add("get:id")
                  return field
                }
              @Unique var key: String = "alpha"
                get() {
                  BindingTrace.events.add("get:key")
                  return field
                }
              var count: Int = 23
                get() {
                  BindingTrace.events.add("get:count")
                  return field
                }
              @Unique var token: BindingToken = BindingToken("beta")
                get() {
                  BindingTrace.events.add("get:token")
                  return field
                }
              var ratio: Float = 1.5f
                get() {
                  BindingTrace.events.add("get:ratio")
                  return field
                }
              var score: Double = 2.25
                get() {
                  BindingTrace.events.add("get:score")
                  return field
                }
              var bytes: ByteArray = byteArrayOf(3, 4)
                get() {
                  BindingTrace.events.add("get:bytes")
                  return field
                }
              var optional: String? = "optional"
                get() {
                  BindingTrace.events.add("get:optional")
                  return field
                }
              @Embedded var details: BindingDetails? = BindingDetails()
                get() {
                  BindingTrace.events.add("get:details")
                  return field
                }
              @Column(handleRecursively = false) var target: BindingTarget? = BindingTarget()
                get() {
                  BindingTrace.events.add("get:target")
                  return field
                }
              var tail: Long = 59L
                get() {
                  BindingTrace.events.add("get:tail")
                  return field
                }
            }

            @Table
            data class BindingNoId(
              @Unique val first: String = "first",
              val count: Int = 7,
              @Unique val second: Long = 29L,
              val note: String? = null
            )

            @Table
            data class BindingOneUnique(
              @Id val id: Long = 13L,
              @Unique val key: String = "only",
              val count: Int = 31
            )

            object UpdateBindingDriver {
              @JvmStatic
              fun run(label: String): List<String> {
                val entity = BindingRecord()
                when (label.substringAfter(':')) {
                  "nulls" -> {
                    entity.optional = null
                    entity.details = null
                    entity.target = null
                  }
                  "leafNull" -> entity.details?.leaf = null
                  "nullId" -> entity.id = null
                  "badRelationship" -> entity.target?.id = null
                }
                BindingTrace.events.clear()
                val statement = Proxy.newProxyInstance(
                  SupportSQLiteStatement::class.java.classLoader,
                  arrayOf(SupportSQLiteStatement::class.java)
                ) { _, method, arguments ->
                  when (method.name) {
                    "clearBindings" -> BindingTrace.events.add("clear")
                    "bindNull" -> BindingTrace.events.add("bindNull:" + arguments!![0])
                    "bindLong", "bindDouble", "bindString", "bindBlob" -> {
                      val value = arguments!![1]
                      val text = when (value) {
                        is ByteArray -> value.joinToString(",")
                        else -> value.toString()
                      }
                      BindingTrace.events.add(method.name + ":" + arguments[0] + ":" + text)
                    }
                    else -> error("Unexpected statement method: " + method.name)
                  }
                  null
                } as SupportSQLiteStatement
                try {
                  when (label.substringBefore(':')) {
                    "oneUniqueId" -> SqliteMagic_BindingOneUnique_Dao.bindToUpdateStatement(
                      statement = statement,
                      entity = BindingOneUnique(),
                      columnName = "id"
                    )
                    "oneUniqueKey" -> SqliteMagic_BindingOneUnique_Dao.bindToUpdateStatement(
                      statement = statement,
                      entity = BindingOneUnique(),
                      columnName = "key"
                    )
                    "noIdFirst" -> SqliteMagic_BindingNoId_Dao.bindToUpdateStatement(
                      statement = statement,
                      entity = BindingNoId(),
                      columnName = "first"
                    )
                    "noIdSecond" -> SqliteMagic_BindingNoId_Dao.bindToUpdateStatement(
                      statement = statement,
                      entity = BindingNoId(),
                      columnName = "second"
                    )
                    else -> {
                      val selectedColumn = when (label.substringBefore(':')) {
                        "id", "idAlias" -> "id"
                        "key", "keyAlias" -> "key"
                        "token", "tokenAlias" -> "token"
                        else -> "count"
                      }
                      SqliteMagic_BindingRecord_Dao.bindToUpdateStatement(
                        statement = statement,
                        entity = entity,
                        columnName = selectedColumn
                      )
                    }
                  }
                } catch (failure: Exception) {
                  BindingTrace.events.add("throw:" + failure.javaClass.simpleName + ":" + failure.message)
                }
                return BindingTrace.events.toList()
              }
            }
          """
        )
      )
      .isOk()
    val driver = compilation
      .loadClass("$PACKAGE.UpdateBindingDriver")
      .getMethod("run", String::class.java)

    val columns = listOf(
      BindingColumn(
        name = "key",
        reads = listOf("get:key"),
        method = "bindString",
        value = "alpha"
      ),
      BindingColumn(
        name = "count",
        reads = listOf("get:count"),
        method = "bindLong",
        value = "23"
      ),
      BindingColumn(
        name = "token",
        reads = listOf("get:token", "transform:beta"),
        method = "bindString",
        value = "beta"
      ),
      BindingColumn(
        name = "ratio",
        reads = listOf("get:ratio"),
        method = "bindDouble",
        value = "1.5"
      ),
      BindingColumn(
        name = "score",
        reads = listOf("get:score"),
        method = "bindDouble",
        value = "2.25"
      ),
      BindingColumn(
        name = "bytes",
        reads = listOf("get:bytes"),
        method = "bindBlob",
        value = "3,4"
      ),
      BindingColumn(
        name = "optional",
        reads = listOf("get:optional"),
        method = "bindString",
        value = "optional"
      ),
      BindingColumn(
        name = "details",
        reads = listOf("get:details", "get:leaf"),
        method = "bindString",
        value = "leaf"
      ),
      BindingColumn(
        name = "detailsExtra",
        reads = listOf("get:details", "get:extra"),
        method = "bindLong",
        value = "61"
      ),
      BindingColumn(
        name = "target",
        reads = listOf("get:target", "get:targetId"),
        method = "bindLong",
        value = "41"
      ),
      BindingColumn(
        name = "tail",
        reads = listOf("get:tail"),
        method = "bindLong",
        value = "59"
      )
    )
    val id = BindingColumn(
      name = "id",
      reads = listOf("get:id"),
      method = "bindLong",
      value = "17"
    )
    val byKey = selectLast(
      columns = columns,
      selected = "key"
    )
    val byToken = selectLast(
      columns = columns,
      selected = "token"
    )
    val cases = listOf(
      "id" to bindingTrace(columns + id),
      "id:nullId" to bindingTrace(
        columns + id.copy(
          method = "bindNull",
          value = null
        )
      ),
      "idAlias" to bindingTrace(columns + id),
      "keyAlias" to bindingTrace(byKey),
      "tokenAlias" to bindingTrace(byToken),
      "key" to bindingTrace(byKey),
      "token" to bindingTrace(byToken),
      "oneUniqueId" to listOf("clear", "bindString:1:only", "bindLong:2:31", "bindLong:3:13"),
      "oneUniqueKey" to listOf("clear", "bindLong:1:31", "bindString:2:only"),
      "noIdFirst" to listOf("clear", "bindLong:1:7", "bindLong:2:29", "bindNull:3", "bindString:4:first"),
      "noIdSecond" to listOf("clear", "bindString:1:first", "bindLong:2:7", "bindNull:3", "bindLong:4:29")
    ) + listOf("id", "key", "token").flatMap { selected ->
      val ordered = when (selected) {
        "id" -> columns + id
        else -> selectLast(
          columns = columns,
          selected = selected
        )
      }
      listOf(
        "$selected:nulls" to bindingTrace(ordered.map(::nullOptionalColumn)),
        "$selected:leafNull" to bindingTrace(ordered.map(::nullEmbeddedLeaf)),
        "$selected:badRelationship" to bindingTrace(ordered.takeWhile { it.name != "target" }) + listOf(
          "get:target",
          "get:targetId",
          """throw:OperationFailedException:Relationship "target" resolved to a NULL ID"""
        )
      )
    }
    cases.forEach { (label, expected) ->
      assertWithMessage(label)
        .that(driver.invoke(null, label))
        .isEqualTo(expected)
    }
  }

  private fun selectLast(
    columns: List<BindingColumn>,
    selected: String
  ) = columns.filterNot { it.name == selected } + columns.single { it.name == selected }

  private fun nullOptionalColumn(column: BindingColumn) = when (column.name) {
    "optional", "details", "detailsExtra", "target" -> column.copy(
      reads = listOf(column.reads.first()),
      method = "bindNull",
      value = null
    )
    else -> column
  }

  private fun nullEmbeddedLeaf(column: BindingColumn) = when (column.name) {
    "details" -> column.copy(
      method = "bindNull",
      value = null
    )
    else -> column
  }

  private fun bindingTrace(columns: List<BindingColumn>) = listOf("clear") + columns.flatMapIndexed { index, column ->
    column.reads + when (column.value) {
      null -> "${column.method}:${index + 1}"
      else -> "${column.method}:${index + 1}:${column.value}"
    }
  }

  private data class BindingColumn(
    val name: String,
    val reads: List<String>,
    val method: String,
    val value: String?
  )
}
