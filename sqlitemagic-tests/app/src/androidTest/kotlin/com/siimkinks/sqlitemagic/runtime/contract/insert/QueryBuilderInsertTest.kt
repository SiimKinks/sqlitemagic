package com.siimkinks.sqlitemagic.runtime.contract.insert

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.COUNT
import com.siimkinks.sqlitemagic.INSERT
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.MutationQuery
import com.siimkinks.sqlitemagic.Row2
import com.siimkinks.sqlitemagic.Row3
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SqlType.INT
import com.siimkinks.sqlitemagic.asScalar
import com.siimkinks.sqlitemagic.asValue
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithFields
import com.siimkinks.sqlitemagic.fixture.model.TransformableObject
import com.siimkinks.sqlitemagic.plus
import com.siimkinks.sqlitemagic.readAs
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.assertSimpleRows
import com.siimkinks.sqlitemagic.runtime.support.assertSqliteRejects
import com.siimkinks.sqlitemagic.runtime.support.sqliteVersion
import com.siimkinks.sqlitemagic.runtime.support.supportsSqlite
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Rule
import org.junit.Test

internal class QueryBuilderInsertTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executePartialMultiRowListAndDefaultRows() {
    val counts = listOf(
      INSERT
        .INTO(SIMPLE_MUTABLE_ENTITY)
        .COLUMNS(SIMPLE_MUTABLE_ENTITY.ID, SIMPLE_MUTABLE_ENTITY.VALUE)
        .VALUES(100L, "explicit"),
      INSERT INTO SIMPLE_MUTABLE_ENTITY COLUMNS SIMPLE_MUTABLE_ENTITY.VALUE VALUES "infix",
      INSERT
        .INTO(SIMPLE_MUTABLE_ENTITY)
        .COLUMNS(SIMPLE_MUTABLE_ENTITY.VALUE, SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN)
        .VALUES("fluent", true)
        .VALUES(null, false),
      INSERT
        .INTO(SIMPLE_MUTABLE_ENTITY)
        .COLUMNS(
          SIMPLE_MUTABLE_ENTITY.VALUE,
          SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
          SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
        )
        .VALUES(listOf(Row3("listed", null, true), Row3(null, true, false))),
      INSERT
        .INTO(SIMPLE_MUTABLE_ENTITY)
        .DEFAULT_VALUES(),
      INSERT
        .OR(CONFLICT_IGNORE)
        .INTO(SIMPLE_MUTABLE_ENTITY)
        .COLUMNS(SIMPLE_MUTABLE_ENTITY.ID, SIMPLE_MUTABLE_ENTITY.VALUE)
        .VALUES(100L, "ignored"),
      INSERT
        .OR(CONFLICT_REPLACE)
        .INTO(SIMPLE_MUTABLE_ENTITY)
        .COLUMNS(SIMPLE_MUTABLE_ENTITY.ID, SIMPLE_MUTABLE_ENTITY.VALUE)
        .VALUES(100L, "replaced"),
      INSERT INTO SIMPLE_MUTABLE_ENTITY COLUMNS SIMPLE_MUTABLE_ENTITY.VALUE VALUES emptyList()
    ).map(MutationQuery::execute)

    assertThat(counts).isEqualTo(listOf(1, 1, 2, 2, 1, 0, 1, 0))
    assertThat(
      SELECT
        .COLUMNS(
          SIMPLE_MUTABLE_ENTITY.VALUE,
          SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
          SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
        )
        .FROM(SIMPLE_MUTABLE_ENTITY)
        .ORDER_BY(SIMPLE_MUTABLE_ENTITY.ID.ASC())
        .execute()
    )
      .isEqualTo(
        listOf(
          Row3("replaced", null, false),
          Row3("infix", null, false),
          Row3("fluent", true, false),
          Row3(null, false, false),
          Row3("listed", null, true),
          Row3(null, true, false),
          Row3(null, null, false)
        )
      )
  }

  @Test
  fun executeTransformedValuesAndExpressions() {
    val simpleCount = SELECT
      .COLUMN(COUNT())
      .FROM(SIMPLE_MUTABLE_ENTITY)
      .asScalar()
      .readAs(INT)

    val affectedRows = INSERT
      .INTO(IMMUTABLE_VALUE_WITH_FIELDS)
      .COLUMNS(
        IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE,
        IMMUTABLE_VALUE_WITH_FIELDS.INTEGER,
        IMMUTABLE_VALUE_WITH_FIELDS.TRANSFORMABLE_OBJECT
      )
      .VALUES("host", 7, TransformableObject(41))
      .VALUES("count".asValue(), simpleCount, TransformableObject(42).asValue())
      .VALUES("arithmetic".asValue(), (40.asValue() + 2).readAs(INT), TransformableObject(43).asValue())
      .observe()
      .blockingGet()

    assertThat(affectedRows).isEqualTo(3)
    assertThat(
      SELECT
        .FROM(IMMUTABLE_VALUE_WITH_FIELDS)
        .execute()
        .map { it.copy(id = null) }
    )
      .isEqualTo(
        listOf(
          ImmutableValueWithFields(
            id = null,
            stringValue = "host",
            aBoolean = false,
            integer = 7,
            aDouble = 0.0,
            aShort = 0,
            transformableObject = TransformableObject(41)
          ),
          ImmutableValueWithFields(
            id = null,
            stringValue = "count",
            aBoolean = false,
            integer = 0,
            aDouble = 0.0,
            aShort = 0,
            transformableObject = TransformableObject(42)
          ),
          ImmutableValueWithFields(
            id = null,
            stringValue = "arithmetic",
            aBoolean = false,
            integer = 42,
            aDouble = 0.0,
            aShort = 0,
            transformableObject = TransformableObject(43)
          )
        )
      )
  }

  @Test
  fun nullableScalarSubqueryIntoRequiredColumnFailsOnNotNull() {
    val missingValue = SELECT
      .COLUMN(SIMPLE_MUTABLE_ENTITY.VALUE)
      .FROM(SIMPLE_MUTABLE_ENTITY)
      .asScalar()

    assertSqliteRejects(containing = "NOT NULL constraint failed") {
      (INSERT INTO IMMUTABLE_VALUE_WITH_FIELDS COLUMNS IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE VALUES missingValue)
        .execute()
    }
    assertThat(SELECT.FROM(IMMUTABLE_VALUE_WITH_FIELDS).execute()).isEmpty()
  }

  @Test
  fun listRowsBindMoreThan999ValuesWhereSqliteAllowsThem() {
    val rows = List(400) { index -> Row3("row-$index", index % 2 == 0, index % 3 == 0) }
    val insert = INSERT
      .INTO(SIMPLE_MUTABLE_ENTITY)
      .COLUMNS(
        SIMPLE_MUTABLE_ENTITY.VALUE,
        SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
        SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
      )
      .VALUES(rows)

    when {
      supportsSqlite(
        version = sqliteVersion(),
        minor = 32
      ) -> {
        assertThat(insert.execute()).isEqualTo(400)
        assertThat(
          SELECT
            .COLUMNS(
              SIMPLE_MUTABLE_ENTITY.VALUE,
              SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
              SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
            )
            .FROM(SIMPLE_MUTABLE_ENTITY)
            .ORDER_BY(SIMPLE_MUTABLE_ENTITY.ID.ASC())
            .execute()
        )
          .isEqualTo(rows)
      }
      else -> {
        assertSqliteRejects(containing = "too many SQL variables") { insert.execute() }
        assertSimpleRows()
      }
    }
  }

  @Test
  fun insertPublishesOnlyWhenRowsAreInserted() {
    val observer = SELECT
      .COLUMN(SIMPLE_MUTABLE_ENTITY.VALUE)
      .FROM(SIMPLE_MUTABLE_ENTITY)
      .ORDER_BY(SIMPLE_MUTABLE_ENTITY.ID.ASC())
      .observe()
      .runQuery()
      .test()
      .assertValuesOnly(emptyList())
    try {
      val counts = listOf(
        INSERT
          .INTO(SIMPLE_MUTABLE_ENTITY)
          .COLUMNS(SIMPLE_MUTABLE_ENTITY.ID, SIMPLE_MUTABLE_ENTITY.VALUE)
          .VALUES(listOf(Row2(1L, "first"))),
        INSERT INTO SIMPLE_MUTABLE_ENTITY COLUMNS SIMPLE_MUTABLE_ENTITY.VALUE VALUES emptyList(),
        INSERT
          .OR(CONFLICT_IGNORE)
          .INTO(SIMPLE_MUTABLE_ENTITY)
          .COLUMNS(SIMPLE_MUTABLE_ENTITY.ID, SIMPLE_MUTABLE_ENTITY.VALUE)
          .VALUES(1L, "ignored")
      ).map(MutationQuery::execute)

      assertThat(counts).isEqualTo(listOf(1, 0, 0))
      observer.assertValuesOnly(emptyList(), listOf("first"))
    } finally {
      observer.dispose()
    }
  }
}
