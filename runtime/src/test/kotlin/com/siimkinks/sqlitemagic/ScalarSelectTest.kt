package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.SqlType.BOOLEAN
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.SqlStorage
import com.siimkinks.sqlitemagic.internal.nullableReadType
import com.siimkinks.sqlitemagic.internal.parameterEncoding
import com.siimkinks.sqlitemagic.internal.requiredReadType
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

internal class ScalarSelectTest {
  private class ScalarSource(name: String, alias: String? = null) : Table<Unit>(
    name = name,
    alias = alias
  ) {
    val flag =
      Column<Unit, Boolean, Required, Boolean, Boolean, Required, Boolean>(
        table = this,
        name = """active"flag""",
        nullness = Required,
        parameterEncoding = BOOLEAN.parameterEncoding()
      )

    override infix fun AS(alias: String) = ScalarSource(
      name = name,
      alias = alias
    )
  }

  @Test
  fun `stages branch without mutating earlier stages and share their clauses`() {
    data class Case(
      val label: String,
      val base: SelectQuery<*, *>,
      val branches: List<SelectPlan<*>>,
      val expected: List<ObservedPlanSnapshot>,
      val sharedClauses: List<Any> = emptyList()
    )

    val flags = ScalarSource("flags")
    val connection = mock<DbConnectionImpl>()
    val projection = SELECT.COLUMN(7L.asValue())
    val grouped = projection.GROUP_BY("coalesce(?, 8)".expr(LONG, 8L))
    val having = grouped.HAVING(true.asValue())
    val ascending = projection.ORDER_BY(8L.asValue().ASC())
    val fluentDistinct = SELECT.DISTINCT(7L.asValue())
      .FROM(flags)
    val infixDistinct = SELECT DISTINCT 7L.asValue() FROM flags
    val rows = SELECT.COLUMNS(PERSON.ID, 2L.asValue())
    val attachedRows = rows.FROM(PERSON)
    val persons = SELECT.FROM(PERSON)
    val joined = SELECT.COLUMN(PERSON.ID)
      .FROM(PERSON)
      .LEFT_JOIN(PET)
    val projected = ObservedPlanSnapshot(
      sql = "SELECT ?",
      arguments = listOf(NativeValue.Integer(7)),
      sources = emptyList()
    )
    val distinct = ObservedPlanSnapshot(
      sql = """SELECT DISTINCT ? FROM "main"."flags"""",
      arguments = listOf(NativeValue.Integer(7)),
      sources = listOf("flags")
    )
    val personSql = """SELECT "person"."id", "person"."name", "person"."age", "person"."parent_id", """ +
        """"person"."token", "person"."flag" FROM "main"."person""""
    val joinedSql = """SELECT "person"."id" FROM "main"."person" LEFT JOIN "main"."pet""""
    val cases = listOf(
      Case(
        label = "GROUP BY and HAVING",
        base = projection,
        branches = listOf(
          grouped.compile(),
          projection
            .GROUP_BY(9L.asValue())
            .compile(),
          having.compile()
        ),
        expected = listOf(
          projected,
          projected.copy(
            sql = "SELECT ? GROUP BY coalesce(?, 8)",
            arguments = listOf(NativeValue.Integer(7), NativeValue.Integer(8))
          ),
          projected.copy(
            sql = "SELECT ? GROUP BY ?",
            arguments = listOf(NativeValue.Integer(7), NativeValue.Integer(9))
          ),
          projected.copy(
            sql = "SELECT ? GROUP BY coalesce(?, 8) HAVING ?",
            arguments = listOf(NativeValue.Integer(7), NativeValue.Integer(8), NativeValue.Integer(1))
          )
        ),
        sharedClauses = listOf(
          grouped,
          having,
          grouped.LIMIT(1),
          grouped.usingConnection(connection),
          grouped.readAs(STRING),
          having.LIMIT(1),
          having.usingConnection(connection),
          having.readAs(STRING)
        ).map { it.node.groupings }
      ),
      Case(
        label = "ORDER BY",
        base = projection,
        branches = listOf(
          ascending.compile(),
          projection
            .ORDER_BY(8L.asValue().DESC())
            .compile()
        ),
        expected = listOf(
          projected,
          projected.copy(
            sql = "SELECT ? ORDER BY ? ASC",
            arguments = listOf(NativeValue.Integer(7), NativeValue.Integer(8))
          ),
          projected.copy(
            sql = "SELECT ? ORDER BY ? DESC",
            arguments = listOf(NativeValue.Integer(7), NativeValue.Integer(8))
          )
        ),
        sharedClauses = listOf(
          ascending,
          ascending.LIMIT(1),
          ascending.usingConnection(connection),
          ascending.readAs(STRING)
        ).map { it.node.orderings }
      ),
      Case(
        label = "FROM",
        base = projection,
        branches = listOf(
          projection
            .FROM(flags)
            .compile(),
          projection
            .FROM(flags)
            .WHERE(true.asValue())
            .compile()
        ),
        expected = listOf(
          projected,
          projected.copy(
            sql = """SELECT ? FROM "main"."flags"""",
            sources = listOf("flags")
          ),
          projected.copy(
            sql = """SELECT ? FROM "main"."flags" WHERE ?""",
            arguments = listOf(NativeValue.Integer(7), NativeValue.Integer(1)),
            sources = listOf("flags")
          )
        )
      ),
      Case(
        label = "fluent DISTINCT",
        base = fluentDistinct,
        branches = listOf(
          fluentDistinct
            .WHERE(true.asValue())
            .compile()
        ),
        expected = listOf(
          distinct,
          distinct.copy(
            sql = "${distinct.sql} WHERE ?",
            arguments = listOf(NativeValue.Integer(7), NativeValue.Integer(1))
          )
        )
      ),
      Case(
        label = "infix DISTINCT",
        base = infixDistinct,
        branches = listOf(
          infixDistinct
            .WHERE(true.asValue())
            .compile()
        ),
        expected = listOf(
          distinct,
          distinct.copy(
            sql = "${distinct.sql} WHERE ?",
            arguments = listOf(NativeValue.Integer(7), NativeValue.Integer(1))
          )
        )
      ),
      Case(
        label = "row selection",
        base = attachedRows,
        branches = listOf(
          attachedRows
            .WHERE(true.asValue())
            .compile()
        ),
        expected = listOf(
          ObservedPlanSnapshot(
            sql = """SELECT "person"."id", ? FROM "main"."person"""",
            arguments = listOf(NativeValue.Integer(2)),
            sources = listOf("person")
          ),
          ObservedPlanSnapshot(
            sql = """SELECT "person"."id", ? FROM "main"."person" WHERE ?""",
            arguments = listOf(NativeValue.Integer(2), NativeValue.Integer(1)),
            sources = listOf("person")
          )
        ),
        sharedClauses = listOf(
          rows,
          attachedRows,
          attachedRows.WHERE(true.asValue())
        ).map { it.node.selection }
      ),
      Case(
        label = "entity source",
        base = persons,
        branches = listOf(
          persons
            .WHERE(PERSON.AGE GREATER_THAN 18)
            .compile(),
          persons
            .ORDER_BY(PERSON.ID.DESC())
            .compile()
        ),
        expected = listOf(
          ObservedPlanSnapshot(
            sql = personSql,
            arguments = emptyList(),
            sources = listOf("person")
          ),
          ObservedPlanSnapshot(
            sql = """$personSql WHERE "person"."age" > ?""",
            arguments = listOf(NativeValue.Integer(18)),
            sources = listOf("person")
          ),
          ObservedPlanSnapshot(
            sql = """$personSql ORDER BY "person"."id" DESC""",
            arguments = emptyList(),
            sources = listOf("person")
          )
        )
      ),
      Case(
        label = "join constraint",
        base = joined,
        branches = listOf(
          joined
            .USING(PET.ID)
            .compile(),
          joined
            .ON(PERSON.ID IS PET.OWNER)
            .compile()
        ),
        expected = listOf(
          ObservedPlanSnapshot(
            sql = joinedSql,
            arguments = emptyList(),
            sources = listOf("person", "pet")
          ),
          ObservedPlanSnapshot(
            sql = """$joinedSql USING ("id")""",
            arguments = emptyList(),
            sources = listOf("person", "pet")
          ),
          ObservedPlanSnapshot(
            sql = """$joinedSql ON "person"."id" = "pet"."owner"""",
            arguments = emptyList(),
            sources = listOf("person", "pet")
          )
        )
      )
    )

    cases.forEach { case ->
      assertWithMessage(case.label)
        .that((listOf(case.base.compile()) + case.branches).map(::observedSnapshot))
        .isEqualTo(case.expected)
      case.sharedClauses.forEach { clauses ->
        assertWithMessage(case.label)
          .that(clauses)
          .isSameInstanceAs(case.sharedClauses.first())
      }
    }
    verifyNoInteractions(connection)
  }

  @Test
  fun `grouped clauses preserve occurrence order, aliases, readers, connections and terminals`() {
    val physical = ScalarSource("physical").AS("selected_source")
    val selected = physical.flag.AS("selected_flag")
    val groupingSource = ScalarSource("grouping_source")
    val havingSource = ScalarSource("having_source")
    val orderingSource = ScalarSource("ordering_source")
    val parameter = false.asValue()
      .asParameter("repeated")
    val grouping = "coalesce(?, 200)".expr(LONG, 22L)
      .observes(groupingSource)
    val havingPredicate = "? < count(*)".expr(BOOLEAN, 44L)
      .observes(havingSource)
    val ordering = "coalesce(?, 500)".expr(LONG, 55L)
      .observes(orderingSource)
    val connection = mock<DbConnectionImpl>()
    val ordered = SELECT.DISTINCT(selected)
      .FROM(physical)
      .WHERE(parameter)
      .GROUP_BY(grouping, parameter, selected)
      .HAVING(havingPredicate)
      .ORDER_BY(selected.DESC(), ordering.ASC(), parameter.ASC())
    val paginated = ordered.LIMIT(5)
      .OFFSET(1)
      .readAs(LONG)
      .usingConnection(connection)
    val compiled = paginated.compile()
      .bind(parameter TO false)
    val sql = """SELECT DISTINCT "selected_source"."active""flag" AS "selected_flag" """ +
        """FROM "main"."physical" AS "selected_source" WHERE ? """ +
        """GROUP BY coalesce(?, 200), ?, "selected_source"."active""flag" HAVING ? < count(*) """ +
        """ORDER BY "selected_source"."active""flag" DESC, coalesce(?, 500) ASC, ? ASC"""
    val expected = ObservedPlanSnapshot(
      sql = "$sql LIMIT 5 OFFSET 1",
      arguments = listOf(
        NativeValue.Integer(0),
        NativeValue.Integer(22),
        NativeValue.Integer(0),
        NativeValue.Integer(44),
        NativeValue.Integer(55),
        NativeValue.Integer(0)
      ),
      sources = listOf("physical", "grouping_source", "having_source", "ordering_source")
    )

    assertThat(observedSnapshot(compiled)).isEqualTo(expected)
    assertThat(compiled.prepared.connection).isSameInstanceAs(connection)
    val rows = mock<Cursor>()
    whenever(rows.getLong(0)).thenReturn(2L)
    assertThat(
      paginated.toCursor()
        .getFromCurrentPosition(rows)
    ).isEqualTo(2L)
    verifyNoInteractions(connection)
  }

  @Test
  fun `readAs replaces the selected reader and keeps the source SQL nullness`() {
    data class Failure(val message: String?)

    data class Case(
      val label: String,
      val query: SelectQuery<*, *>,
      val cell: String?,
      val expected: Any?,
      val decodes: List<String>
    )

    val decodes = mutableListOf<String>()
    val sentinel = nullableReadType(
      storage = SqlStorage.STRING,
      encode = { value: String -> value },
      decode = { value ->
        decodes += "sentinel"
        value ?: "sentinel"
      },
      encodingNullness = SqlNullable
    )
    val required = requiredReadType(
      storage = SqlStorage.STRING,
      encode = { value: String -> value },
      decode = { value ->
        decodes += "required"
        value
      },
      encodingNullness = Required
    )
    val source = ScalarSource("sentinel") AS "source"
    val field = Column(
      table = source,
      name = "value",
      nullness = SqlNullable,
      parameterEncoding = sentinel.parameterEncoding(),
      selectedRead = sentinel.selectRead
    )
    val labeled = SELECT.COLUMN(field AS "label")
      .FROM(source)
    val requiredNull = SELECT.COLUMN("NULL".expr(LONG))
    val nullFailure = Failure("Required SQL result returned SQL NULL")
    val cases = listOf(
      Case(
        label = "field alias keeps the direct reader",
        query = labeled,
        cell = null,
        expected = "sentinel",
        decodes = listOf("sentinel")
      ),
      Case(
        label = "native readAs replaces the direct reader",
        query = labeled.readAs(STRING),
        cell = "value",
        expected = "value",
        decodes = emptyList()
      ),
      Case(
        label = "nullable source reads NULL without a required replacement",
        query = labeled.readAs(required),
        cell = null,
        expected = null,
        decodes = emptyList()
      ),
      Case(
        label = "required replacement decodes a value",
        query = labeled.readAs(required),
        cell = "value",
        expected = "value",
        decodes = listOf("required")
      ),
      Case(
        label = "readAs replaces a required decoder",
        query = SELECT
          .COLUMN("'value'".expr(required))
          .readAs(STRING),
        cell = "value",
        expected = "value",
        decodes = emptyList()
      ),
      Case(
        label = "required readAs rejects NULL",
        query = requiredNull.readAs(STRING),
        cell = null,
        expected = nullFailure,
        decodes = emptyList()
      ),
      Case(
        label = "later required readAs wins",
        query = requiredNull
          .readAs(STRING.nullable)
          .readAs(LONG),
        cell = null,
        expected = nullFailure,
        decodes = emptyList()
      ),
      Case(
        label = "later nullable readAs wins",
        query = requiredNull
          .readAs(LONG)
          .readAs(STRING.nullable),
        cell = null,
        expected = null,
        decodes = emptyList()
      ),
      Case(
        label = "nullable SQL stays nullable through every readAs",
        query = SELECT.COLUMN(NULL)
          .readAs(LONG)
          .usingConnection(mock<DbConnectionImpl>())
          .readAs(STRING),
        cell = null,
        expected = null,
        decodes = emptyList()
      )
    )
    val rows = mock<Cursor>()

    cases.forEach { case ->
      decodes.clear()
      whenever(rows.isNull(0)).thenReturn(case.cell == null)
      whenever(rows.getString(0)).thenReturn(case.cell)
      val read = case.query.toCursor()::getFromCurrentPosition
      val actual = when (case.expected) {
        is Failure -> Failure(sqlExceptionMessage { read(rows) })
        else -> read(rows)
      }
      assertWithMessage(case.label)
        .that(actual to decodes.toList())
        .isEqualTo(case.expected to case.decodes)
    }
  }

  @Test
  fun `fields require their exact source occurrence before connection resolution`() {
    val alias = PERSON AS "p"
    val independentAlias = PERSON AS "p"
    val owner = PERSON AS "owner"
    val connection = mock<DbConnectionImpl>()
    val cases = listOf(
      "missing source" to SELECT.COLUMN(PERSON.ID),
      "foreign projection" to SELECT.COLUMN(PET.NAME)
        .FROM(PERSON),
      "independent alias" to SELECT.COLUMN(alias.ID)
        .FROM(independentAlias),
      "second output without its source" to SELECT.COLUMNS(2L.asValue(), PERSON.ID),
      "second output of another occurrence" to SELECT
        .COLUMNS(2L.asValue(), PERSON.ID)
        .FROM(alias),
      "foreign predicate" to SELECT.COLUMN(PERSON.ID)
        .FROM(PERSON)
        .WHERE((PERSON.ID IS 1L) AND !(alias.NAME.IS_NULL())),
      "foreign grouping" to SELECT.COLUMN(PERSON.ID)
        .FROM(PERSON)
        .GROUP_BY(PET.NAME),
      "foreign HAVING" to SELECT.COLUMN(PERSON.ID)
        .FROM(PERSON)
        .GROUP_BY(PERSON.ID)
        .HAVING(PET.NAME IS "Rex"),
      "foreign ordering" to SELECT.COLUMN(PERSON.ID)
        .FROM(PERSON)
        .ORDER_BY(PET.ID.ASC()),
      "join ON before its occurrence" to SELECT.COLUMN(PET.ID)
        .FROM(PET)
        .INNER_JOIN(PERSON)
        .ON(owner.ID IS PET.OWNER)
        .INNER_JOIN(owner)
        .ON(owner.ID IS PERSON.ID),
      "foreign USING" to SELECT.COLUMN(PERSON.ID)
        .FROM(PERSON)
        .INNER_JOIN(PET)
        .USING(owner.ID)
    )

    assertThat(
      cases.map { (label, query) ->
        label to assertThrows(IllegalArgumentException::class.java) {
          query
            .usingConnection(connection)
            .execute()
        }.message
      }
    ).isEqualTo(
      cases.map { (label, _) -> label to "A field requires the exact table occurrence of its FROM, UPDATE or DELETE" }
    )
    verifyNoInteractions(connection)
  }

  @Test
  fun `selected field alias is one quoted identifier and independent from its source alias`() {
    val source = ScalarSource("""fl"ags""")
    val sourceAlias = source.AS("""source.alias"name""")
    val selected = sourceAlias.flag
      .AS("""output.alias"name""")
    val query = SELECT.COLUMN(selected)
      .FROM(sourceAlias)
      .WHERE(selected)
    val sql = """SELECT "source.alias""name"."active""flag" AS "output.alias""name" """ +
        """FROM "main"."fl""ags" AS "source.alias""name" """ +
        """WHERE "source.alias""name"."active""flag""""

    assertThat(query.compile().sql).isEqualTo(sql)
    assertThat(query.readAs(BOOLEAN).compile().sql).isEqualTo(sql)
  }

  @Test
  fun `raw expressions add only their declared tables to the query sources`() {
    val source = ScalarSource("physical")
    val other = ScalarSource("other")
    val queries = listOf(
      SELECT COLUMN 7L.asValue() FROM source,
      SELECT.COLUMN("?".expr(LONG, 8L))
        .FROM(source),
      SELECT
        .COLUMN(
          "?".expr(LONG, 9L)
            .observes(other, source)
        )
        .FROM(source),
      SELECT.COLUMN("?".expr(LONG, 10L))
    )

    assertThat(queries.map(SelectQuery<*, *>::observedTables)).isEqualTo(
      listOf(
        listOf("physical"),
        listOf("physical"),
        listOf("other", "physical"),
        emptyList()
      )
    )
  }

  @Test
  fun `terminals transform structure and preserve explicit pagination`() {
    val base = SELECT COLUMN 7L.asValue()

    assertThat(
      listOf(
        base.compile(),
        base.takeFirst(),
        base.LIMIT(0L)
          .takeFirst(),
        base.LIMIT(-1L)
          .OFFSET(Long.MAX_VALUE)
          .takeFirst(),
        base.LIMIT(2)
          .OFFSET(-1)
          .count(),
        base.toCursor()
      )
        .map(SelectPlan<*>::sql)
    ).isEqualTo(
      listOf(
        "SELECT ?",
        "SELECT ? LIMIT 1",
        "SELECT ? LIMIT 0",
        "SELECT ? LIMIT -1 OFFSET 9223372036854775807",
        "SELECT count(*) FROM (SELECT ? LIMIT 2 OFFSET -1)",
        "SELECT ?"
      )
    )
  }

  @Test
  fun `mixed expression bindings follow SQL order and literals consume no positions`() {
    data class Case(
      val label: String,
      val query: SelectQuery<Boolean, ScalarSelection<Boolean, Required, Boolean>>,
      val expected: PlanSnapshot
    )

    val parameter = true
      .asValue()
      .asParameter("flag")
    val cases = listOf(
      Case(
        label = "parameter before raw arguments",
        query = SELECT.COLUMN(parameter)
          .WHERE("? OR ?".expr(BOOLEAN, false, true)),
        expected = PlanSnapshot(
          sql = "SELECT ? WHERE ? OR ?",
          arguments = listOf(NativeValue.Integer(1), NativeValue.Integer(0), NativeValue.Integer(1))
        )
      ),
      Case(
        label = "raw arguments before parameter",
        query = SELECT.COLUMN("coalesce(?, ?)".expr(BOOLEAN, false, true))
          .WHERE(parameter),
        expected = PlanSnapshot(
          sql = "SELECT coalesce(?, ?) WHERE ?",
          arguments = listOf(NativeValue.Integer(0), NativeValue.Integer(1), NativeValue.Integer(1))
        )
      ),
      Case(
        label = "literal before parameter",
        query = SELECT.COLUMN(false.asLiteral())
          .WHERE(parameter),
        expected = PlanSnapshot(
          sql = "SELECT 0 WHERE ?",
          arguments = listOf(NativeValue.Integer(1))
        )
      ),
      Case(
        label = "parameter before literal",
        query = SELECT.COLUMN(parameter)
          .WHERE(false.asLiteral()),
        expected = PlanSnapshot(
          sql = "SELECT ? WHERE 0",
          arguments = listOf(NativeValue.Integer(1))
        )
      ),
      Case(
        label = "immediate before parameter",
        query = SELECT.COLUMN(false.asValue())
          .WHERE(parameter),
        expected = PlanSnapshot(
          sql = "SELECT ? WHERE ?",
          arguments = listOf(NativeValue.Integer(0), NativeValue.Integer(1))
        )
      ),
      Case(
        label = "parameter before immediate",
        query = SELECT.COLUMN(parameter)
          .WHERE(false.asValue()),
        expected = PlanSnapshot(
          sql = "SELECT ? WHERE ?",
          arguments = listOf(NativeValue.Integer(1), NativeValue.Integer(0))
        )
      )
    )

    cases.forEach { case ->
      assertWithMessage(case.label)
        .that(
          snapshot(
            case.query.compile()
              .bind(parameter TO true)
          )
        )
        .isEqualTo(case.expected)
    }
  }

  @Test
  fun `source-free observation is cold and emits one mapped result per subscription`() {
    val connection = mock<DbConnectionImpl>()
    val database = mock<SupportSQLiteDatabase>()
    val cursor = mock<Cursor>()
    whenever(connection.readableDatabase).thenReturn(database)
    whenever(connection.queryScheduler).thenReturn(Schedulers.trampoline())
    whenever(connection.ensureNotInTransaction).thenReturn(Consumer { })
    whenever(database.query(any<SupportSQLiteQuery>())).thenReturn(cursor)
    whenever(cursor.count).thenReturn(1)
    whenever(cursor.moveToNext()).thenReturn(true, false, true, false)
    whenever(cursor.getLong(0)).thenReturn(8L)
    val observable = SELECT.COLUMN(8L.asValue())
      .usingConnection(connection)
      .observe()
      .runQuery()
    verifyNoInteractions(connection)

    observable
      .test()
      .assertResult(listOf(8L))
    observable
      .test()
      .assertResult(listOf(8L))
    verify(cursor, times(2)).close()
  }
}
