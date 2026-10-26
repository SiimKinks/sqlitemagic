package com.siimkinks.sqlitemagic.internal.sql

import android.database.Cursor
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.ExecutionReader
import com.siimkinks.sqlitemagic.SelectPlan
import com.siimkinks.sqlitemagic.Selection
import com.siimkinks.sqlitemagic.asValue
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
      reader = Cursor::getLong
    )
    val initial = SelectNode(selection)
    val filtered = initial.copy(
      where = true.asValue().node,
      limit = 2L
    )

    assertThat(selection.events).isEmpty()
    val count = compileCount(filtered)
      .bind(parameter TO 9L)
    assertThat(count.sql).isEqualTo("SELECT count(*) FROM (SELECT ? WHERE ? LIMIT 2)")
    assertThat(count.boundArguments())
      .containsExactly(NativeValue.Integer(9L), NativeValue.Integer(1L))
      .inOrder()
    assertThat(selection.events).containsExactly("render")

    selection.events.clear()
    val cursorPlan = compileCursor(filtered)
    assertThat(selection.events).containsExactly("render", "reader")
      .inOrder()
    val first = cursorPlan.bind(parameter TO 11L)
    val second = cursorPlan.bind(parameter TO 13L)
    assertThat(first.prepared).isSameInstanceAs(cursorPlan.prepared)
    assertThat(second.prepared).isSameInstanceAs(cursorPlan.prepared)
    assertThat(first.boundArguments())
      .containsExactly(NativeValue.Integer(11L), NativeValue.Integer(1L))
      .inOrder()
    assertThat(second.boundArguments())
      .containsExactly(NativeValue.Integer(13L), NativeValue.Integer(1L))
      .inOrder()
    val rows = mock<Cursor>()
    whenever(rows.getLong(0)).thenReturn(17L)
    assertThat(first.getFromCurrentPosition(rows)).isEqualTo(17L)
    assertThat(second.getFromCurrentPosition(rows)).isEqualTo(17L)
    assertThat(selection.events).containsExactly("render", "reader")
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
      reader = { cursor: Cursor, index: Int ->
        PairResult(
          number = cursor.getLong(index),
          text = cursor.getString(index + 1)
        )
      }
    )
    val node = SelectNode(selection)
    val select = compileSelect(node)
    val first = compileFirst(node)
    val cursor = compileCursor(node)
    val readers: List<Pair<String, (Cursor) -> PairResult?>> = listOf(
      "select" to { rows ->
        select.mapRows(rows)
          .single()
      },
      "first" to { rows -> first.mapRows(rows) },
      "cursor" to cursor::getFromCurrentPosition
    )
    val expected = PairResult(
      number = 11L,
      text = "eleven"
    )

    assertThat(select.sql).isEqualTo("SELECT 11, 'eleven'")
    readers.forEach { (terminal, read) ->
      val rows = mock<Cursor>()
      whenever(rows.moveToNext()).thenReturn(true, false)
      whenever(rows.getLong(0)).thenReturn(11L)
      whenever(rows.getString(1)).thenReturn("eleven")
      assertWithMessage(terminal)
        .that(read(rows))
        .isEqualTo(expected)
    }
    assertThat(selection.events)
      .containsExactly("render", "reader", "render", "reader", "render", "reader")
      .inOrder()
  }

  @Test
  fun `rendering failures never request a reader`() {
    val terminals: List<Pair<String, (SelectNode<Long, Selection<Long>>) -> SelectPlan<*>>> = listOf(
      "select" to ::compileSelect,
      "first" to ::compileFirst,
      "cursor" to ::compileCursor
    )
    val output = 1L.asValue().node

    terminals.forEach { (terminal, compile) ->
      val selection = RecordingSelection(
        output = output,
        reader = Cursor::getLong,
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
  fun `reading terminals close their cursor while cursor terminals leave it to the caller`() {
    data class Case(
      val label: String,
      val rows: Int,
      val read: (Cursor) -> Any?
    )

    val node = SelectNode(
      RecordingSelection(
        output = 7L.asValue().node,
        reader = Cursor::getLong
      )
    )
    val failing = SelectNode(
      RecordingSelection<Long>(
        output = 7L.asValue().node,
        reader = { _, _ -> error("read failed") }
      )
    )
    val cursor = compileCursor(node)
    val cases = listOf(
      Case(
        label = "select",
        rows = 2,
        read = compileSelect(node)::mapRows
      ),
      Case(
        label = "first",
        rows = 1,
        read = compileFirst(node)::mapRows
      ),
      Case(
        label = "empty first",
        rows = 0,
        read = compileFirst(node)::mapRows
      ),
      Case(
        label = "count",
        rows = 1,
        read = compileCount(node)::mapRows
      ),
      Case(
        label = "failed select read",
        rows = 1,
        read = { rows -> runCatching { compileSelect(failing).mapRows(rows) }.exceptionOrNull()?.message }
      ),
      Case(
        label = "cursor",
        rows = 1,
        read = { rows -> cursor.mapRows(rows) === rows }
      )
    )

    assertThat(
      cases.map { case ->
        var position = -1
        val rows = mock<Cursor>()
        whenever(rows.count).thenReturn(case.rows)
        whenever(rows.moveToNext()).thenAnswer { ++position < case.rows }
        whenever(rows.getLong(0)).thenReturn(7L)
        val result = case.read(rows)
        val closes = mockingDetails(rows).invocations.count { it.method.name == "close" }
        case.label to (result to closes)
      }
    ).isEqualTo(
      listOf(
        "select" to (listOf(7L, 7L) to 1),
        "first" to (7L to 1),
        "empty first" to (null to 1),
        "count" to (7L to 1),
        "failed select read" to ("read failed" to 1),
        "cursor" to (true to 0)
      )
    )
  }

  private class RecordingSelection<T>(
    private val output: SqlRenderable,
    private val reader: (Cursor, Int) -> T,
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
