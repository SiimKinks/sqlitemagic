package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.Select.asColumn
import com.siimkinks.sqlitemagic.Select.format
import com.siimkinks.sqlitemagic.Select.groupConcat
import com.siimkinks.sqlitemagic.Select.groupConcatDistinct
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SqlUtilTest {
  @Test
  fun `quotes SQL string literal`() {
    val testCases = arrayOf(
      arrayOf("empty", "", "''"),
      arrayOf("ordinary", "hello", "'hello'"),
      arrayOf("single apostrophe", "O'Brien", "'O''Brien'"),
      arrayOf("multiple apostrophes", "'quoted'", "'''quoted'''"),
      arrayOf("other characters", "line\n\"two\"", "'line\n\"two\"'")
    )

    for ((label, input, expected) in testCases) {
      assertWithMessage(label)
        .that(SqlUtil.quoteSqlStringLiteral(input))
        .isEqualTo(expected)
    }
  }

  @Test
  fun `quotes inline string literal expressions`() {
    val value = asColumn("O'Brien")

    assertSql(
      column = value,
      expected = "'O''Brien'"
    )
    assertSql(
      column = value.replace("'", "x'y"),
      expected = "replace('O''Brien','''','x''y')"
    )
    assertSql(
      column = value.trim("'"),
      expected = "trim('O''Brien','''')"
    )
    assertSql(
      column = groupConcat(value, "'|"),
      expected = "group_concat('O''Brien','''|')"
    )
    assertSql(
      column = groupConcatDistinct(value, "'|"),
      expected = "group_concat(DISTINCT 'O''Brien','''|')"
    )
    assertSql(
      column = format("%'s", value),
      expected = "printf('%''s', 'O''Brien')"
    )
  }

  @Test
  fun `query uses no-args overload when args are null`() {
    val database = mock<SupportSQLiteDatabase>()
    val cursor = mock<Cursor>()
    whenever(database.query(QUERY))
      .thenReturn(cursor)

    val actual = SqlUtil.query(
      db = database,
      sql = QUERY,
      args = null
    )

    assertThat(actual)
      .isSameInstanceAs(cursor)
    verify(database).query(QUERY)
    verify(database, never()).query(eq(QUERY), any())
  }

  @Test
  fun `query uses args overload when args are present`() {
    val database = mock<SupportSQLiteDatabase>()
    val cursor = mock<Cursor>()
    val args = arrayOf("first", "second")
    whenever(
      database.query(QUERY, args)
    ).thenReturn(cursor)

    val actual = SqlUtil.query(
      db = database,
      sql = QUERY,
      args = args
    )

    assertThat(actual)
      .isSameInstanceAs(cursor)
    verify(database).query(QUERY, args)
    verify(database, never()).query(QUERY)
  }

  @Test
  fun `create view uses no-args execSQL overload when query args are null`() {
    val database = mock<SupportSQLiteDatabase>()
    val query = compiledSelect(
      sql = "SELECT id FROM books",
      args = null
    )
    val expectedSql = "CREATE VIEW IF NOT EXISTS books_view AS SELECT id FROM books"

    SqlUtil.createView(
      db = database,
      query = query,
      viewName = "books_view"
    )

    verify(database).execSQL(expectedSql)
    verify(database, never()).execSQL(eq(expectedSql), any())
  }

  @Test
  fun `create view uses args execSQL overload when query args are present`() {
    val database = mock<SupportSQLiteDatabase>()
    val args = arrayOf("first", "second")
    val query = compiledSelect(
      sql = "SELECT id FROM books WHERE id=?",
      args = args
    )
    val expectedSql = "CREATE VIEW IF NOT EXISTS books_view AS SELECT id FROM books WHERE id=?"

    SqlUtil.createView(
      db = database,
      query = query,
      viewName = "books_view"
    )

    verify(database).execSQL(expectedSql, args)
    verify(database, never()).execSQL(expectedSql)
  }

  @Test
  fun `opByColumnSql returns original SQL when columns are null`() {
    val sql = "UPDATE books SET name=? WHERE id=? AND key=?"

    val actual = SqlUtil.opByColumnSql(
      sql = sql,
      tableName = TestSchema.table.name,
      byColumns = null
    )

    assertThat(actual).isEqualTo(sql)
  }

  @Test
  fun `opByColumnSql returns original SQL when no column matches table`() {
    val sql = "UPDATE books SET name=? WHERE id=? AND key=?"

    val actual = SqlUtil.opByColumnSql(
      sql = sql,
      tableName = "authors",
      byColumns = listOf(TestSchema.id)
    )

    assertThat(actual).isEqualTo(sql)
  }

  @Test
  fun `opByColumnSql replaces existing predicate with matching column`() {
    val sql = "UPDATE books SET name=? WHERE id=? AND key=?"

    val actual = SqlUtil.opByColumnSql(
      sql = sql,
      tableName = TestSchema.table.name,
      byColumns = listOf(TestSchema.id)
    )

    assertThat(actual)
      .isEqualTo("UPDATE books SET name=? WHERE id=?")
  }

  @Test
  fun `firstColumnForTable skips non-columns and returns first matching column`() {
    val columnFromOtherTable = TestSchema.id.inTable(
      Table<Any>("authors", null, 1)
    )

    val actual = SqlUtil.firstColumnForTable(
      tableName = TestSchema.table.name,
      columns = listOf("not a column", columnFromOtherTable, TestSchema.key, TestSchema.id)
    )

    assertThat(actual)
      .isSameInstanceAs(TestSchema.key)
  }

  @Test
  fun `firstColumnForTable returns null when no column matches`() {
    val columnFromOtherTable = TestSchema.id.inTable(
      Table<Any>("authors", null, 1)
    )

    val actual = SqlUtil.firstColumnForTable(
      tableName = TestSchema.table.name,
      columns = listOf("not a column", columnFromOtherTable)
    )

    assertThat(actual).isNull()
  }

  @Test
  fun `bindAllArgsAsStrings does nothing when args are null`() {
    val statement = mock<SupportSQLiteStatement>()

    SqlUtil.bindAllArgsAsStrings(
      statement = statement,
      args = null
    )

    verifyNoInteractions(statement)
  }

  @Test
  fun `bindAllArgsAsStrings binds all args at one-based indexes`() {
    val statement = mock<SupportSQLiteStatement>()

    SqlUtil.bindAllArgsAsStrings(
      statement = statement,
      args = arrayOf("first", "second", "third")
    )

    verify(statement).bindString(1, "first")
    verify(statement).bindString(2, "second")
    verify(statement).bindString(3, "third")
  }

  private fun compiledSelect(
    sql: String,
    args: Array<String>?
  ) = CompiledSelectImpl<Any, Any>(
    sql = sql,
    args = args,
    table = mock<Table<Any>>(),
    dbConnection = null,
    observedTables = emptyArray(),
    columns = null,
    tableGraphNodeNames = null,
    queryDeep = false
  )

  private fun assertSql(
    column: Column<*, *, *, *, *>,
    expected: String
  ) {
    val sql = StringBuilder()
    column.appendSql(sql)
    assertThat(sql.toString()).isEqualTo(expected)
  }

  companion object {
    private const val QUERY = "SELECT id FROM books"
  }
}
