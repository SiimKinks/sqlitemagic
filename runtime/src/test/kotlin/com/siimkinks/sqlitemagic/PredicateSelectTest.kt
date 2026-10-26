package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlType.BOOLEAN
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class PredicateSelectTest(private val case: PredicateCase) {
  class PredicateCase(
    private val label: String,
    val predicate: () -> SqlExpression<Boolean, *, *>,
    val expectedIds: List<Long>
  ) {
    override fun toString() = label
  }

  @Test
  fun `WHERE returns the rows SQLite accepts`() {
    personDatabase().use { database ->
      val ids = SELECT.COLUMN(PERSON.ID)
        .FROM(PERSON)
        .WHERE(case.predicate())
        .ORDER_BY(PERSON.ID.ASC())
        .usingConnection(database.dbConnection())
        .execute()

      assertThat(ids).isEqualTo(case.expectedIds)
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun cases() = listOf(
      PredicateCase(
        label = "IS host value",
        predicate = { PERSON.NAME IS "Ada" },
        expectedIds = listOf(1L)
      ),
      PredicateCase(
        label = "IS_NOT host value skips SQL NULL",
        predicate = { PERSON.NAME IS_NOT "Ada" },
        expectedIds = listOf(2L, 4L)
      ),
      PredicateCase(
        label = "GREATER_THAN host value",
        predicate = { PERSON.AGE GREATER_THAN 17 },
        expectedIds = listOf(1L, 2L)
      ),
      PredicateCase(
        label = "GREATER_OR_EQUAL host value",
        predicate = { PERSON.AGE.GREATER_OR_EQUAL(36) },
        expectedIds = listOf(1L, 2L)
      ),
      PredicateCase(
        label = "LESS_THAN host value",
        predicate = { PERSON.AGE LESS_THAN 36 },
        expectedIds = listOf(3L, 4L)
      ),
      PredicateCase(
        label = "LESS_OR_EQUAL host value",
        predicate = { PERSON.AGE.LESS_OR_EQUAL(17) },
        expectedIds = listOf(3L, 4L)
      ),
      PredicateCase(
        label = "IS field operand",
        predicate = { PERSON.ID IS PERSON.PARENT_ID },
        expectedIds = listOf(4L)
      ),
      PredicateCase(
        label = "numeric widening Long LESS_THAN Int",
        predicate = { PERSON.PARENT_ID LESS_THAN PERSON.AGE },
        expectedIds = listOf(2L, 3L, 4L)
      ),
      PredicateCase(
        label = "custom encoder IS",
        predicate = { PERSON.TOKEN IS Token("g") },
        expectedIds = listOf(2L)
      ),
      PredicateCase(
        label = "custom encoder orders by stored representation",
        predicate = { PERSON.TOKEN GREATER_THAN Token("g") },
        expectedIds = listOf(3L, 4L)
      ),
      PredicateCase(
        label = "aliased custom field keeps its encoder",
        predicate = { PERSON.TOKEN.AS("label") IS Token("l") },
        expectedIds = listOf(4L)
      ),
      PredicateCase(
        label = "alias named like another column keeps its expression",
        predicate = { (PERSON.AGE AS "name") IS 17 },
        expectedIds = listOf(3L, 4L)
      ),
      PredicateCase(
        label = "bound value operand",
        predicate = { PERSON.ID IS_NOT 1L.asValue() },
        expectedIds = listOf(2L, 3L, 4L)
      ),
      PredicateCase(
        label = "SQL NULL operand never matches",
        predicate = { PERSON.NAME IS NULL },
        expectedIds = emptyList()
      ),
      PredicateCase(
        label = "IS_NULL",
        predicate = { PERSON.NAME.IS_NULL() },
        expectedIds = listOf(3L)
      ),
      PredicateCase(
        label = "IS_NOT_NULL",
        predicate = { PERSON.NAME.IS_NOT_NULL() },
        expectedIds = listOf(1L, 2L, 4L)
      ),
      PredicateCase(
        label = "AND",
        predicate = { (PERSON.AGE GREATER_THAN 17) AND (PERSON.NAME IS "Grace") },
        expectedIds = listOf(2L)
      ),
      PredicateCase(
        label = "OR",
        predicate = { (PERSON.NAME IS "Ada") OR (PERSON.PARENT_ID IS 2L) },
        expectedIds = listOf(1L, 3L)
      ),
      PredicateCase(
        label = "AND_NOT",
        predicate = { (PERSON.AGE LESS_OR_EQUAL 17) AND_NOT (PERSON.ID IS 4L) },
        expectedIds = listOf(3L)
      ),
      PredicateCase(
        label = "OR_NOT",
        predicate = { (PERSON.NAME IS "Ada") OR_NOT (PERSON.AGE GREATER_THAN 17) },
        expectedIds = listOf(1L, 3L, 4L)
      ),
      PredicateCase(
        label = "nullable Boolean field",
        predicate = { PERSON.FLAG IS true },
        expectedIds = listOf(1L, 4L)
      ),
      PredicateCase(
        label = "raw operand keeps its own grouping",
        predicate = {
          "person.name = 'Ada' OR person.age < 18".expr(BOOLEAN)
            .observes(PERSON) AND (PERSON.ID GREATER_THAN 1L)
        },
        expectedIds = listOf(3L, 4L)
      ),
      PredicateCase(
        label = "Kotlin infix order becomes SQL grouping",
        predicate = { (PERSON.NAME IS "Ada") OR (PERSON.AGE IS 17) AND (PERSON.ID IS 4L) },
        expectedIds = listOf(4L)
      ),
      PredicateCase(
        label = "IN host collection",
        predicate = { PERSON.ID IN listOf(1L, 3L) },
        expectedIds = listOf(1L, 3L)
      ),
      PredicateCase(
        label = "NOT_IN host array",
        predicate = { PERSON.ID NOT_IN arrayOf(1L, 3L) },
        expectedIds = listOf(2L, 4L)
      ),
      PredicateCase(
        label = "IN host varargs",
        predicate = { PERSON.AGE.IN(17, 85) },
        expectedIds = listOf(2L, 3L, 4L)
      ),
      PredicateCase(
        label = "NOT_IN skips a NULL receiver",
        predicate = { PERSON.NAME NOT_IN listOf("Ada") },
        expectedIds = listOf(2L, 4L)
      ),
      PredicateCase(
        label = "IN through a custom encoder",
        predicate = { PERSON.TOKEN IN listOf(Token("g"), Token("l")) },
        expectedIds = listOf(2L, 4L)
      ),
      PredicateCase(
        label = "IN typed operands",
        predicate = { PERSON.ID.IN(PERSON.PARENT_ID, 3L.asValue()) },
        expectedIds = listOf(3L, 4L)
      ),
      PredicateCase(
        label = "NOT_IN a NULL typed operand stays unknown",
        predicate = { PERSON.ID.NOT_IN(PERSON.PARENT_ID) },
        expectedIds = listOf(2L, 3L)
      ),
      PredicateCase(
        label = "numeric widening IN",
        predicate = { PERSON.ID.IN(1.asValue(), PERSON.AGE) },
        expectedIds = listOf(1L)
      ),
      PredicateCase(
        label = "BETWEEN host bounds",
        predicate = { PERSON.AGE BETWEEN 17 AND 36 },
        expectedIds = listOf(1L, 3L, 4L)
      ),
      PredicateCase(
        label = "NOT_BETWEEN host bounds",
        predicate = { PERSON.AGE NOT_BETWEEN 17 AND 36 },
        expectedIds = listOf(2L)
      ),
      PredicateCase(
        label = "BETWEEN keeps the supplied bound order",
        predicate = { PERSON.AGE BETWEEN 36 AND 17 },
        expectedIds = emptyList()
      ),
      PredicateCase(
        label = "BETWEEN host lower and typed upper",
        predicate = { PERSON.ID BETWEEN 2L AND PERSON.PARENT_ID },
        expectedIds = listOf(4L)
      ),
      PredicateCase(
        label = "BETWEEN typed lower and host upper",
        predicate = { PERSON.ID BETWEEN PERSON.PARENT_ID AND 3L },
        expectedIds = listOf(2L, 3L)
      ),
      PredicateCase(
        label = "BETWEEN text bounds",
        predicate = { PERSON.NAME BETWEEN "B" AND "H" },
        expectedIds = listOf(2L)
      ),
      PredicateCase(
        label = "BETWEEN custom encoder bounds",
        predicate = { PERSON.TOKEN BETWEEN Token("b") AND Token("h") },
        expectedIds = listOf(2L)
      ),
      PredicateCase(
        label = "numeric widening BETWEEN",
        predicate = { PERSON.AGE BETWEEN PERSON.ID AND 20 },
        expectedIds = listOf(3L, 4L)
      ),
      PredicateCase(
        label = "fluent BETWEEN",
        predicate = { PERSON.AGE.BETWEEN(18).AND(90) },
        expectedIds = listOf(1L, 2L)
      ),
      PredicateCase(
        label = "LIKE ignores ASCII case",
        predicate = { PERSON.NAME LIKE "a%" },
        expectedIds = listOf(1L)
      ),
      PredicateCase(
        label = "NOT_LIKE skips a NULL receiver",
        predicate = { PERSON.NAME NOT_LIKE "%a%" },
        expectedIds = listOf(4L)
      ),
      PredicateCase(
        label = "LIKE matches a numeric receiver as text",
        predicate = { PERSON.AGE LIKE "1%" },
        expectedIds = listOf(3L, 4L)
      ),
      PredicateCase(
        label = "LIKE matches a custom encoding's stored text",
        predicate = { PERSON.TOKEN LIKE "db:g%" },
        expectedIds = listOf(2L)
      ),
      PredicateCase(
        label = "LIKE typed pattern",
        predicate = { PERSON.NAME LIKE "G%".asValue() },
        expectedIds = listOf(2L)
      ),
      PredicateCase(
        label = "GLOB is case-sensitive",
        predicate = { PERSON.NAME GLOB "*A*" },
        expectedIds = listOf(1L)
      ),
      PredicateCase(
        label = "NOT_GLOB",
        predicate = { PERSON.NAME NOT_GLOB "A*" },
        expectedIds = listOf(2L, 4L)
      ),
      PredicateCase(
        label = "range and membership compose with Boolean logic",
        predicate = { (PERSON.AGE BETWEEN 17 AND 36) AND (PERSON.NAME NOT_IN listOf("Ada")) },
        expectedIds = listOf(4L)
      ),
      PredicateCase(
        label = "a range completes before a following Boolean AND",
        predicate = { PERSON.AGE BETWEEN 17 AND 36 AND (PERSON.ID GREATER_THAN 1L) },
        expectedIds = listOf(3L, 4L)
      ),
      PredicateCase(
        label = "arithmetic receiver >",
        predicate = { (PERSON.AGE + 1) GREATER_THAN 18 },
        expectedIds = listOf(1L, 2L)
      ),
      PredicateCase(
        label = "arithmetic receiver IN",
        predicate = { (PERSON.AGE + 1) IN listOf(18, 86) },
        expectedIds = listOf(2L, 3L, 4L)
      ),
      PredicateCase(
        label = "arithmetic receiver BETWEEN",
        predicate = { (PERSON.AGE * 2) BETWEEN 30 AND 70.5 },
        expectedIds = listOf(3L, 4L)
      ),
      PredicateCase(
        label = "arithmetic receiver IS a Byte",
        predicate = { (PERSON.AGE + 1) IS 18.toByte() },
        expectedIds = listOf(3L, 4L)
      )
    )
  }
}
