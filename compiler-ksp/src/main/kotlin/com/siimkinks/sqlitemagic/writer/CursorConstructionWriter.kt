package com.siimkinks.sqlitemagic.writer

import com.siimkinks.sqlitemagic.model.ModelConstruction
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.MUTABLE_PROPERTIES
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.PRIMARY_CONSTRUCTOR
import com.siimkinks.sqlitemagic.model.PropertyMetadata
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.withIndent

/** Shared construction shape; each source supplies its own cursor read and mutable-default policy. */
internal fun <P : PropertyMetadata> constructFromCursor(
  type: TypeName,
  construction: ModelConstruction,
  properties: List<P>,
  readValue: (P) -> CodeBlock,
  mutableAssignment: (P) -> CodeBlock
): CodeBlock = when (construction.strategy) {
  PRIMARY_CONSTRUCTOR -> {
    val orderedParameters = construction.constructorParameters
      .mapNotNull { path ->
        properties.firstOrNull { it.access.path == path }
      }
    buildCodeBlock {
      add("%T(\n", type.copy(nullable = false))
      if (orderedParameters.isNotEmpty()) {
        withIndent {
          add(
            "%L\n",
            orderedParameters
              .map { property ->
                CodeBlock.of("%N = %L", property.access.path.propertyName, readValue(property))
              }
              .joinToCode(separator = ",\n")
          )
        }
      }
      add(")")
    }
  }
  MUTABLE_PROPERTIES -> buildCodeBlock {
    beginControlFlow("%T().apply", type.copy(nullable = false))
    properties.forEach { add("%L\n", mutableAssignment(it)) }
    endControlFlow()
  }
}
