package com.siimkinks.sqlitemagic.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class TableReadLayoutTest {
  @Test
  fun `counts relationship readers in deep and shallow layouts`() {
    val leaf = mockTableElement(
      modelName = "Leaf",
      properties = listOf(mockColumnPropertyElement())
    )
    val child = mockTableElement(
      modelName = "Child",
      properties = listOf(
        mockColumnPropertyElement(),
        mockColumnPropertyElement(
          column = mockColumnElement(
            relationship = mockRelationshipElement(
              referencedTableType = leaf.parsedType,
              canConstructWithOnlyId = false
            )
          )
        )
      )
    )
    val rootRelationship = mockColumnElement(
      relationship = mockRelationshipElement(referencedTableType = child.parsedType)
    )
    val root = mockTableElement(
      modelName = "Root",
      properties = listOf(
        mockColumnPropertyElement(),
        mockColumnPropertyElement(column = rootRelationship)
      )
    )
    val layout = TableReadLayout(
      mapOf(
        root.typeKey to root,
        child.typeKey to child,
        leaf.typeKey to leaf
      )
    )

    assertThat(layout.width(table = root, recursive = false)).isEqualTo(2)
    assertThat(layout.width(table = root, recursive = true)).isEqualTo(5)
    assertThat(layout.relationshipWidth(column = rootRelationship, recursive = true)).isEqualTo(3)
    assertThat(layout.width(table = child, recursive = false)).isEqualTo(3)
  }
}
