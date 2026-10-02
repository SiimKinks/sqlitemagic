package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class CompiledSelectConnectionConstructionTest {
  @TestFactory
  fun constructionDoesNotResolveDefaultConnection() = listOf(
    constructionCase(
      label = "multi-column compile",
      construct = {
        Select
          .from(SIMPLE_MUTABLE_ENTITY)
          .compile()
      }
    ),
    constructionCase(
      label = "multi-column first",
      construct = {
        Select
          .from(SIMPLE_MUTABLE_ENTITY)
          .takeFirst()
      }
    ),
    constructionCase(
      label = "multi-column count",
      construct = {
        Select
          .from(SIMPLE_MUTABLE_ENTITY)
          .count()
      }
    ),
    constructionCase(
      label = "multi-column cursor",
      construct = {
        Select
          .from(SIMPLE_MUTABLE_ENTITY)
          .toCursor()
      }
    ),
    constructionCase(
      label = "single-column compile",
      construct = {
        Select
          .column(SIMPLE_MUTABLE_ENTITY.VALUE)
          .from(SIMPLE_MUTABLE_ENTITY)
          .compile()
      }
    ),
    constructionCase(
      label = "single-column first",
      construct = {
        Select
          .column(SIMPLE_MUTABLE_ENTITY.VALUE)
          .from(SIMPLE_MUTABLE_ENTITY)
          .takeFirst()
      }
    ),
    constructionCase(
      label = "single-column count",
      construct = {
        Select
          .column(SIMPLE_MUTABLE_ENTITY.VALUE)
          .from(SIMPLE_MUTABLE_ENTITY)
          .count()
      }
    ),
    constructionCase(
      label = "single-column cursor",
      construct = {
        Select
          .column(SIMPLE_MUTABLE_ENTITY.VALUE)
          .from(SIMPLE_MUTABLE_ENTITY)
          .toCursor()
      }
    ),
    constructionCase(
      label = "raw compile",
      construct = {
        Select
          .raw("SELECT * FROM simple_mutable_entity")
          .compile()
      }
    ),
    constructionCase(
      label = "raw from-table compile",
      construct = {
        Select
          .raw("SELECT * FROM simple_mutable_entity")
          .from(SIMPLE_MUTABLE_ENTITY)
          .compile()
      }
    )
  )

  private fun constructionCase(
    label: String,
    construct: () -> Unit
  ) = DynamicTest.dynamicTest(label) {
    withoutDefaultConnection(construct)
  }

  private fun withoutDefaultConnection(block: () -> Unit) {
    val previousConnection = SqliteMagic.defaultConnection
    SqliteMagic.defaultConnection = null
    try {
      block()
    } finally {
      SqliteMagic.defaultConnection = previousConnection
    }
  }
}
