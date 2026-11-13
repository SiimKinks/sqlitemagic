package com.siimkinks.sqlitemagic.runtime.contract.insert

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.COUNT
import com.siimkinks.sqlitemagic.INSERT
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.InsertedRow
import com.siimkinks.sqlitemagic.MutationQuery
import com.siimkinks.sqlitemagic.Row2
import com.siimkinks.sqlitemagic.Row3
import com.siimkinks.sqlitemagic.RowIdInsertQuery
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.ScalarStorageEntityTable.Companion.SCALAR_STORAGE_ENTITY
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SqlType.FLOAT
import com.siimkinks.sqlitemagic.SqlType.INT
import com.siimkinks.sqlitemagic.UNION_ALL
import com.siimkinks.sqlitemagic.WITH
import com.siimkinks.sqlitemagic.asScalar
import com.siimkinks.sqlitemagic.asValue
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithFields
import com.siimkinks.sqlitemagic.fixture.model.ScalarStorageEntity
import com.siimkinks.sqlitemagic.fixture.model.TransformableObject
import com.siimkinks.sqlitemagic.mapTo
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

private fun InsertedRow<ScalarStorageEntity>.assign(entity: ScalarStorageEntity) {
  this[SCALAR_STORAGE_ENTITY.ID] = entity.id
  this[SCALAR_STORAGE_ENTITY.LONG_VALUE] = entity.longValue
  this[SCALAR_STORAGE_ENTITY.NULLABLE_LONG] = entity.nullableLong
  this[SCALAR_STORAGE_ENTITY.FLOAT_VALUE] = entity.floatValue
  this[SCALAR_STORAGE_ENTITY.NULLABLE_FLOAT] = entity.nullableFloat
  this[SCALAR_STORAGE_ENTITY.BYTE_VALUE] = entity.byteValue
  this[SCALAR_STORAGE_ENTITY.NULLABLE_BYTE] = entity.nullableByte
  this[SCALAR_STORAGE_ENTITY.BYTE_ARRAY] = entity.byteArray
  this[SCALAR_STORAGE_ENTITY.NULLABLE_BYTE_ARRAY] = entity.nullableByteArray
  this[SCALAR_STORAGE_ENTITY.BOXED_BYTE_ARRAY] = entity.boxedByteArray
  this[SCALAR_STORAGE_ENTITY.NULLABLE_BOXED_BYTE_ARRAY] = entity.nullableBoxedByteArray
}

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
    )
      .map(MutationQuery::execute)

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
  fun executeWideRows() {
    val sparse = ScalarStorageEntity(
      id = 1L,
      longValue = 10L,
      nullableLong = null,
      floatValue = 1.25f,
      nullableFloat = null,
      byteValue = 1,
      nullableByte = null,
      byteArray = byteArrayOf(1),
      nullableByteArray = null,
      boxedByteArray = arrayOf(1),
      nullableBoxedByteArray = null
    )
    val full = ScalarStorageEntity(
      id = 2L,
      longValue = Long.MAX_VALUE,
      nullableLong = 20L,
      floatValue = -0.5f,
      nullableFloat = 0.75f,
      byteValue = Byte.MIN_VALUE,
      nullableByte = Byte.MAX_VALUE,
      byteArray = byteArrayOf(2, 3),
      nullableByteArray = byteArrayOf(),
      boxedByteArray = arrayOf(4),
      nullableBoxedByteArray = arrayOf(5, 6)
    )
    val columns = arrayOf(
      SCALAR_STORAGE_ENTITY.ID,
      SCALAR_STORAGE_ENTITY.LONG_VALUE,
      SCALAR_STORAGE_ENTITY.NULLABLE_LONG,
      SCALAR_STORAGE_ENTITY.FLOAT_VALUE,
      SCALAR_STORAGE_ENTITY.NULLABLE_FLOAT,
      SCALAR_STORAGE_ENTITY.BYTE_VALUE,
      SCALAR_STORAGE_ENTITY.NULLABLE_BYTE,
      SCALAR_STORAGE_ENTITY.BYTE_ARRAY,
      SCALAR_STORAGE_ENTITY.NULLABLE_BYTE_ARRAY,
      SCALAR_STORAGE_ENTITY.BOXED_BYTE_ARRAY,
      SCALAR_STORAGE_ENTITY.NULLABLE_BOXED_BYTE_ARRAY
    )
    val sparseLong = SELECT
      .COLUMN(SCALAR_STORAGE_ENTITY.LONG_VALUE)
      .FROM(SCALAR_STORAGE_ENTITY)
      .WHERE(SCALAR_STORAGE_ENTITY.ID IS 1L)
      .asScalar()

    val counts = listOf(
      INSERT
        .INTO(SCALAR_STORAGE_ENTITY)
        .COLUMNS(
          SCALAR_STORAGE_ENTITY.ID,
          SCALAR_STORAGE_ENTITY.LONG_VALUE,
          SCALAR_STORAGE_ENTITY.NULLABLE_LONG,
          SCALAR_STORAGE_ENTITY.FLOAT_VALUE,
          SCALAR_STORAGE_ENTITY.NULLABLE_FLOAT,
          SCALAR_STORAGE_ENTITY.BYTE_VALUE,
          SCALAR_STORAGE_ENTITY.NULLABLE_BYTE,
          SCALAR_STORAGE_ENTITY.BYTE_ARRAY,
          SCALAR_STORAGE_ENTITY.NULLABLE_BYTE_ARRAY,
          SCALAR_STORAGE_ENTITY.BOXED_BYTE_ARRAY,
          SCALAR_STORAGE_ENTITY.NULLABLE_BOXED_BYTE_ARRAY
        )
        .VALUES { it.assign(sparse) },
      INSERT.INTO(SCALAR_STORAGE_ENTITY).COLUMNS(*columns) VALUES {
        it.assign(full)
        it[SCALAR_STORAGE_ENTITY.NULLABLE_LONG] = sparseLong
        it[SCALAR_STORAGE_ENTITY.FLOAT_VALUE] = (1.5.asValue() + 1.0).readAs(FLOAT)
      },
      INSERT
        .INTO(SCALAR_STORAGE_ENTITY)
        .COLUMNS(*columns)
        .VALUES(listOf(sparse.copy(id = 3L), full.copy(id = 4L)), InsertedRow<ScalarStorageEntity>::assign)
        .VALUES(emptyList(), InsertedRow<ScalarStorageEntity>::assign),
      INSERT
        .INTO(SCALAR_STORAGE_ENTITY)
        .COLUMNS(*columns)
        .VALUES(emptyList(), InsertedRow<ScalarStorageEntity>::assign),
      INSERT
        .OR(CONFLICT_IGNORE)
        .INTO(SCALAR_STORAGE_ENTITY)
        .COLUMNS(*columns)
        .VALUES { it.assign(full.copy(id = 1L)) }
    )
      .map(MutationQuery::execute)

    assertThat(counts).isEqualTo(listOf(1, 1, 2, 0, 0))
    assertThat(
      SELECT
        .FROM(SCALAR_STORAGE_ENTITY)
        .ORDER_BY(SCALAR_STORAGE_ENTITY.ID.ASC())
        .execute()
    )
      .isEqualTo(
        listOf(
          sparse,
          full.copy(
            nullableLong = 10L,
            floatValue = 2.5f
          ),
          sparse.copy(id = 3L),
          full.copy(id = 4L)
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
      )
        .map(MutationQuery::execute)

      assertThat(counts).isEqualTo(listOf(1, 0, 0))
      observer.assertValuesOnly(emptyList(), listOf("first"))
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun readRowIdReadsInsertedRowIdsAndNullWhenNothingWasInserted() {
    val observer = SELECT
      .COLUMN(SIMPLE_MUTABLE_ENTITY.VALUE)
      .FROM(SIMPLE_MUTABLE_ENTITY)
      .ORDER_BY(SIMPLE_MUTABLE_ENTITY.ID.ASC())
      .observe()
      .runQuery()
      .test()
      .assertValuesOnly(emptyList())
    try {
      val inserted = checkNotNull(
        (INSERT INTO SIMPLE_MUTABLE_ENTITY COLUMNS SIMPLE_MUTABLE_ENTITY.VALUE VALUES "inserted")
          .readRowId()
          .execute()
      )
      val conflicting = INSERT
        .OR(CONFLICT_IGNORE)
        .INTO(SIMPLE_MUTABLE_ENTITY)
        .COLUMNS(SIMPLE_MUTABLE_ENTITY.ID, SIMPLE_MUTABLE_ENTITY.VALUE)
      val rowIds = listOf(
        conflicting
          .VALUES(inserted, "ignored")
          .readRowId(),
        conflicting
          .VALUES(-1L, "minus one")
          .readRowId(),
        INSERT
          .INTO(SIMPLE_MUTABLE_ENTITY)
          .DEFAULT_VALUES()
          .readRowId()
      )
        .map(RowIdInsertQuery::execute)

      assertThat(rowIds).isEqualTo(listOf(null, -1L, inserted + 1))
      observer.assertValuesOnly(
        emptyList(),
        listOf("inserted"),
        listOf("minus one", "inserted"),
        listOf("minus one", "inserted", null)
      )
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun insertSelectInsertsTheRowsOfTypedQueries() {
    val target = INSERT
      .INTO(SIMPLE_MUTABLE_ENTITY)
      .COLUMNS(SIMPLE_MUTABLE_ENTITY.VALUE, SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN)
    val a = checkNotNull(
      target
        .VALUES("a", true)
        .readRowId()
        .execute()
    )
    val b = checkNotNull(
      target
        .VALUES("b", false)
        .readRowId()
        .execute()
    )
    val rows = SELECT
      .COLUMNS(SIMPLE_MUTABLE_ENTITY.VALUE, SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN)
      .FROM(SIMPLE_MUTABLE_ENTITY)
    val first = rows.WHERE(SIMPLE_MUTABLE_ENTITY.ID IS a) AS "first"
    val flagged = SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN.asParameter("flagged")

    val counts = listOf(
      INSERT INTO SIMPLE_MUTABLE_ENTITY COLUMNS SIMPLE_MUTABLE_ENTITY.VALUE SELECT (
        SELECT COLUMN SIMPLE_MUTABLE_ENTITY.VALUE FROM SIMPLE_MUTABLE_ENTITY WHERE (SIMPLE_MUTABLE_ENTITY.ID IS a)
      ),
      target SELECT (rows.WHERE(SIMPLE_MUTABLE_ENTITY.ID IS b) UNION_ALL rows.WHERE(SIMPLE_MUTABLE_ENTITY.ID IS a)),
      target.SELECT(
        SELECT
          .COLUMNS(first[SIMPLE_MUTABLE_ENTITY.VALUE], first[SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN])
          .FROM(first)
      ),
      target.SELECT(
        WITH(rows.WHERE(SIMPLE_MUTABLE_ENTITY.ID IS b)) { second ->
          SELECT
            .COLUMNS(second[SIMPLE_MUTABLE_ENTITY.VALUE], second[SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN])
            .FROM(second)
        }
      ),
      target
        .SELECT(
          rows
            .WHERE(SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN IS flagged)
            .mapTo { value, flag -> "$value $flag" }
        )
        .bind(flagged TO true)
    )
      .map(MutationQuery::execute)

    assertThat(counts).isEqualTo(listOf(1, 2, 1, 1, 3))
    assertThat(
      rows
        .ORDER_BY(SIMPLE_MUTABLE_ENTITY.ID.ASC())
        .execute()
    )
      .isEqualTo(
        listOf(
          Row2("a", true),
          Row2("b", false),
          Row2("a", false),
          Row2("b", false),
          Row2("a", true),
          Row2("a", true),
          Row2("b", false),
          Row2("a", true),
          Row2("a", true),
          Row2("a", true)
        )
      )
  }

  @Test
  fun wideInsertSelectTakesAnyQueryAndLeavesTheColumnCountToSqlite() {
    val stored = ScalarStorageEntity(
      id = 1L,
      longValue = 10L,
      nullableLong = null,
      floatValue = 1.25f,
      nullableFloat = 0.5f,
      byteValue = 1,
      nullableByte = null,
      byteArray = byteArrayOf(1),
      nullableByteArray = null,
      boxedByteArray = arrayOf(1),
      nullableBoxedByteArray = arrayOf(2)
    )
    val copied = arrayOf(
      SCALAR_STORAGE_ENTITY.LONG_VALUE,
      SCALAR_STORAGE_ENTITY.NULLABLE_LONG,
      SCALAR_STORAGE_ENTITY.FLOAT_VALUE,
      SCALAR_STORAGE_ENTITY.NULLABLE_FLOAT,
      SCALAR_STORAGE_ENTITY.BYTE_VALUE,
      SCALAR_STORAGE_ENTITY.NULLABLE_BYTE,
      SCALAR_STORAGE_ENTITY.BYTE_ARRAY,
      SCALAR_STORAGE_ENTITY.NULLABLE_BYTE_ARRAY,
      SCALAR_STORAGE_ENTITY.BOXED_BYTE_ARRAY,
      SCALAR_STORAGE_ENTITY.NULLABLE_BOXED_BYTE_ARRAY
    )
    INSERT
      .INTO(SCALAR_STORAGE_ENTITY)
      .COLUMNS(SCALAR_STORAGE_ENTITY.ID, *copied)
      .VALUES { it.assign(stored) }
      .execute()

    val copy = INSERT
      .INTO(SCALAR_STORAGE_ENTITY)
      .COLUMNS(*copied)
      .SELECT(
        SELECT
          .COLUMNS(*copied)
          .FROM(SCALAR_STORAGE_ENTITY)
      )

    assertThat(copy.execute()).isEqualTo(1)
    assertSqliteRejects(containing = "2 values for 10 columns") {
      INSERT
        .INTO(SCALAR_STORAGE_ENTITY)
        .COLUMNS(*copied)
        .SELECT(
          SELECT
            .COLUMNS(SCALAR_STORAGE_ENTITY.LONG_VALUE, SCALAR_STORAGE_ENTITY.FLOAT_VALUE)
            .FROM(SCALAR_STORAGE_ENTITY)
        )
        .execute()
    }
    assertThat(
      SELECT
        .FROM(SCALAR_STORAGE_ENTITY)
        .ORDER_BY(SCALAR_STORAGE_ENTITY.ID.ASC())
        .execute()
        .map { it.copy(id = null) }
    )
      .isEqualTo(List(2) { stored.copy(id = null) })
  }

  @Test
  fun insertSelectPublishesOnlyWhenRowsAreInsertedAndLeavesNotNullToSqlite() {
    val copy = INSERT INTO SIMPLE_MUTABLE_ENTITY COLUMNS SIMPLE_MUTABLE_ENTITY.VALUE SELECT (
      SELECT COLUMN IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE FROM IMMUTABLE_VALUE_WITH_FIELDS
    )
    val observer = SELECT
      .COLUMN(SIMPLE_MUTABLE_ENTITY.VALUE)
      .FROM(SIMPLE_MUTABLE_ENTITY)
      .ORDER_BY(SIMPLE_MUTABLE_ENTITY.ID.ASC())
      .observe()
      .runQuery()
      .test()
      .assertValuesOnly(emptyList())
    try {
      val empty = copy.execute()
      (INSERT INTO IMMUTABLE_VALUE_WITH_FIELDS COLUMNS IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE VALUES "copied")
        .execute()
      val copied = copy.execute()

      assertThat(listOf(empty, copied)).isEqualTo(listOf(0, 1))
      observer.assertValuesOnly(emptyList(), listOf("copied"))
    } finally {
      observer.dispose()
    }
    INSERT
      .INTO(SIMPLE_MUTABLE_ENTITY)
      .DEFAULT_VALUES()
      .execute()
    assertSqliteRejects(containing = "NOT NULL constraint failed") {
      (
        INSERT INTO IMMUTABLE_VALUE_WITH_FIELDS COLUMNS IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE SELECT (
          SELECT COLUMN SIMPLE_MUTABLE_ENTITY.VALUE FROM SIMPLE_MUTABLE_ENTITY
        )
      )
        .execute()
    }
    assertThat(
      SELECT
        .COLUMN(IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE)
        .FROM(IMMUTABLE_VALUE_WITH_FIELDS)
        .execute()
    )
      .isEqualTo(listOf("copied"))
  }
}
