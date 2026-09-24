package com.siimkinks.sqlitemagic

import android.database.Cursor
import android.database.sqlite.SQLiteDoneException
import androidx.sqlite.db.SupportSQLiteStatement
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlTestAssertions.assertExpression
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class ColumnValueAdapterTest {
  @Test
  fun `numeric conversion survives aliasing and query graph rebinding`() {
    val table = testTable<Any>(name = "score")
    val column = ScoreColumn(table = table)
    val aliased = column.`as`("points")
    val cursor = mock<Cursor>()
    val statement = mock<SupportSQLiteStatement>()
    whenever(cursor.getLong(0)).thenReturn(7L)
    whenever(statement.simpleQueryForLong()).thenReturn(8L)

    assertExpression(
      expression = aliased.greaterThan(Score(3L)),
      expectedSql = "score.value>?",
      "4"
    )
    assertThat(aliased.getFromCursor<Score>(cursor)).isEqualTo(Score(7L))
    assertThat(aliased.getFromStatement<Score>(statement)).isEqualTo(Score(8L))

    val rebound = Column.internalCopy(
      newTable = testTable<Any>(name = "score", alias = "joined"),
      column = aliased
    )
    assertThat(rebound.toSqlArg(Score(3L))).isEqualTo("4")
    assertThat(rebound.getFromCursor<Score>(cursor)).isEqualTo(Score(7L))
  }

  @Test
  fun `numeric base alias retains adapter conversion`() {
    val column = NumericColumn<Score, Score, Score, Any, NotNullable>(
      table = testTable(name = "score"),
      name = "value",
      valueParser = Utils.LONG_PARSER,
      nullable = false,
      alias = null,
      valueAdapter = ColumnValueAdapter.transformed(
        parser = Utils.LONG_PARSER,
        toDb = ::serializeScore,
        fromDb = ::deserializeScore
      )
    )

    assertExpression(
      expression = column.`as`("points").greaterThan(Score(3L)),
      expectedSql = "score.value>?",
      "4"
    )
  }

  @Test
  fun `nullable input reader sees null cursor values and empty statement results`() {
    val column = Column<Email, Email, Email, Any, Nullable>(
      table = testTable(name = "email"),
      name = "address",
      valueParser = Utils.STRING_PARSER,
      nullable = true,
      alias = null,
      valueAdapter = ColumnValueAdapter.transformedNullableInput(
        parser = Utils.STRING_PARSER,
        toDb = ::serializeNullableEmail,
        fromDb = ::deserializeNullableEmail
      )
    )
    val cursor = mock<Cursor>()
    val statement = mock<SupportSQLiteStatement>()
    whenever(cursor.isNull(0)).thenReturn(true)
    whenever(statement.simpleQueryForString()).thenThrow(mock<SQLiteDoneException>())
    val aliased = column.`as`("display")

    assertThat(aliased.getFromCursor<Email>(cursor)).isEqualTo(Email("missing"))
    assertThat(aliased.getFromStatement<Email>(statement)).isEqualTo(Email("missing"))
    val error = assertThrows(NullPointerException::class.java) {
      aliased.toSqlArg(Email(""))
    }
    assertThat(error)
      .hasMessageThat()
      .isEqualTo("SQL argument cannot be null")
  }

  private data class Score(val value: Long)

  private data class Email(val value: String)

  private class ScoreColumn : NumericColumn<Score, Score, Score, Any, NotNullable> {
    constructor(table: Table<Any>) : super(
      table = table,
      name = "value",
      valueParser = Utils.LONG_PARSER,
      nullable = false,
      alias = null,
      valueAdapter = ColumnValueAdapter.transformed(
        parser = Utils.LONG_PARSER,
        toDb = ::serializeScore,
        fromDb = ::deserializeScore
      )
    )

    private constructor(source: ScoreColumn, alias: String) : super(source = source, alias = alias)

    override fun `as`(alias: String) = ScoreColumn(source = this, alias = alias)
  }

  companion object {
    private fun serializeScore(value: Score) = value.value + 1L

    private fun deserializeScore(value: Long) = Score(value)

    private fun serializeNullableEmail(value: Email): String? = value.value.takeUnless(String::isEmpty)

    private fun deserializeNullableEmail(value: String?) = Email(value ?: "missing")
  }
}
