package com.siimkinks.sqlitemagic.dsl

import com.siimkinks.sqlitemagic.AND
import com.siimkinks.sqlitemagic.AS
import com.siimkinks.sqlitemagic.DELETE
import com.siimkinks.sqlitemagic.DSLTests
import com.siimkinks.sqlitemagic.FROM
import com.siimkinks.sqlitemagic.GREATER_THAN
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.IS_NOT
import com.siimkinks.sqlitemagic.LESS_THAN
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.OR
import com.siimkinks.sqlitemagic.WHERE
import com.siimkinks.sqlitemagic.isEqualTo
import org.junit.jupiter.api.Test

class DeleteTest : DSLTests {
  @Test
  fun deleteFromBuilder() {
    (DELETE FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo("DELETE FROM entity_with_relationship ")
  }

  @Test
  fun deleteFromBuilderWithAlias() {
    (DELETE FROM (ENTITY_WITH_RELATIONSHIP AS "foo"))
      .isEqualTo("DELETE FROM entity_with_relationship ")
  }

  @Test
  fun deleteRawFromBuilder() {
    (DELETE FROM "entity_with_relationship")
      .isEqualTo("DELETE FROM entity_with_relationship ")
  }

  @Test
  fun deleteWhereBuilder() {
    (DELETE
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (ENTITY_WITH_RELATIONSHIP.VALUE IS "asd"))
      .isEqualTo(
        expectedSql = "DELETE FROM entity_with_relationship WHERE entity_with_relationship.value=? ",
        withArgs = arrayOf("asd")
      )

    (DELETE
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (
        ((ENTITY_WITH_RELATIONSHIP.VALUE IS "asd") AND ENTITY_WITH_RELATIONSHIP.VALUE.isNotNull)
            OR (ENTITY_WITH_RELATIONSHIP.COUNT GREATER_THAN 2)))
      .isEqualTo(
        "DELETE " +
            "FROM entity_with_relationship " +
            "WHERE (" +
            "(entity_with_relationship.value=? AND entity_with_relationship.value IS NOT NULL) " +
            "OR entity_with_relationship.count>?) ",
        "asd", "2"
      )

    (DELETE
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (
        ((ENTITY_WITH_RELATIONSHIP.VALUE IS "asd") AND ENTITY_WITH_RELATIONSHIP.VALUE.isNotNull)
            AND (ENTITY_WITH_RELATIONSHIP.COUNT IS 2)))
      .isEqualTo(
        "DELETE " +
            "FROM entity_with_relationship " +
            "WHERE (" +
            "(entity_with_relationship.value=? AND entity_with_relationship.value IS NOT NULL) " +
            "AND entity_with_relationship.count=?) ",
        "asd", "2"
      )

    (DELETE
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (
        ((((ENTITY_WITH_RELATIONSHIP.VALUE IS "asd") AND ENTITY_WITH_RELATIONSHIP.VALUE.isNotNull)
            AND (ENTITY_WITH_RELATIONSHIP.COUNT IS 2))
            AND (((ENTITY_WITH_RELATIONSHIP.ID IS 2) OR
            ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.isNotNull) OR
            (ENTITY_WITH_RELATIONSHIP.COUNT LESS_THAN 55)))
            OR (((ENTITY_WITH_RELATIONSHIP.ID GREATER_THAN 2) AND
            ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.isNull) AND
            (ENTITY_WITH_RELATIONSHIP.COUNT IS_NOT 55))))
      .isEqualTo(
        "DELETE " +
            "FROM entity_with_relationship " +
            "WHERE ((((entity_with_relationship.value=? AND " +
            "entity_with_relationship.value IS NOT NULL) AND entity_with_relationship.count=?) " +
            "AND ((entity_with_relationship.id=? OR " +
            "entity_with_relationship.related_entity IS NOT NULL) OR entity_with_relationship.count<?)) " +
            "OR ((entity_with_relationship.id>? AND " +
            "entity_with_relationship.related_entity IS NULL) AND entity_with_relationship.count!=?)) ",
        "asd", "2", "2", "55", "2", "55"
      )
  }

  @Test
  fun deleteRawWhereBuilder() {
    (DELETE
        FROM "book"
        WHERE "book.title IS NOT NULL")
      .isEqualTo("DELETE FROM book WHERE book.title IS NOT NULL ")
  }

  @Test
  fun deleteRawWhereWithArgsBuilder() {
    (DELETE
        FROM "book"
        WHERE ("book.title=?" to arrayOf("foo")))
      .isEqualTo("DELETE FROM book WHERE book.title=? ", "foo")
  }
}
