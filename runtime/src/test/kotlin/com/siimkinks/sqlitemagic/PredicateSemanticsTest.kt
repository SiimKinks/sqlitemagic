package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.SqlType.BOOLEAN
import com.siimkinks.sqlitemagic.SqlType.INT
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Test

internal class PredicateSemanticsTest {
  @Test
  fun `boolean operators project SQLite three-valued logic`() {
    val operands = listOf<SqlExpression<Boolean, *, Boolean?>>(
      true.asValue(),
      false.asValue(),
      (null as Boolean?).asValue()
    )
    val operators = listOf<Pair<String, BooleanOperator>>(
      "AND" to { left, right -> left AND right },
      "OR" to { left, right -> left OR right },
      "AND_NOT" to { left, right -> left AND_NOT right },
      "OR_NOT" to { left, right -> left OR_NOT right }
    )
    val expected = mapOf(
      "AND" to listOf(true, false, null, false, false, false, null, false, null),
      "OR" to listOf(true, true, true, true, false, null, true, null, null),
      "AND_NOT" to listOf(false, true, null, false, false, false, false, null, null),
      "OR_NOT" to listOf(true, true, true, false, true, null, null, true, null)
    )

    personDatabase().use { database ->
      val connection = database.dbConnection()
      val actual = operators.associate { (label, operator) ->
        label to operands.flatMap { left ->
          operands.map { right ->
            SELECT.COLUMN(operator(left, right))
              .usingConnection(connection)
              .execute()
              .single()
          }
        }
      }
      val negated = operands.map { operand ->
        SELECT.COLUMN(!operand)
          .usingConnection(connection)
          .execute()
          .single()
      }

      assertThat(actual).isEqualTo(expected)
      assertThat(negated).isEqualTo(listOf(false, true, null))
    }
  }

  @Test
  fun `predicate projections read SQLite truth and NULL predicates stay required`() {
    personDatabase().use { database ->
      val connection = database.dbConnection()
      val comparisons = SELECT
        .COLUMNS(PERSON.ID, PERSON.NAME IS "Ada")
        .FROM(PERSON)
        .ORDER_BY(PERSON.ID.ASC())
        .usingConnection(connection)
        .execute()
      val nullTests = SELECT.COLUMN(PERSON.NAME.IS_NULL())
        .FROM(PERSON)
        .ORDER_BY(PERSON.ID.ASC())
        .usingConnection(connection)
        .execute()
      val nullOperand = SELECT.COLUMN(NULL.IS_NOT_NULL())
        .usingConnection(connection)
        .execute()

      assertThat(comparisons).isEqualTo(
        listOf(
          Row2(1L, true),
          Row2(2L, false),
          Row2(3L, null),
          Row2(4L, false)
        )
      )
      assertThat(nullTests).isEqualTo(listOf(false, false, true, false))
      assertThat(nullOperand).isEqualTo(listOf(false))
    }
  }

  @Test
  fun `raw expressions of encoding types compare with host values through their encoders`() {
    val token = "person.token".expr(TOKEN_TYPE)
    val lowerName = "lower(person.name)".expr(STRING)
    val query = SELECT.COLUMN(PERSON.ID)
      .FROM(PERSON)
      .WHERE((token IS Token("g")) OR (lowerName IN listOf("linus")))
      .ORDER_BY(PERSON.ID.ASC())

    assertThat(snapshot(query.compile())).isEqualTo(
      PlanSnapshot(
        sql = """SELECT "person"."id" FROM "main"."person" """ +
            """WHERE ((person.token) = ?) OR ((lower(person.name)) IN (?)) ORDER BY "person"."id" ASC""",
        arguments = listOf(
          NativeValue.Text("db:g"),
          NativeValue.Text("linus")
        )
      )
    )
    assertThat(query.rowsIn(::personDatabase)).isEqualTo(listOf(2L, 4L))
  }

  @Test
  fun `operators render grouped SQL and bind host values through the receiver encoder`() {
    data class Case(
      val label: String,
      val predicate: SqlExpression<Boolean, *, *>,
      val where: String
    )

    val years = PERSON.AGE.AS("years")
    val label = PERSON.TOKEN.AS("label")
    val rawFlag = "person.flag = ?".expr(BOOLEAN, true)
      .observes(PERSON)
    val prefix = """SELECT "person"."id" FROM "main"."person" WHERE """
    val cases = listOf(
      Case(
        label = "IS",
        predicate = PERSON.ID IS PERSON.PARENT_ID,
        where = """"person"."id" = "person"."parent_id""""
      ),
      Case(
        label = "IS_NOT",
        predicate = PERSON.NAME IS_NOT "Ada",
        where = """"person"."name" != ?"""
      ),
      Case(
        label = "GREATER_THAN",
        predicate = PERSON.AGE GREATER_THAN 1,
        where = """"person"."age" > ?"""
      ),
      Case(
        label = "GREATER_OR_EQUAL",
        predicate = PERSON.AGE GREATER_OR_EQUAL 2,
        where = """"person"."age" >= ?"""
      ),
      Case(
        label = "LESS_THAN",
        predicate = PERSON.ID LESS_THAN PERSON.AGE,
        where = """"person"."id" < "person"."age""""
      ),
      Case(
        label = "LESS_OR_EQUAL",
        predicate = PERSON.AGE LESS_OR_EQUAL 3,
        where = """"person"."age" <= ?"""
      ),
      Case(
        label = "IS_NULL",
        predicate = PERSON.NAME.IS_NULL(),
        where = """"person"."name" IS NULL"""
      ),
      Case(
        label = "IS_NOT_NULL",
        predicate = PERSON.NAME.IS_NOT_NULL(),
        where = """"person"."name" IS NOT NULL"""
      ),
      Case(
        label = "NOT field",
        predicate = !PERSON.FLAG,
        where = """NOT "person"."flag""""
      ),
      Case(
        label = "negated connectives",
        predicate = (PERSON.FLAG AND_NOT PERSON.FLAG.IS_NULL()) OR_NOT (PERSON.NAME IS NULL),
        where = """("person"."flag" AND NOT ("person"."flag" IS NULL)) OR NOT ("person"."name" = NULL)"""
      ),
      Case(
        label = "predicate operands of comparisons and NULL tests",
        predicate = ((PERSON.AGE GREATER_THAN 18) IS (PERSON.ID LESS_THAN 9L)).IS_NOT_NULL(),
        where = """(("person"."age" > ?) = ("person"."id" < ?)) IS NOT NULL"""
      ),
      Case(
        label = "IN host values",
        predicate = PERSON.ID IN listOf(1L, 2L),
        where = """"person"."id" IN (?, ?)"""
      ),
      Case(
        label = "empty IN",
        predicate = PERSON.ID IN emptyList(),
        where = """"person"."id" IN ()"""
      ),
      Case(
        label = "NOT_IN typed operands",
        predicate = PERSON.ID.NOT_IN(PERSON.PARENT_ID, 5L.asValue()),
        where = """"person"."id" NOT IN ("person"."parent_id", ?)"""
      ),
      Case(
        label = "BETWEEN",
        predicate = PERSON.AGE BETWEEN 1 AND PERSON.AGE,
        where = """"person"."age" BETWEEN ? AND "person"."age""""
      ),
      Case(
        label = "NOT_BETWEEN",
        predicate = PERSON.AGE NOT_BETWEEN 1 AND 2,
        where = """"person"."age" NOT BETWEEN ? AND ?"""
      ),
      Case(
        label = "LIKE",
        predicate = PERSON.NAME LIKE "a%",
        where = """"person"."name" LIKE ?"""
      ),
      Case(
        label = "NOT_LIKE ESCAPE",
        predicate = PERSON.NAME NOT_LIKE "a!%" ESCAPE "!",
        where = """"person"."name" NOT LIKE ? ESCAPE ?"""
      ),
      Case(
        label = "GLOB",
        predicate = PERSON.NAME GLOB "a*",
        where = """"person"."name" GLOB ?"""
      ),
      Case(
        label = "NOT_GLOB",
        predicate = PERSON.NAME NOT_GLOB "a*",
        where = """"person"."name" NOT GLOB ?"""
      ),
      Case(
        label = "membership, range and pattern operands",
        predicate = (PERSON.NAME LIKE "a%" ESCAPE "!") OR !(PERSON.ID IN listOf(1L)),
        where = """("person"."name" LIKE ? ESCAPE ?) OR (NOT ("person"."id" IN (?)))"""
      ),
      Case(
        label = "operator and raw operands inside ranges and lists",
        predicate = ("person.age".expr(INT).observes(PERSON) BETWEEN PERSON.AGE AND 9.asValue()) AND
            (PERSON.AGE GREATER_THAN 1).IN(PERSON.FLAG),
        where = """((person.age) BETWEEN "person"."age" AND ?) AND (("person"."age" > ?) IN ("person"."flag"))"""
      )
    )

    cases.forEach { case ->
      assertWithMessage(case.label)
        .that(
          SELECT.COLUMN(PERSON.ID)
            .FROM(PERSON)
            .WHERE(case.predicate)
            .compile()
            .sql
        )
        .isEqualTo(prefix + case.where)
    }
    val nested = SELECT.COLUMN(label IS Token("projected"))
      .FROM(PERSON)
      .WHERE(((label IS Token("g")) AND !(years LESS_THAN 40)) OR rawFlag)
      .GROUP_BY(PERSON.FLAG IS_NOT false)
      .HAVING(years GREATER_THAN 7)
      .ORDER_BY((PERSON.NAME IS "last").DESC())
    assertThat(snapshot(nested.compile())).isEqualTo(
      PlanSnapshot(
        sql = """SELECT "person"."token" = ? FROM "main"."person" """ +
            """WHERE (("person"."token" = ?) AND (NOT ("person"."age" < ?))) OR (person.flag = ?) """ +
            """GROUP BY "person"."flag" != ? HAVING "person"."age" > ? ORDER BY "person"."name" = ? DESC""",
        arguments = listOf(
          NativeValue.Text("db:projected"),
          NativeValue.Text("db:g"),
          NativeValue.Integer(40),
          NativeValue.Integer(1),
          NativeValue.Integer(0),
          NativeValue.Integer(7),
          NativeValue.Text("last")
        )
      )
    )
  }

  @Test
  fun `empty membership and NULL operands keep SQLite semantics`() {
    val nullReceiver = (null as Long?).asValue()
    val predicates = listOf(
      nullReceiver IN emptyList(),
      nullReceiver NOT_IN emptyList(),
      nullReceiver IN listOf(1L),
      1L.asValue().IN(2L.asValue(), NULL),
      1L.asValue().NOT_IN(2L.asValue(), NULL),
      1L.asValue().IN(1L.asValue(), NULL),
      1L.asValue() BETWEEN NULL AND 0L
    )

    personDatabase().use { database ->
      val connection = database.dbConnection()
      val results = predicates.map { predicate ->
        SELECT.COLUMN(predicate)
          .usingConnection(connection)
          .execute()
          .single()
      }

      assertThat(results).isEqualTo(listOf(false, true, null, null, null, true, false))
    }
  }

  @Test
  fun `ESCAPE matches literal wildcards and SQLite rejects an invalid escape`() {
    personDatabase().use { database ->
      val connection = database.dbConnection()
      val matches = listOf("50%", "500").map { value ->
        SELECT.COLUMN(value.asValue() LIKE "50!%" ESCAPE "!")
          .usingConnection(connection)
          .execute()
          .single()
      }
      val invalid = SELECT.COLUMN("a".asValue() LIKE "a" ESCAPE "ab")
        .usingConnection(connection)

      val failure = sqliteErrorMessage(invalid::execute)

      assertThat(matches).isEqualTo(listOf(true, false))
      assertThat(failure).contains("ESCAPE expression must be a single character")
    }
  }
}

private typealias BooleanOperator =
      (SqlExpression<Boolean, *, *>, SqlExpression<Boolean, *, *>) -> SqlExpression<Boolean, SqlNullable, Boolean?>
