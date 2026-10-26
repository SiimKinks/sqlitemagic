package com.siimkinks.sqlitemagic.runtime.contract.query

import android.database.Cursor
import android.database.Cursor.FIELD_TYPE_BLOB
import android.database.Cursor.FIELD_TYPE_FLOAT
import android.database.Cursor.FIELD_TYPE_INTEGER
import android.database.Cursor.FIELD_TYPE_NULL
import android.database.Cursor.FIELD_TYPE_STRING
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.LIST_PARAMETERIZED_TOKEN
import com.siimkinks.sqlitemagic.NULL
import com.siimkinks.sqlitemagic.OWNED_TOKEN
import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.Row2
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.ScalarStorageEntityTable.Companion.SCALAR_STORAGE_ENTITY
import com.siimkinks.sqlitemagic.SelectQuery
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SqlExpression
import com.siimkinks.sqlitemagic.SqlType
import com.siimkinks.sqlitemagic.SqlType.BOOLEAN
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.NUMBER
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.TransformerRuntimeEntityTable.Companion.TRANSFORMER_RUNTIME_ENTITY
import com.siimkinks.sqlitemagic.asValue
import com.siimkinks.sqlitemagic.expr
import com.siimkinks.sqlitemagic.fixture.model.OwnedToken
import com.siimkinks.sqlitemagic.fixture.model.ParameterizedToken
import com.siimkinks.sqlitemagic.fixture.model.ScalarStorageEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.readAs
import com.siimkinks.sqlitemagic.runtime.model.catalog.TransformerModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.defaultWritableDatabase
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import org.junit.Rule
import org.junit.Test

internal class ScalarSelectRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun generatedGroupsHavingAndPaginationCountFinalExactIntegerResults() {
    val database = defaultWritableDatabase()
    database.execSQL(
      "INSERT INTO scalar_storage_entity(long_value) VALUES " +
          "(9007199254740993),(9007199254740993),(9007199254740993)," +
          "(7),(7),(5),(5),(3),(-4),(-4)"
    )
    val storage = SCALAR_STORAGE_ENTITY AS "grouped storage"
    val grouped = SELECT.COLUMN(storage.LONG_VALUE)
      .FROM(storage)
      .WHERE(
        """"grouped storage"."long_value" > ?""".expr(BOOLEAN, 0L)
          .observes(storage)
      )
      .GROUP_BY(storage.LONG_VALUE)
      .HAVING(
        "count(*) >= ?".expr(BOOLEAN, 2L)
          .observes(storage)
      )
      .ORDER_BY(storage.LONG_VALUE.DESC())
    assertThat(grouped.execute()).isEqualTo(listOf(9007199254740993L, 7L, 5L))
    assertThat(
      grouped.takeFirst()
        .execute()
    ).isEqualTo(9007199254740993L)
    assertThat(
      grouped.count()
        .execute()
    ).isEqualTo(3L)
    val paginated = grouped.LIMIT(2)
      .OFFSET(1)
    assertThat(paginated.execute()).isEqualTo(listOf(7L, 5L))
    assertThat(
      paginated.count()
        .execute()
    ).isEqualTo(2L)
    assertThat(
      paginated.takeFirst()
        .execute()
    ).isEqualTo(7L)
  }

  @Test
  fun generatedFieldsKeepAuthoritativeDirectReadersForPhysicalNulls() {
    val database = defaultWritableDatabase()
    database.execSQL(
      "INSERT INTO scalar_storage_entity(long_value,nullable_long,byte_array,boxed_byte_array) " +
          "VALUES (NULL,NULL,NULL,NULL)"
    )
    database.execSQL("INSERT INTO query_composition_author(id,name) VALUES (1,NULL)")
    database.execSQL("INSERT INTO simple_mutable_entity(primitive_boolean,boxed_boolean) VALUES (NULL,NULL)")
    val requiredBoolean = SELECT.COLUMN(SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN)
      .FROM(SIMPLE_MUTABLE_ENTITY)

    assertThat(
      listOf(
        SELECT.COLUMN(SCALAR_STORAGE_ENTITY.LONG_VALUE)
          .FROM(SCALAR_STORAGE_ENTITY),
        SELECT.COLUMN(SCALAR_STORAGE_ENTITY.NULLABLE_LONG)
          .FROM(SCALAR_STORAGE_ENTITY),
        SELECT.COLUMN(SCALAR_STORAGE_ENTITY.BYTE_ARRAY)
          .FROM(SCALAR_STORAGE_ENTITY),
        SELECT.COLUMN(SCALAR_STORAGE_ENTITY.BOXED_BYTE_ARRAY)
          .FROM(SCALAR_STORAGE_ENTITY),
        SELECT.COLUMN(QUERY_COMPOSITION_AUTHOR.NAME)
          .FROM(QUERY_COMPOSITION_AUTHOR),
        requiredBoolean,
        SELECT.COLUMN(SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN)
          .FROM(SIMPLE_MUTABLE_ENTITY)
      ).map(SelectQuery<*, *>::execute)
    ).isEqualTo(
      listOf(
        listOf(0L),
        listOf(null),
        listOf(null),
        listOf(null),
        listOf(null),
        listOf(false),
        listOf(null)
      )
    )
    database.execSQL("UPDATE simple_mutable_entity SET primitive_boolean=2")
    assertThat(requiredBoolean.execute()).isEqualTo(listOf(true))
  }

  @Test
  fun generatedFieldsPreserveExactValuesAliasesAndStorage() {
    val row = ScalarStorageEntity(
      id = null,
      longValue = 9007199254740993L,
      nullableLong = null,
      floatValue = 1.25f,
      nullableFloat = null,
      byteValue = (-128).toByte(),
      nullableByte = null,
      byteArray = byteArrayOf(0, 1, -1),
      nullableByteArray = null,
      boxedByteArray = arrayOf(2.toByte(), (-3).toByte()),
      nullableBoxedByteArray = null
    )
    insertedResult(
      result = row.insert()
        .execute(),
      modelName = "ScalarStorageEntity"
    )
    val alias = SCALAR_STORAGE_ENTITY AS "storage alias"
    assertThat(
      SELECT.COLUMN(alias.LONG_VALUE)
        .FROM(alias)
        .execute()
    ).isEqualTo(listOf(9007199254740993L))
    assertThat(
      SELECT
        .COLUMNS(SCALAR_STORAGE_ENTITY.LONG_VALUE, SCALAR_STORAGE_ENTITY.NULLABLE_LONG)
        .FROM(SCALAR_STORAGE_ENTITY)
        .execute()
    ).isEqualTo(listOf(Row2(9007199254740993L, null)))
    assertThat(
      SELECT.COLUMN(SCALAR_STORAGE_ENTITY.ID)
        .FROM(SCALAR_STORAGE_ENTITY)
        .execute()
        .single()
    ).isEqualTo(row.id)
    val cases = listOf(
      StorageCase(
        label = "generated exact integer",
        expression = SCALAR_STORAGE_ENTITY.LONG_VALUE,
        expected = StorageCell(
          kind = FIELD_TYPE_INTEGER,
          value = 9007199254740993L
        )
      ),
      StorageCase(
        label = "generated byte storage",
        expression = SCALAR_STORAGE_ENTITY.BYTE_VALUE,
        expected = StorageCell(
          kind = FIELD_TYPE_BLOB,
          value = listOf<Byte>(-128)
        )
      ),
      StorageCase(
        label = "generated nullable blob",
        expression = SCALAR_STORAGE_ENTITY.NULLABLE_BYTE_ARRAY,
        expected = StorageCell(
          kind = FIELD_TYPE_NULL,
          value = null
        )
      )
    )
    for (case in cases) {
      val actual = SELECT.COLUMN(case.expression)
        .FROM(SCALAR_STORAGE_ENTITY)
        .toCursor()
        .execute()
        .use(::storageCell)
      assertWithMessage(case.label)
        .that(actual)
        .isEqualTo(case.expected)
    }
  }

  @Test
  fun generatedCustomFieldsKeepDirectNullAndDecoderNullPolicies() {
    val modelCase = TransformerModelCatalog.runtimeCase
    insertedResult(
      result = modelCase.insert(modelCase.newValue(1))
        .execute(),
      modelName = modelCase.name
    )
    val owned = SELECT.COLUMN(TRANSFORMER_RUNTIME_ENTITY.OWNED_TOKEN)
      .FROM(TRANSFORMER_RUNTIME_ENTITY)
    assertThat(owned.execute()).isEqualTo(listOf(OwnedToken("owned-1")))
    assertThat(
      owned.readAs(STRING)
        .execute()
    ).isEqualTo(listOf("owned-1"))
    assertThat(
      SELECT.COLUMN(TRANSFORMER_RUNTIME_ENTITY.EXTERNAL_TOKEN)
        .FROM(TRANSFORMER_RUNTIME_ENTITY)
        .execute()
    ).isEqualTo(listOf(modelCase.newValue(1).externalToken))
    val database = defaultWritableDatabase()
    database.execSQL("UPDATE transformer_runtime_entity SET owned_token=NULL, nullable_token=NULL, external_token=NULL")
    assertThat(owned.execute()).isEqualTo(listOf(OwnedToken("sentinel")))
    assertThat(
      owned.readAs(STRING)
        .execute()
    ).isEqualTo(listOf(null))
    assertThat(
      SELECT.COLUMN(TRANSFORMER_RUNTIME_ENTITY.NULLABLE_TOKEN)
        .FROM(TRANSFORMER_RUNTIME_ENTITY)
        .execute()
    ).isEqualTo(listOf(null))
    assertThat(
      SELECT.COLUMN(TRANSFORMER_RUNTIME_ENTITY.EXTERNAL_TOKEN)
        .FROM(TRANSFORMER_RUNTIME_ENTITY)
        .execute()
    ).isEqualTo(listOf(null))
    database.execSQL("UPDATE transformer_runtime_entity SET owned_token='reject', nullable_token='reject'")
    assertThat(owned.execute()).isEqualTo(listOf(null))
    assertThat(
      owned.readAs(STRING)
        .execute()
    ).isEqualTo(listOf("reject"))
    assertThat(
      SELECT.COLUMN(TRANSFORMER_RUNTIME_ENTITY.NULLABLE_TOKEN)
        .FROM(TRANSFORMER_RUNTIME_ENTITY)
        .execute()
    ).isEqualTo(listOf(null))
  }

  @Test
  fun nativeArgumentsKeepTheirSQLiteStorageAndExactValues() {
    val cases = listOf(
      StorageCase(
        label = "integer precision",
        expression = 9007199254740993L.asValue(),
        expected = StorageCell(
          kind = FIELD_TYPE_INTEGER,
          value = 9007199254740993L
        )
      ),
      StorageCase(
        label = "real",
        expression = 1.25.asValue(),
        expected = StorageCell(
          kind = FIELD_TYPE_FLOAT,
          value = 1.25
        )
      ),
      StorageCase(
        label = "text",
        expression = "O'Brien".asValue(),
        expected = StorageCell(
          kind = FIELD_TYPE_STRING,
          value = "O'Brien"
        )
      ),
      StorageCase(
        label = "null",
        expression = (null as String?).asValue(),
        expected = StorageCell(
          kind = FIELD_TYPE_NULL,
          value = null
        )
      ),
      StorageCase(
        label = "blob",
        expression = byteArrayOf(0, 1, -1).asValue(),
        expected = StorageCell(
          kind = FIELD_TYPE_BLOB,
          value = listOf<Byte>(0, 1, -1)
        )
      ),
      StorageCase(
        label = "byte is blob",
        expression = 42.toByte()
          .asValue(),
        expected = StorageCell(
          kind = FIELD_TYPE_BLOB,
          value = listOf<Byte>(42)
        )
      )
    )
    for (case in cases) {
      val actual = SELECT
        .COLUMN(case.expression)
        .toCursor()
        .execute()
        .use(::storageCell)
      assertWithMessage(case.label)
        .that(actual)
        .isEqualTo(case.expected)
    }
    assertThat(
      SELECT.COLUMN(9007199254740993L.asValue())
        .execute()
    ).isEqualTo(listOf(9007199254740993L))
    assertThat(
      SELECT.COLUMN(9007199254740993L.asValue())
        .readAs(NUMBER)
        .execute()
    ).isEqualTo(listOf(9007199254740993L))
    assertThat(
      SELECT.COLUMN(1.25.asValue())
        .readAs(NUMBER)
        .execute()
    ).isEqualTo(listOf(1.25))
    assertThat(
      SELECT.COLUMN(NULL)
        .readAs(LONG)
        .execute()
    ).isEqualTo(listOf(null))
  }

  @Test
  fun generatedCodecOwnsEncodingAndDirectNullDecoding() {
    val value = listOf(ParameterizedToken("a"), ParameterizedToken("b"))
    val query = SELECT.COLUMN(value.asValue())
    val storage = query.toCursor()
      .execute()
      .use(::storageCell)
    assertThat(storage)
      .isEqualTo(
        StorageCell(
          kind = FIELD_TYPE_STRING,
          value = "a|b"
        )
      )
    assertThat(query.execute()).isEqualTo(listOf(value))
    assertThat(
      SELECT.COLUMN("'a|b'".expr(SqlType.LIST_PARAMETERIZED_TOKEN))
        .execute()
    ).isEqualTo(listOf(value))
    assertThat(
      SELECT.COLUMN("NULL".expr(SqlType.OWNED_TOKEN))
        .execute()
    ).isEqualTo(listOf(OwnedToken("sentinel")))
    assertThat(
      SELECT.COLUMN("NULL".expr(SqlType.OWNED_TOKEN.nullable))
        .execute()
    ).isEqualTo(listOf(null))
    assertThat(
      SELECT.COLUMN("'reject'".expr(SqlType.OWNED_TOKEN))
        .execute()
    ).isEqualTo(listOf(null))
  }

  private class StorageCase(
    val label: String,
    val expression: SqlExpression<*, *, *>,
    val expected: StorageCell
  )

  private data class StorageCell(val kind: Int, val value: Any?)

  private fun storageCell(cursor: Cursor): StorageCell {
    check(cursor.moveToFirst())
    val kind = cursor.getType(0)
    return StorageCell(
      kind = kind,
      value = when (kind) {
        FIELD_TYPE_NULL -> null
        FIELD_TYPE_INTEGER -> cursor.getLong(0)
        FIELD_TYPE_FLOAT -> cursor.getDouble(0)
        FIELD_TYPE_STRING -> cursor.getString(0)
        else -> cursor.getBlob(0).toList()
      }
    )
  }
}
