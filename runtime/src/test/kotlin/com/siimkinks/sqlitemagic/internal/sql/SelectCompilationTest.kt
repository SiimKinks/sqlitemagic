package com.siimkinks.sqlitemagic.internal.sql

import androidx.sqlite.SQLiteStatement
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.ExecutionReader
import com.siimkinks.sqlitemagic.NOT_DISPOSED
import com.siimkinks.sqlitemagic.PlanSnapshot
import com.siimkinks.sqlitemagic.SelectPlan
import com.siimkinks.sqlitemagic.Selection
import com.siimkinks.sqlitemagic.asValue
import com.siimkinks.sqlitemagic.snapshot
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class SelectCompilationTest {
  @Test
  fun `count renders selection without reader and binding reuses completed compilation`() {
    val parameter = 7L.asValue()
      .asParameter("value")
    val selection = RecordingSelection(
      output = parameter.node,
      reader = SQLiteStatement::getLong
    )
    val initial = SelectNode(selection)
    val filtered = initial.copy(
      where = true.asValue().node,
      limit = 2L
    )

    assertThat(selection.events).isEmpty()
    val count = compileCount(filtered)
      .bind(parameter TO 9L)
    assertThat(snapshot(count)).isEqualTo(
      PlanSnapshot(
        sql = "SELECT count(*) FROM (SELECT ? WHERE ? LIMIT 2)",
        arguments = listOf(NativeValue.Integer(9L), NativeValue.Integer(1L))
      )
    )
    assertThat(selection.events).containsExactly("render")

    selection.events.clear()
    val selectPlan = compileSelect(filtered)
    assertThat(selection.events)
      .containsExactly("render", "reader")
      .inOrder()
    val first = selectPlan.bind(parameter TO 11L)
    val second = selectPlan.bind(parameter TO 13L)
    assertThat(first.prepared).isSameInstanceAs(selectPlan.prepared)
    assertThat(second.prepared).isSameInstanceAs(selectPlan.prepared)
    assertThat(listOf(first, second).map(SelectPlan<*>::boundArguments)).isEqualTo(
      listOf(
        listOf(NativeValue.Integer(11L), NativeValue.Integer(1L)),
        listOf(NativeValue.Integer(13L), NativeValue.Integer(1L))
      )
    )
    val statement = mock<SQLiteStatement>()
    whenever(statement.step()).thenReturn(true, false, true, false)
    whenever(statement.getLong(0)).thenReturn(17L)
    assertThat(
      listOf(first, second).map { plan ->
        plan.mapRows(
          statement = statement,
          isDisposed = NOT_DISPOSED
        )
      }
    ).isEqualTo(listOf(listOf(17L), listOf(17L)))
    assertThat(selection.events)
      .containsExactly("render", "reader")
      .inOrder()
  }

  @Test
  fun `shared reading terminals use the selection-defined result`() {
    data class PairResult(val number: Long, val text: String)

    val selection = RecordingSelection(
      output = object : SqlRenderable {
        override fun render(context: SqlRenderingContext) {
          context.sql.append("11, 'eleven'")
        }
      },
      reader = { statement, index ->
        PairResult(
          number = statement.getLong(index),
          text = statement.getText(index + 1)
        )
      }
    )
    val node = SelectNode(selection)
    val select = compileSelect(node)
    val first = compileFirst(node)
    val readers: List<Pair<String, (SQLiteStatement) -> PairResult?>> = listOf(
      "select" to { statement ->
        select
          .mapRows(
            statement = statement,
            isDisposed = NOT_DISPOSED
          )
          .single()
      },
      "first" to { statement ->
        first.mapRows(
          statement = statement,
          isDisposed = NOT_DISPOSED
        )
      }
    )
    val expected = PairResult(
      number = 11L,
      text = "eleven"
    )

    assertThat(select.sql).isEqualTo("SELECT 11, 'eleven'")
    readers.forEach { (terminal, read) ->
      val statement = mock<SQLiteStatement>()
      whenever(statement.step()).thenReturn(true, false)
      whenever(statement.getLong(0)).thenReturn(11L)
      whenever(statement.getText(1)).thenReturn("eleven")
      assertWithMessage(terminal)
        .that(read(statement))
        .isEqualTo(expected)
    }
    assertThat(selection.events)
      .containsExactly("render", "reader", "render", "reader")
      .inOrder()
  }

  @Test
  fun `rendering failures never request a reader`() {
    val terminals: List<Pair<String, (SelectNode<Long, Selection<Long>>) -> SelectPlan<*>>> = listOf(
      "select" to ::compileSelect,
      "first" to ::compileFirst
    )
    val output = 1L.asValue().node

    terminals.forEach { (terminal, compile) ->
      val selection = RecordingSelection(
        output = output,
        reader = SQLiteStatement::getLong,
        failure = "render"
      )
      val node = SelectNode<Long, Selection<Long>>(selection)
      val thrown = assertThrows(IllegalStateException::class.java) { compile(node) }
      assertWithMessage(terminal)
        .that(thrown.message to selection.events)
        .isEqualTo("render" to listOf("render"))
    }
  }

  @Test
  fun `reading terminals map the rows of the statement and leave it to its lease`() {
    data class Case(
      val label: String,
      val rows: Int,
      val read: (SQLiteStatement) -> Any?
    )

    val node = SelectNode(
      RecordingSelection(
        output = 7L.asValue().node,
        reader = SQLiteStatement::getLong
      )
    )
    val failing = SelectNode(
      RecordingSelection<Long>(
        output = 7L.asValue().node,
        reader = { _, _ -> error("read failed") }
      )
    )

    fun reading(plan: SelectPlan<*>, disposed: Boolean = false): (SQLiteStatement) -> Any? = { statement ->
      plan.mapRows(
        statement = statement,
        isDisposed = { disposed }
      )
    }

    val cases = listOf(
      Case(
        label = "select",
        rows = 2,
        read = reading(compileSelect(node))
      ),
      Case(
        label = "disposed select",
        rows = 2,
        read = reading(
          plan = compileSelect(node),
          disposed = true
        )
      ),
      Case(
        label = "first",
        rows = 1,
        read = reading(compileFirst(node))
      ),
      Case(
        label = "empty first",
        rows = 0,
        read = reading(compileFirst(node))
      ),
      Case(
        label = "count",
        rows = 1,
        read = reading(compileCount(node))
      ),
      Case(
        label = "failed select read",
        rows = 1,
        read = { statement -> runCatching { reading(compileSelect(failing))(statement) }.exceptionOrNull()?.message }
      )
    )

    assertThat(
      cases.map { case ->
        var position = -1
        val statement = mock<SQLiteStatement>()
        whenever(statement.step()).thenAnswer { ++position < case.rows }
        whenever(statement.getLong(0)).thenReturn(7L)
        val result = case.read(statement)
        val closes = mockingDetails(statement).invocations.count { it.method.name == "close" }
        case.label to (result to closes)
      }
    ).isEqualTo(
      listOf(
        "select" to (listOf(7L, 7L) to 0),
        "disposed select" to (emptyList<Long>() to 0),
        "first" to (7L to 0),
        "empty first" to (null to 0),
        "count" to (7L to 0),
        "failed select read" to ("read failed" to 0)
      )
    )
  }

  private class RecordingSelection<T>(
    private val output: SqlRenderable,
    private val reader: (SQLiteStatement, Int) -> T,
    private val failure: String? = null
  ) : Selection<T>() {
    val events = mutableListOf<String>()

    private fun record(event: String) {
      events += event
      check(event != failure) { event }
    }

    override fun render(context: SqlRenderingContext) {
      record("render")
      output.render(context)
    }

    override fun createExecutionReader(context: SqlRenderingContext): ExecutionReader<T> {
      record("reader")
      return ExecutionReader { reader }
    }
  }
}
