package com.siimkinks.sqlitemagic.model

import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ModelUpdateBindingContractTest : ProcessingStepsTest {
  override val processingSteps = ::modelProcessingSteps

  @Test
  fun `generated multi-key binders preserve ordered evaluation and typed binding`() {
    val compilation = SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "UpdateBindingRuntime.kt",
          contents = """
            package $PACKAGE

            import androidx.sqlite.db.SupportSQLiteStatement
            import com.siimkinks.sqlitemagic.BindingNoIdTable
            import com.siimkinks.sqlitemagic.BindingRecordTable
            import com.siimkinks.sqlitemagic.BindingOneUniqueTable
            import com.siimkinks.sqlitemagic.Column as SqlColumn
            import com.siimkinks.sqlitemagic.NotNullable
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
                      byColumn = BindingOneUniqueTable.BINDING_ONE_UNIQUE.ID
                    )
                    "oneUniqueKey" -> SqliteMagic_BindingOneUnique_Dao.bindToUpdateStatement(
                      statement = statement,
                      entity = BindingOneUnique(),
                      byColumn = BindingOneUniqueTable.BINDING_ONE_UNIQUE.KEY
                    )
                    "noIdFirst" -> SqliteMagic_BindingNoId_Dao.bindToUpdateStatement(
                      statement = statement,
                      entity = BindingNoId(),
                      byColumn = BindingNoIdTable.BINDING_NO_ID.FIRST
                    )
                    "noIdSecond" -> SqliteMagic_BindingNoId_Dao.bindToUpdateStatement(
                      statement = statement,
                      entity = BindingNoId(),
                      byColumn = BindingNoIdTable.BINDING_NO_ID.SECOND
                    )
                    else -> {
                      // The binder selector uses NotNullable even when the selected ID value may be null.
                      @Suppress("UNCHECKED_CAST")
                      val selectedColumn = when (label.substringBefore(':')) {
                        "id" -> BindingRecordTable.BINDING_RECORD.ID
                        "idAlias" -> BindingRecordTable.BINDING_RECORD.ID.`as`("aliased_id")
                        "keyAlias" -> BindingRecordTable.BINDING_RECORD.KEY.`as`("aliased_key")
                        "tokenAlias" -> BindingRecordTable.BINDING_RECORD.TOKEN.`as`("aliased_token")
                        "key" -> BindingRecordTable.BINDING_RECORD.KEY
                        "token" -> BindingRecordTable.BINDING_RECORD.TOKEN
                        else -> BindingRecordTable.BINDING_RECORD.COUNT
                      } as SqlColumn<*, *, *, BindingRecord, NotNullable>
                      SqliteMagic_BindingRecord_Dao.bindToUpdateStatement(
                        statement = statement,
                        entity = entity,
                        byColumn = selectedColumn
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
    val driver = (compilation.result as JvmCompilationResult)
      .classLoader
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
      BindingCase(
        label = "id",
        expected = bindingTrace(columns + id)
      ),
      BindingCase(
        label = "id:nullId",
        expected = bindingTrace(
          columns + id.copy(
            method = "bindNull",
            value = null
          )
        )
      ),
      BindingCase(
        label = "idAlias",
        expected = bindingTrace(columns + id)
      ),
      BindingCase(
        label = "keyAlias",
        expected = bindingTrace(byKey)
      ),
      BindingCase(
        label = "tokenAlias",
        expected = bindingTrace(byToken)
      ),
      BindingCase(
        label = "key",
        expected = bindingTrace(byKey)
      ),
      BindingCase(
        label = "token",
        expected = bindingTrace(byToken)
      ),
      BindingCase(
        label = "unknown",
        expected = listOf("clear", "throw:IllegalArgumentException:Column does not identify an entity property")
      ),
      BindingCase(
        label = "oneUniqueId",
        expected = listOf("clear", "bindString:1:only", "bindLong:2:31", "bindLong:3:13")
      ),
      BindingCase(
        label = "oneUniqueKey",
        expected = listOf("clear", "bindLong:1:31", "bindString:2:only")
      ),
      BindingCase(
        label = "noIdFirst",
        expected = listOf("clear", "bindLong:1:7", "bindLong:2:29", "bindNull:3", "bindString:4:first")
      ),
      BindingCase(
        label = "noIdSecond",
        expected = listOf("clear", "bindString:1:first", "bindLong:2:7", "bindNull:3", "bindLong:4:29")
      )
    ) + listOf("id", "key", "token").flatMap { selected ->
      val ordered = when (selected) {
        "id" -> columns + id
        else -> selectLast(
          columns = columns,
          selected = selected
        )
      }
      listOf(
        BindingCase(
          label = "$selected:nulls",
          expected = bindingTrace(ordered.map(::nullOptionalColumn))
        ),
        BindingCase(
          label = "$selected:leafNull",
          expected = bindingTrace(ordered.map(::nullEmbeddedLeaf))
        ),
        BindingCase(
          label = "$selected:badRelationship",
          expected = bindingTrace(ordered.takeWhile { it.name != "target" }) + listOf(
            "get:target",
            "get:targetId",
            "throw:OperationFailedException:Relationship \"target\" resolved to a NULL ID"
          )
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

  private data class BindingCase(
    val label: String,
    val expected: List<String>
  )
}
