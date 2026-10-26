package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

internal class CompiledSelectConnectionConstructionTest {
  private val source = SELECT.FROM(SIMPLE_MUTABLE_ENTITY)

  @TestFactory
  fun `construction does not resolve the default connection`() = listOf(
    constructionCase(
      label = "generated scalar compile",
      construct = {
        SELECT.COLUMN(SIMPLE_MUTABLE_ENTITY.VALUE)
          .FROM(SIMPLE_MUTABLE_ENTITY)
          .compile()
      }
    ),
    constructionCase(
      label = "generated scalar first",
      construct = {
        SELECT.COLUMN(SIMPLE_MUTABLE_ENTITY.VALUE)
          .FROM(SIMPLE_MUTABLE_ENTITY)
          .takeFirst()
      }
    ),
    constructionCase(
      label = "generated scalar count",
      construct = {
        SELECT.COLUMN(SIMPLE_MUTABLE_ENTITY.VALUE)
          .FROM(SIMPLE_MUTABLE_ENTITY)
          .count()
      }
    ),
    constructionCase(
      label = "generated scalar cursor",
      construct = {
        SELECT.COLUMN(SIMPLE_MUTABLE_ENTITY.VALUE)
          .FROM(SIMPLE_MUTABLE_ENTITY)
          .toCursor()
      }
    ),
    constructionCase(
      label = "generated Row2 compile",
      construct = {
        SELECT
          .COLUMNS(SIMPLE_MUTABLE_ENTITY.ID, SIMPLE_MUTABLE_ENTITY.VALUE)
          .FROM(SIMPLE_MUTABLE_ENTITY)
          .compile()
      }
    ),
    constructionCase(
      label = "generated entity compile",
      construct = {
        source.compile()
      }
    ),
    constructionCase(
      label = "generated entity first",
      construct = {
        source.takeFirst()
      }
    ),
    constructionCase(
      label = "generated entity count",
      construct = {
        source.count()
      }
    ),
    constructionCase(
      label = "generated entity cursor",
      construct = {
        source.toCursor()
      }
    ),
    constructionCase(
      label = "distinct generated scalar compile",
      construct = {
        SELECT.DISTINCT(SIMPLE_MUTABLE_ENTITY.VALUE)
          .FROM(SIMPLE_MUTABLE_ENTITY)
          .compile()
      }
    ),
    constructionCase(
      label = "infix distinct generated scalar first",
      construct = {
        (SELECT DISTINCT SIMPLE_MUTABLE_ENTITY.VALUE FROM SIMPLE_MUTABLE_ENTITY).takeFirst()
      }
    ),
    constructionCase(
      label = "distinct generated entity count",
      construct = {
        (SELECT.DISTINCT FROM SIMPLE_MUTABLE_ENTITY).count()
      }
    )
  )

  private fun constructionCase(
    label: String,
    construct: () -> Unit
  ) = dynamicTest(label) {
    val previousConnection = SqliteMagic.defaultConnection
    SqliteMagic.defaultConnection = null
    try {
      construct()
    } finally {
      SqliteMagic.defaultConnection = previousConnection
    }
  }
}
