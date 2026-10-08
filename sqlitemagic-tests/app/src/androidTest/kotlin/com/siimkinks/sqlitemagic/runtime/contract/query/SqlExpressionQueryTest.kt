package com.siimkinks.sqlitemagic.runtime.contract.query

import android.annotation.SuppressLint
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.ImmutableValueWithNullableFieldsTable.Companion.IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithNullableFields
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.model.catalog.QueryPredicateCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertSeedInserted
import org.junit.Test

@SuppressLint("CheckResult")
class SqlExpressionQueryTest : RuntimeDatabaseTest() {
  @Test
  fun safeSumsReturnZeroForEmptyInput() {
    assertSafeSums(
      expectedSum = 0.0,
      expectedDistinctSum = 0.0
    )
  }

  @Test
  fun safeSumsReturnZeroForAllNullInput() {
    seedNullableIntegers(null, null)
    assertSafeSums(
      expectedSum = 0.0,
      expectedDistinctSum = 0.0
    )
  }

  @Test
  fun safeSumDistinctRemovesDuplicateNonNullValues() {
    seedNullableIntegers(10, null, 10, 20)
    assertSafeSums(
      expectedSum = 40.0,
      expectedDistinctSum = 30.0
    )
  }

  @Test
  fun safeSumAvoidsIntegerOverflow() {
    seedNullableIntegers(1, 2)
    val query = Select
      .column(Select.sum(Select.asColumn(Long.MAX_VALUE)))
      .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
      .takeFirst()
    val expected = Long.MAX_VALUE.toDouble() * 2.0
    // Scalar statement parsing passes through SQLite text; allow 14 significant decimal digits.
    val tolerance = expected * 1e-14

    fun assertTotal(value: Double?) {
      val actual = checkNotNull(value)
      assertThat(actual.isFinite()).isTrue()
      assertThat(actual)
        .isWithin(tolerance)
        .of(expected)
    }

    assertTotal(query.execute())
    val observer = query.observe()
      .runQueryOnce()
      .test()
    observer.assertComplete()
      .assertNoErrors()
      .assertValueCount(1)
    val observed = observer
      .values()
      .single()
    assertTotal(observed)
  }

  private fun assertSafeSums(
    expectedSum: Double,
    expectedDistinctSum: Double
  ) {
    val column = IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER
    val cases = listOf(
      "sum" to (Select.sum(column) to expectedSum),
      "sumDistinct" to (Select.sumDistinct(column) to expectedDistinctSum)
    )
    for ((label, aggregateAndExpected) in cases) {
      val (aggregate, expected) = aggregateAndExpected
      val query = Select.column(aggregate)
        .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
        .takeFirst()
      assertWithMessage(label).that(query.execute())
        .isEqualTo(expected)
      query.observe()
        .runQueryOnce()
        .test()
        .assertResult(expected)
    }
  }

  private fun seedNullableIntegers(vararg values: Int?) = values.forEach { integer ->
    assertSeedInserted(
      result = ImmutableValueWithNullableFields(
        id = null,
        string = null,
        aBoolean = null,
        integer = integer
      )
        .insert()
        .execute(),
      modelName = "safe-sum"
    )
  }

  @Test
  fun numericAggregatesReturnExpectedValues() {
    QueryPredicateCatalog.seed()

    assertThat(
      Select
        .column(Select.avg(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .takeFirst()
        .execute()
    ).isEqualTo(202.0)
    assertThat(
      Select
        .column(Select.sum(IMMUTABLE_VALUE_WITH_FIELDS.A_SHORT))
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .takeFirst()
        .execute()
    ).isEqualTo(66.0)
    assertThat(
      Select
        .column(Select.min(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .takeFirst()
        .execute()
    ).isEqualTo(101)
    assertThat(
      Select
        .column(Select.max(IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE))
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .takeFirst()
        .execute()
    ).isEqualTo("non-null-string-3")
  }

  @Test
  fun stringFunctionsReturnExpectedValues() {
    QueryPredicateCatalog.seed()

    assertThat(
      Select
        .column(Select.lower(IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE))
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
        .execute()
    ).isEqualTo(
      listOf(
        "non-null-string-1",
        "non-null-string-2",
        "non-null-string-3"
      )
    )
    assertThat(
      Select
        .column(Select.length(IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE))
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
        .execute()
    ).isEqualTo(listOf(17L, 17L, 17L))
    assertThat(
      Select
        .column(IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE.replace("non-null-", ""))
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
        .execute()
    ).isEqualTo(listOf("string-1", "string-2", "string-3"))
  }

  @Test
  fun chainedArithmeticReturnsExpectedValues() {
    QueryPredicateCatalog.seed()
    val expression = ((((IMMUTABLE_VALUE_WITH_FIELDS.INTEGER + 2) * 2.0) - 4.0) / 2.0) % 100.0

    assertThat(
      Select
        .column(expression)
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
        .execute()
    ).isEqualTo(listOf(1.0, 2.0, 3.0))
  }

  @Test
  fun rawTextExpressionReturnsExpectedValues() {
    QueryPredicateCatalog.seed()
    val expression = Select.asRawColumn(
      "printf('%s:%d', ${IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE}, ${IMMUTABLE_VALUE_WITH_FIELDS.A_SHORT})"
    )

    assertThat(
      Select
        .column(expression)
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
        .execute()
    ).isEqualTo(
      listOf(
        "non-null-string-1:11",
        "non-null-string-2:22",
        "non-null-string-3:33"
      )
    )
  }
}
