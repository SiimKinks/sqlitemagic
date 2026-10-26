package com.siimkinks.sqlitemagic.dsl

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AND
import com.siimkinks.sqlitemagic.DELETE
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.OR
import com.siimkinks.sqlitemagic.SqlType.BOOLEAN
import com.siimkinks.sqlitemagic.expr
import org.junit.jupiter.api.Test

internal class DeleteTest {
  @Test
  fun `DELETE FROM builder renders SQL`() {
    (DELETE FROM ENTITY_WITH_RELATIONSHIP)
      .assertSql("""DELETE FROM "main"."entity_with_relationship"""")
  }

  @Test
  fun `DELETE WHERE builder renders SQL`() {
    (DELETE
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (ENTITY_WITH_RELATIONSHIP.VALUE IS "asd"))
      .assertSql(
        """DELETE FROM "main"."entity_with_relationship" WHERE "entity_with_relationship"."value" = ?"""
      )

    (DELETE
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (
        ((ENTITY_WITH_RELATIONSHIP.VALUE IS "asd") AND ENTITY_WITH_RELATIONSHIP.VALUE.IS_NOT_NULL())
            OR (ENTITY_WITH_RELATIONSHIP.COUNT GREATER_THAN 2)))
      .assertSql(
        """DELETE FROM "main"."entity_with_relationship" WHERE """ +
            """(("entity_with_relationship"."value" = ?) AND ("entity_with_relationship"."value" IS NOT NULL)) """ +
            """OR ("entity_with_relationship"."count" > ?)"""
      )

    (DELETE
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (
        ((((ENTITY_WITH_RELATIONSHIP.VALUE IS "asd") AND ENTITY_WITH_RELATIONSHIP.VALUE.IS_NOT_NULL())
            AND (ENTITY_WITH_RELATIONSHIP.COUNT IS 2))
            AND (((ENTITY_WITH_RELATIONSHIP.ID IS 2) OR
            ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.IS_NOT_NULL()) OR
            (ENTITY_WITH_RELATIONSHIP.COUNT LESS_THAN 55)))
            OR (((ENTITY_WITH_RELATIONSHIP.ID GREATER_THAN 2) AND
            ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.IS_NULL()) AND
            (ENTITY_WITH_RELATIONSHIP.COUNT IS_NOT 55))))
      .assertSql(
        """DELETE FROM "main"."entity_with_relationship" WHERE """ +
            """(((("entity_with_relationship"."value" = ?) AND """ +
            """("entity_with_relationship"."value" IS NOT NULL)) AND ("entity_with_relationship"."count" = ?)) """ +
            """AND ((("entity_with_relationship"."id" = ?) OR """ +
            """("entity_with_relationship"."related_entity" IS NOT NULL)) OR """ +
            """("entity_with_relationship"."count" < ?))) """ +
            """OR ((("entity_with_relationship"."id" > ?) AND """ +
            """("entity_with_relationship"."related_entity" IS NULL)) AND """ +
            """("entity_with_relationship"."count" != ?))"""
      )
  }

  @Test
  fun `DELETE raw WHERE builder renders SQL`() {
    (DELETE
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE "entity_with_relationship.value IS NOT NULL".expr(BOOLEAN))
      .assertSql("""DELETE FROM "main"."entity_with_relationship" WHERE entity_with_relationship.value IS NOT NULL""")
    (DELETE
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE "entity_with_relationship.value = ?".expr(BOOLEAN, "foo"))
      .assertSql("""DELETE FROM "main"."entity_with_relationship" WHERE entity_with_relationship.value = ?""")
  }

  @Test
  fun `raw statements keep their SQL`() {
    val all = "DELETE FROM entity_with_relationship"
    val filtered = "DELETE FROM book WHERE book.title = ?"

    assertThat(
      mapOf(
        "infix" to (DELETE RAW all)
          .compile()
          .sql,
        "arguments" to DELETE
          .RAW(filtered, "foo")
          .compile()
          .sql
      )
    ).isEqualTo(
      mapOf(
        "infix" to all,
        "arguments" to filtered
      )
    )
  }
}
