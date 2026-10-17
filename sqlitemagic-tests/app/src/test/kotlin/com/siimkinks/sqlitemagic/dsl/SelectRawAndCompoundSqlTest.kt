package com.siimkinks.sqlitemagic.dsl

import com.siimkinks.sqlitemagic.COLUMNS
import com.siimkinks.sqlitemagic.DISTINCT
import com.siimkinks.sqlitemagic.DSLTests
import com.siimkinks.sqlitemagic.EXCEPT
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.FROM
import com.siimkinks.sqlitemagic.INTERSECT
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.RAW
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.UNION
import com.siimkinks.sqlitemagic.UNION_ALL
import com.siimkinks.sqlitemagic.WHERE
import com.siimkinks.sqlitemagic.WITH_ARGS
import com.siimkinks.sqlitemagic.isEqualTo
import org.junit.jupiter.api.Test

class SelectRawAndCompoundSqlTest : DSLTests {
  @Test
  fun rawSelect() {
    (SELECT
        RAW "SELECT * FROM simple_mutable_entity")
      .compile()
      .isEqualTo("SELECT * FROM simple_mutable_entity")
  }

  @Test
  fun rawSelectWithArgs() {
    (SELECT
        RAW "SELECT * FROM simple_mutable_entity WHERE simple_mutable_entity.value=?"
        WITH_ARGS arrayOf("foo"))
      .compile()
      .isEqualTo(
        expectedSql = "SELECT * FROM simple_mutable_entity WHERE simple_mutable_entity.value=?",
        expectedArgs = arrayOf("foo")
      )

    (SELECT
        RAW "SELECT * FROM simple_mutable_entity WHERE simple_mutable_entity.value=?"
        WITH_ARGS arrayOf("foo")
        FROM SIMPLE_MUTABLE_ENTITY)
      .compile()
      .isEqualTo(
        expectedSql = "SELECT * FROM simple_mutable_entity WHERE simple_mutable_entity.value=?",
        expectedObservedTables = arrayOf("simple_mutable_entity"),
        expectedArgs = arrayOf("foo")
      )

    (SELECT
        RAW "SELECT * FROM simple_mutable_entity WHERE simple_mutable_entity.value=?"
        FROM SIMPLE_MUTABLE_ENTITY
        WITH_ARGS arrayOf("foo"))
      .compile()
      .isEqualTo(
        expectedSql = "SELECT * FROM simple_mutable_entity WHERE simple_mutable_entity.value=?",
        expectedObservedTables = arrayOf("simple_mutable_entity"),
        expectedArgs = arrayOf("foo")
      )
  }

  @Test
  fun rawSelectWithObservedTable() {
    (SELECT
        RAW "SELECT * FROM simple_mutable_entity"
        FROM SIMPLE_MUTABLE_ENTITY)
      .compile()
      .isEqualTo(
        expectedSql = "SELECT * FROM simple_mutable_entity",
        expectedObservedTables = arrayOf("simple_mutable_entity")
      )

    (SELECT
        RAW "SELECT * FROM entity_with_relationship, simple_mutable_entity"
        FROM arrayOf(ENTITY_WITH_RELATIONSHIP, SIMPLE_MUTABLE_ENTITY))
      .compile()
      .isEqualTo(
        expectedSql = "SELECT * FROM entity_with_relationship, simple_mutable_entity",
        expectedObservedTables = arrayOf("entity_with_relationship", "simple_mutable_entity")
      )

    (SELECT
        RAW "SELECT * FROM simple_mutable_entity"
        FROM listOf(SIMPLE_MUTABLE_ENTITY))
      .compile()
      .isEqualTo(
        expectedSql = "SELECT * FROM simple_mutable_entity",
        expectedObservedTables = arrayOf("simple_mutable_entity")
      )
  }

  @Test
  fun selectAllFrom() {
    (SELECT FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo("SELECT * FROM main.entity_with_relationship ")

    (SELECT.DISTINCT FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo("SELECT DISTINCT * FROM main.entity_with_relationship ")
  }

  @Test
  fun selectAllFromSubquery() {
    (SELECT FROM (SELECT FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo("SELECT * FROM (SELECT * FROM main.simple_mutable_entity ) ")

    (SELECT.DISTINCT
        FROM (SELECT FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo("SELECT DISTINCT * FROM (SELECT * FROM main.simple_mutable_entity ) ")
  }

  @Test
  fun compoundSelectWithArgs() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        UNION
        (SELECT
            FROM SIMPLE_MUTABLE_ENTITY
            WHERE (SIMPLE_MUTABLE_ENTITY.VALUE IS "foo")))
      .isEqualTo(
        "SELECT * FROM main.simple_mutable_entity UNION " +
            "SELECT * FROM main.simple_mutable_entity WHERE simple_mutable_entity.value=?  "
      )
  }

  @Test
  fun compoundSelectWithArgsInBothSelects() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        WHERE (SIMPLE_MUTABLE_ENTITY.VALUE IS "foo")
        UNION
        (SELECT
            FROM SIMPLE_MUTABLE_ENTITY
            WHERE (SIMPLE_MUTABLE_ENTITY.VALUE IS "foo")))
      .isEqualTo(
        "SELECT * FROM main.simple_mutable_entity WHERE simple_mutable_entity.value=? UNION " +
            "SELECT * FROM main.simple_mutable_entity WHERE simple_mutable_entity.value=?  "
      )
  }

  @Test
  fun unionSelect() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        UNION (SELECT FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo("SELECT * FROM main.simple_mutable_entity UNION SELECT * FROM main.simple_mutable_entity  ")
  }

  @Test
  fun unionSelectExplicitColumns() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        UNION
        (SELECT
            COLUMNS arrayOf(
          SIMPLE_MUTABLE_ENTITY.ID,
          SIMPLE_MUTABLE_ENTITY.VALUE,
          SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
          SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
        ) FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo(
        "SELECT * FROM main.simple_mutable_entity UNION " +
            "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM main.simple_mutable_entity  "
      )
  }

  @Test
  fun unionSelectExplicitAllColumns() {
    (SELECT
        COLUMNS arrayOf(
      SIMPLE_MUTABLE_ENTITY.ID,
      SIMPLE_MUTABLE_ENTITY.VALUE,
      SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
      SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
    )
        FROM SIMPLE_MUTABLE_ENTITY
        UNION
        (SELECT
            COLUMNS arrayOf(
          SIMPLE_MUTABLE_ENTITY.ID,
          SIMPLE_MUTABLE_ENTITY.VALUE,
          SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
          SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
        )
            FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo(
        "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM main.simple_mutable_entity UNION " +
            "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM main.simple_mutable_entity  "
      )
  }

  @Test
  fun unionAllSelect() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        UNION_ALL (SELECT FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo("SELECT * FROM main.simple_mutable_entity UNION ALL SELECT * FROM main.simple_mutable_entity  ")
  }

  @Test
  fun unionAllSelectExplicitColumns() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        UNION_ALL
        (SELECT
            COLUMNS arrayOf(
          SIMPLE_MUTABLE_ENTITY.ID,
          SIMPLE_MUTABLE_ENTITY.VALUE,
          SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
          SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
        )
            FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo(
        "SELECT * FROM main.simple_mutable_entity UNION ALL " +
            "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM main.simple_mutable_entity  "
      )
  }

  @Test
  fun unionAllSelectExplicitAllColumns() {
    (SELECT
        COLUMNS arrayOf(
      SIMPLE_MUTABLE_ENTITY.ID,
      SIMPLE_MUTABLE_ENTITY.VALUE,
      SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
      SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
    )
        FROM SIMPLE_MUTABLE_ENTITY
        UNION_ALL
        (SELECT
            COLUMNS arrayOf(
          SIMPLE_MUTABLE_ENTITY.ID,
          SIMPLE_MUTABLE_ENTITY.VALUE,
          SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
          SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
        )
            FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo(
        "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM main.simple_mutable_entity UNION ALL " +
            "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM main.simple_mutable_entity  "
      )
  }

  @Test
  fun intersectSelect() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        INTERSECT (SELECT FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo("SELECT * FROM main.simple_mutable_entity INTERSECT SELECT * FROM main.simple_mutable_entity  ")
  }

  @Test
  fun intersectSelectExplicitColumns() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        INTERSECT
        (SELECT
            COLUMNS arrayOf(
          SIMPLE_MUTABLE_ENTITY.ID,
          SIMPLE_MUTABLE_ENTITY.VALUE,
          SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
          SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
        )
            FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo(
        "SELECT * FROM main.simple_mutable_entity INTERSECT " +
            "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM main.simple_mutable_entity  "
      )
  }

  @Test
  fun intersectSelectExplicitAllColumns() {
    (SELECT
        COLUMNS arrayOf(
      SIMPLE_MUTABLE_ENTITY.ID,
      SIMPLE_MUTABLE_ENTITY.VALUE,
      SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
      SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
    )
        FROM SIMPLE_MUTABLE_ENTITY
        INTERSECT
        (SELECT
            COLUMNS arrayOf(
          SIMPLE_MUTABLE_ENTITY.ID,
          SIMPLE_MUTABLE_ENTITY.VALUE,
          SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
          SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
        )
            FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo(
        "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM main.simple_mutable_entity INTERSECT " +
            "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM main.simple_mutable_entity  "
      )
  }

  @Test
  fun exceptSelect() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        EXCEPT (SELECT FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo("SELECT * FROM main.simple_mutable_entity EXCEPT SELECT * FROM main.simple_mutable_entity  ")
  }

  @Test
  fun exceptSelectExplicitColumns() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        EXCEPT
        (SELECT
            COLUMNS arrayOf(
          SIMPLE_MUTABLE_ENTITY.ID,
          SIMPLE_MUTABLE_ENTITY.VALUE,
          SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
          SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
        )
            FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo(
        "SELECT * FROM main.simple_mutable_entity EXCEPT " +
            "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM main.simple_mutable_entity  "
      )
  }

  @Test
  fun exceptSelectExplicitAllColumns() {
    (SELECT
        COLUMNS arrayOf(
      SIMPLE_MUTABLE_ENTITY.ID,
      SIMPLE_MUTABLE_ENTITY.VALUE,
      SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
      SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
    )
        FROM SIMPLE_MUTABLE_ENTITY
        EXCEPT
        (SELECT
            COLUMNS arrayOf(
          SIMPLE_MUTABLE_ENTITY.ID,
          SIMPLE_MUTABLE_ENTITY.VALUE,
          SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
          SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
        )
            FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo(
        "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM main.simple_mutable_entity EXCEPT " +
            "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM main.simple_mutable_entity  "
      )
  }
}
