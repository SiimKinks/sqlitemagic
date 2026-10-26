package com.siimkinks.sqlitemagic.writer

import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.model.ModelConstruction
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.MUTABLE_PROPERTIES
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.PRIMARY_CONSTRUCTOR
import com.siimkinks.sqlitemagic.model.PropertyAccess
import com.siimkinks.sqlitemagic.model.PropertyPath
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.STRING
import org.junit.jupiter.api.Test

internal class CursorReadTreeWriterTest {
  private data class Case(
    val label: String,
    val construction: ModelConstruction,
    val nodes: List<CursorReadNode>,
    val expectedBody: String
  )

  private fun related(
    path: PropertyPath,
    read: String?,
    isNullable: Boolean = false
  ) = CursorReadNode.Related(
    source = CursorReadProperty(
      access = PropertyAccess(
        path = path,
        isMutable = true
      ),
      deserializedType = mockParsedType(typeName = STRING),
      isNullable = isNullable
    ),
    read = read?.let(CodeBlock::of)
  )

  @Test
  fun `construction matches paths to parameters and assigns mutable properties in order`() {
    val firstPath = PropertyPath(listOf("first"))
    val secondPath = PropertyPath(listOf("second"))
    val missingPath = PropertyPath(listOf("optional"))
    val leftPath = PropertyPath(listOf("left", "value"))
    val rightPath = PropertyPath(listOf("right", "value"))
    val first = related(
      path = firstPath,
      read = "firstRead"
    )
    val second = related(
      path = secondPath,
      read = "secondRead"
    )
    val constructor = ModelConstruction(
      strategy = PRIMARY_CONSTRUCTOR,
      constructorParameters = listOf(firstPath, secondPath),
      defaultableParameters = emptySet()
    )
    val mutable = constructor.copy(strategy = MUTABLE_PROPERTIES)
    val cases = listOf(
      Case(
        label = "constructor parameter order",
        construction = constructor,
        nodes = listOf(second, first),
        expectedBody = """
          return Model(
            first = firstRead,
            second = secondRead,
          )
        """.trimIndent()
      ),
      Case(
        label = "omitted optional path",
        construction = constructor.copy(
          constructorParameters = listOf(firstPath, missingPath, secondPath),
          defaultableParameters = setOf(missingPath)
        ),
        nodes = listOf(first, second),
        expectedBody = """
          return Model(
            first = firstRead,
            second = secondRead,
          )
        """.trimIndent()
      ),
      Case(
        label = "first duplicate path wins",
        construction = constructor,
        nodes = listOf(
          first,
          related(
            path = firstPath,
            read = "duplicateRead"
          ),
          second
        ),
        expectedBody = """
          return Model(
            first = firstRead,
            second = secondRead,
          )
        """.trimIndent()
      ),
      Case(
        label = "embedded paths with the same leaf name remain distinct",
        construction = constructor.copy(constructorParameters = listOf(leftPath, rightPath)),
        nodes = listOf(
          related(
            path = rightPath,
            read = "rightRead"
          ),
          related(
            path = leftPath,
            read = "leftRead"
          )
        ),
        expectedBody = """
          return Model(
            `value` = leftRead,
            `value` = rightRead,
          )
        """.trimIndent()
      ),
      Case(
        label = "unread constructor value",
        construction = constructor,
        nodes = listOf(
          first,
          related(
            path = secondPath,
            read = null,
            isNullable = true
          )
        ),
        expectedBody = """
          return Model(
            first = firstRead,
            second = null,
          )
        """.trimIndent()
      ),
      Case(
        label = "mutable assignments retain property order",
        construction = mutable,
        nodes = listOf(second, first),
        expectedBody = """
          return Model().apply {
            this.second = secondRead
            this.first = firstRead
          }
        """.trimIndent()
      ),
      Case(
        label = "nullable mutable value keeps its initializer and an unread one is not assigned",
        construction = mutable,
        nodes = listOf(
          related(
            path = firstPath,
            read = "firstRead",
            isNullable = true
          ),
          related(
            path = secondPath,
            read = null,
            isNullable = true
          )
        ),
        expectedBody = """
          return Model().apply {
            (firstRead)?.let { this.first = it }
          }
        """.trimIndent()
      )
    )

    val modelType = ClassName("", "Model")
    for ((label, construction, nodes, expectedBody) in cases) {
      val reader = cursorPositionReader(
        returnType = modelType,
        readsRelated = false,
        columns = emptyList<Unit>(),
        tree = {
          CursorReadTree(
            type = modelType,
            construction = construction,
            nodes = nodes
          )
        }
      )

      assertWithMessage(label)
        .that(
          reader.body
            .toString()
            .trimEnd()
        )
        .isEqualTo(expectedBody)
    }
  }
}
