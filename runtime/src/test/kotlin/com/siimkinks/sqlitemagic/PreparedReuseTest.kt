package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.nullBypassParameterEncoding
import com.siimkinks.sqlitemagic.internal.parameterEncoding
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Test

internal class PreparedReuseTest {
  @Test
  fun `descriptors reuse their parameter encodings and nullable forms`() {
    assertThat(LONG.parameterEncoding()).isSameInstanceAs(LONG.parameterEncoding())
    assertThat(STRING.nullBypassParameterEncoding()).isSameInstanceAs(STRING.nullBypassParameterEncoding())
    assertThat(TOKEN_TYPE.parameterEncoding()).isSameInstanceAs(TOKEN_TYPE.parameterEncoding())
    assertThat(LONG.nullable).isSameInstanceAs(LONG.nullable)
    assertThat(TOKEN_TYPE.nullable).isSameInstanceAs(TOKEN_TYPE.nullable)
  }

  @Test
  fun `bound values are the bindings and immediate queries reuse them`() {
    val prepared = SELECT
      .COLUMN(PERSON.NAME)
      .FROM(PERSON)
      .WHERE(PERSON.AGE IS 30)
      .compile()
      .prepared

    assertThat(prepared.bindings).isEqualTo(listOf(NativeValue.Integer(30)))
    assertThat(prepared.arguments(emptyList())).isSameInstanceAs(prepared.bindings)
  }

  @Test
  fun `tables reuse their query source across compilations`() {
    fun source() = QueryDependencies
      .Builder()
      .also(PERSON::addDependencies)
      .build()
      .directSources
      .single()

    assertThat(source()).isSameInstanceAs(source())
  }

  @Test
  fun `compiled plans bind their parameters per execution in every operand position`() {
    data class Case(
      val label: String,
      val query: SelectQuery<*, *>,
      val executions: List<Pair<List<ParameterAssignment>, List<Any?>>>
    )

    val name = PERSON.NAME.asParameter("name")
    val age = PERSON.AGE.asParameter("age")
    val pattern = PERSON.NAME.asParameter("pattern")
    val delta = PERSON.AGE.asParameter("delta")
    val title = BOOK.TITLE.asParameter("title")
    val cases = listOf(
      Case(
        label = "comparison",
        query = SELECT.COLUMN(PERSON.NAME)
          .FROM(PERSON)
          .WHERE((PERSON.NAME IS name) OR ((PERSON.AGE GREATER_THAN age) AND (PERSON.NAME IS_NOT name)))
          .ORDER_BY(PERSON.ID.ASC()),
        executions = listOf(
          listOf(name TO "Grace", age TO 30) to listOf("Ada", "Grace"),
          listOf(name TO "Ada", age TO 90) to listOf("Ada")
        )
      ),
      Case(
        label = "pattern",
        query = SELECT.COLUMN(PERSON.NAME)
          .FROM(PERSON)
          .WHERE(PERSON.NAME LIKE pattern)
          .ORDER_BY(PERSON.ID.ASC()),
        executions = listOf(
          listOf(pattern TO "%a%") to listOf("Ada", "Grace"),
          listOf(pattern TO "L%") to listOf("Linus")
        )
      ),
      Case(
        label = "arithmetic",
        query = SELECT.COLUMN(PERSON.AGE + delta)
          .FROM(PERSON)
          .ORDER_BY(PERSON.ID.ASC())
          .readAs(LONG),
        executions = listOf(
          listOf(delta TO 10) to listOf(46L, 95L, 27L, 27L),
          listOf(delta TO -17) to listOf(19L, 68L, 0L, 0L)
        )
      ),
      Case(
        label = "nested IN",
        query = (SELECT COLUMN AUTHOR.NAME FROM AUTHOR)
          .WHERE(AUTHOR.ID IN (SELECT COLUMN BOOK.AUTHOR FROM BOOK WHERE (BOOK.TITLE IS title))),
        executions = listOf(
          listOf(title TO "Emma") to listOf("Grace"),
          listOf(title TO "Kernel") to listOf("Linus")
        )
      )
    )

    libraryDatabase().use { database ->
      val connection = database.dbConnection()
      cases.forEach { case ->
        val compiled = case.query
          .usingConnection(connection)
          .compile()
        assertWithMessage(case.label)
          .that(
            case.executions.map { (bindings, _) ->
              compiled.bind(*bindings.toTypedArray())
                .execute()
            }
          )
          .isEqualTo(case.executions.map(Pair<*, List<Any?>>::second))
      }
    }
  }
}
