package com.siimkinks.sqlitemagic.writer

import com.siimkinks.sqlitemagic.model.ModelConstruction
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.MUTABLE_PROPERTIES
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.PRIMARY_CONSTRUCTOR
import com.siimkinks.sqlitemagic.model.PropertyMetadata
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.joinToCode

/** Shared construction shape; each source supplies its own cursor read and mutable-default policy. */
internal fun <P : PropertyMetadata> constructFromCursor(
  type: TypeName,
  construction: ModelConstruction,
  properties: List<P>,
  readValue: (P) -> CodeBlock,
  mutableAssignment: (P) -> CodeBlock
): CodeBlock = when (construction.strategy) {
  PRIMARY_CONSTRUCTOR -> {
    // Reverse iteration keeps the first property when duplicate paths overwrite an index entry.
    val propertiesByPath = properties
      .asReversed()
      .associateBy { it.access.path }
    val orderedParameters = construction
      .constructorParameters
      .mapNotNull(propertiesByPath::get)
    CodeBlock.of(
      "%T(%L)",
      type.copy(nullable = false),
      orderedParameters.joinToCode { property ->
        CodeBlock.of(
          "%N = %L",
          property.access.path.propertyName,
          readValue(property)
        )
      }
    )
  }
  MUTABLE_PROPERTIES -> buildCodeBlock {
    beginControlFlow("%T().apply", type.copy(nullable = false))
    properties.forEach { add("%L\n", mutableAssignment(it)) }
    endControlFlow()
  }
}
