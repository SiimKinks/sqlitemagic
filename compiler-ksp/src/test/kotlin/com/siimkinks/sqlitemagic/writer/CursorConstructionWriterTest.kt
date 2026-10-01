package com.siimkinks.sqlitemagic.writer

import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.model.ModelConstruction
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.MUTABLE_PROPERTIES
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.PRIMARY_CONSTRUCTOR
import com.siimkinks.sqlitemagic.model.PropertyAccess
import com.siimkinks.sqlitemagic.model.PropertyMetadata
import com.siimkinks.sqlitemagic.model.PropertyPath
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.STRING
import org.junit.jupiter.api.Test

internal class CursorConstructionWriterTest {
  private data class Property(
    override val access: PropertyAccess,
    val value: String
  ) : PropertyMetadata {
    override val deserializedType = mockParsedType(typeName = STRING)
    override val isNullable = false
  }

  private data class Case(
    val label: String,
    val construction: ModelConstruction,
    val properties: List<Property>,
    val expectedCode: String,
    val expectedReads: List<String>,
    val expectedAssignments: List<String> = emptyList()
  )

  @Test
  fun `construction preserves matching and callback semantics`() {
    val firstPath = PropertyPath(listOf("first"))
    val secondPath = PropertyPath(listOf("second"))
    val missingPath = PropertyPath(listOf("optional"))
    val leftPath = PropertyPath(listOf("left", "value"))
    val rightPath = PropertyPath(listOf("right", "value"))
    val first = Property(
      access = PropertyAccess(
        path = firstPath,
        isMutable = true
      ),
      value = "firstRead"
    )
    val second = Property(
      access = PropertyAccess(
        path = secondPath,
        isMutable = true
      ),
      value = "secondRead"
    )
    val constructor = ModelConstruction(
      strategy = PRIMARY_CONSTRUCTOR,
      constructorParameters = listOf(firstPath, secondPath),
      defaultableParameters = emptySet()
    )
    val cases = listOf(
      Case(
        label = "constructor parameter order",
        construction = constructor,
        properties = listOf(second, first),
        expectedCode = "Model(first = firstRead, second = secondRead)",
        expectedReads = listOf("firstRead", "secondRead")
      ),
      Case(
        label = "omitted optional path",
        construction = constructor.copy(
          constructorParameters = listOf(firstPath, missingPath, secondPath),
          defaultableParameters = setOf(missingPath)
        ),
        properties = listOf(first, second),
        expectedCode = "Model(first = firstRead, second = secondRead)",
        expectedReads = listOf("firstRead", "secondRead")
      ),
      Case(
        label = "first duplicate path wins",
        construction = constructor,
        properties = listOf(first, first.copy(value = "duplicateRead"), second),
        expectedCode = "Model(first = firstRead, second = secondRead)",
        expectedReads = listOf("firstRead", "secondRead")
      ),
      Case(
        label = "embedded paths with the same leaf name remain distinct",
        construction = constructor.copy(constructorParameters = listOf(leftPath, rightPath)),
        properties = listOf(
          Property(
            access = PropertyAccess(
              path = rightPath,
              isMutable = false
            ),
            value = "rightRead"
          ),
          Property(
            access = PropertyAccess(
              path = leftPath,
              isMutable = false
            ),
            value = "leftRead"
          )
        ),
        expectedCode = "Model(`value` = leftRead, `value` = rightRead)",
        expectedReads = listOf("leftRead", "rightRead")
      ),
      Case(
        label = "mutable assignment callbacks retain property order",
        construction = constructor.copy(strategy = MUTABLE_PROPERTIES),
        properties = listOf(second, first),
        expectedCode = "Model().apply {\n  this.second = secondRead\n  this.first = firstRead\n}",
        expectedReads = emptyList(),
        expectedAssignments = listOf("secondRead", "firstRead")
      )
    )

    for (case in cases) {
      with(case) {
        val reads = mutableListOf<String>()
        val assignments = mutableListOf<String>()
        val actual = constructFromCursor(
          type = ClassName("", "Model"),
          construction = construction,
          properties = properties,
          readValue = { property ->
            reads.add(property.value)
            CodeBlock.of("%L", property.value)
          },
          mutableAssignment = { property ->
            assignments.add(property.value)
            CodeBlock.of("this.%N = %L", property.access.path.propertyName, property.value)
          }
        )

        val actualCode = actual.toString()
          .trimEnd()
        assertWithMessage(label).that(actualCode)
          .isEqualTo(expectedCode)
        assertWithMessage(label).that(reads)
          .isEqualTo(expectedReads)
        assertWithMessage(label).that(assignments)
          .isEqualTo(expectedAssignments)
      }
    }
  }
}
