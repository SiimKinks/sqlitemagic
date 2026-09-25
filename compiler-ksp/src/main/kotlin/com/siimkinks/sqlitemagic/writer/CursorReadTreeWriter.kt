package com.siimkinks.sqlitemagic.writer

import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.WriterTypes.SQL_EXCEPTION
import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.model.ModelConstruction
import com.siimkinks.sqlitemagic.model.PropertyAccess
import com.siimkinks.sqlitemagic.model.PropertyMetadata
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.joinToCode

internal data class CursorReadTree(
  val type: TypeName,
  val construction: ModelConstruction,
  val nodes: List<CursorReadNode>
)

internal data class CursorReadProperty(
  override val access: PropertyAccess,
  override val deserializedType: ParsedType,
  override val isNullable: Boolean
) : PropertyMetadata {
  companion object {
    fun from(property: PropertyMetadata) = CursorReadProperty(
      access = property.access,
      deserializedType = property.deserializedType,
      isNullable = property.isNullable
    )
  }
}

internal sealed interface CursorReadNode : PropertyMetadata {
  val source: CursorReadProperty

  data class Scalar(
    override val source: CursorReadProperty,
    val position: CursorPosition,
    val storageType: SqlStorageType,
    val transformer: TransformerElement?,
    val serializedInputNullable: Boolean,
    val enclosingPathNullable: Boolean,
    val requirePresentValue: Boolean,
    val missingMessage: String,
    val nullMessage: String,
    val mutableValueGuarded: Boolean
  ) : CursorReadNode, PropertyMetadata by source

  data class Embedded(
    override val source: CursorReadProperty,
    val tree: CursorReadTree,
    val absence: CursorAbsence,
    val skipWidthOnAbsent: Int,
    val mutableValueGuarded: Boolean
  ) : CursorReadNode, PropertyMetadata by source

  data class PersistedRelationship(
    override val source: CursorReadProperty,
    val position: CursorPosition,
    val read: () -> CodeBlock,
    val nestedTree: CursorReadTree? = null,
    val readCanBeNull: Boolean,
    val nullMessage: String,
    val missingMessage: String,
    val skipWidthOnAbsent: Int,
    val selected: Boolean
  ) : CursorReadNode, PropertyMetadata by source

  sealed interface CompleteProjection : CursorReadNode {
    val nestedTree: CursorReadTree
    val descendantPositions: List<CursorPosition>
  }

  data class CompleteTableProjection(
    override val source: CursorReadProperty,
    override val nestedTree: CursorReadTree,
    override val descendantPositions: List<CursorPosition>
  ) : CompleteProjection, PropertyMetadata by source

  data class CompleteViewProjection(
    override val source: CursorReadProperty,
    override val nestedTree: CursorReadTree,
    override val descendantPositions: List<CursorPosition>
  ) : CompleteProjection, PropertyMetadata by source
}

internal sealed interface CursorAbsence {
  data class TableEmbedded(
    val positions: List<CursorPosition>,
    val selected: Boolean
  ) : CursorAbsence

  data class MappedValues(
    val positions: List<CursorPosition>
  ) : CursorAbsence
}

internal fun CursorReadTree.descendantPositions(): List<CursorPosition> = nodes.flatMap { node ->
  when (node) {
    is CursorReadNode.Scalar -> listOf(node.position)
    is CursorReadNode.Embedded -> node.tree.descendantPositions()
    is CursorReadNode.PersistedRelationship ->
      listOf(node.position) + node.nestedTree?.descendantPositions().orEmpty()
    is CursorReadNode.CompleteProjection -> node.descendantPositions
  }
}

/** Emits SQL value reads, nullable boundaries, default-preserving assignments, and cursor skips. */
internal class CursorReadTreeWriter {
  fun construct(tree: CursorReadTree): CodeBlock = constructFromCursor(
    type = tree.type,
    construction = tree.construction,
    properties = tree.nodes,
    readValue = ::value,
    mutableAssignment = ::mutableAssignment
  )

  private fun mutableAssignment(node: CursorReadNode): CodeBlock {
    val nullableCheck = when {
      !node.isNullable -> null
      node is CursorReadNode.Scalar -> node.position.nullCheck()
      node is CursorReadNode.PersistedRelationship -> node.position.nullCheck()
      node is CursorReadNode.Embedded -> absent(node.absence)
      node is CursorReadNode.CompleteProjection -> projectionAbsent(node)
      else -> error("Unhandled cursor node $node")
    }
    val guarded = when (node) {
      is CursorReadNode.Scalar -> node.mutableValueGuarded
      is CursorReadNode.Embedded -> node.mutableValueGuarded
      is CursorReadNode.PersistedRelationship -> true
      is CursorReadNode.CompleteProjection -> false
    }
    val assignment = CodeBlock.of(
      "this.%N = %L",
      node.access.path.propertyName,
      value(
        node = node,
        nullableValueGuarded = nullableCheck != null && guarded
      )
    )
    val skippedWidth = when (node) {
      is CursorReadNode.Embedded -> node.skipWidthOnAbsent
      is CursorReadNode.PersistedRelationship -> node.skipWidthOnAbsent
      is CursorReadNode.Scalar -> 0
      is CursorReadNode.CompleteProjection -> 0
    }
    return when {
      nullableCheck == null -> assignment
      skippedWidth > 0 -> CodeBlock.of(
        "if (%L) columnOffset.value += %L else %L",
        nullableCheck,
        skippedWidth,
        assignment
      )
      else -> CodeBlock.of("if (!(%L)) %L", nullableCheck, assignment)
    }
  }

  private fun value(node: CursorReadNode) = value(
    node = node,
    nullableValueGuarded = false
  )

  private fun value(
    node: CursorReadNode,
    nullableValueGuarded: Boolean
  ): CodeBlock = when (node) {
    is CursorReadNode.Scalar -> scalarValue(
      node = node,
      nullableValueGuarded = nullableValueGuarded
    )
    is CursorReadNode.Embedded -> embeddedValue(
      node = node,
      nullableValueGuarded = nullableValueGuarded
    )
    is CursorReadNode.PersistedRelationship -> relationshipValue(
      node = node,
      nullableValueGuarded = nullableValueGuarded
    )
    is CursorReadNode.CompleteProjection -> projectionValue(node)
  }

  private fun scalarValue(
    node: CursorReadNode.Scalar,
    nullableValueGuarded: Boolean
  ): CodeBlock {
    val databaseValue = databaseCursorGetter(
      storageType = node.storageType,
      index = node.position.code
    )
    val input = when {
      node.serializedInputNullable -> CodeBlock.of(
        "if (%L) null else %L",
        node.position.presentNullCheck(),
        databaseValue
      )
      else -> databaseValue
    }
    val value = node.transformer?.deserializedValueGetter(input) ?: databaseValue
    return when {
      node.enclosingPathNullable && !node.isNullable -> requiredValue(
        position = node.position,
        value = value,
        missingMessage = node.missingMessage,
        nullMessage = node.nullMessage
      )
      !nullableValueGuarded && node.isNullable -> CodeBlock.of(
        "if (%L) null else %L",
        node.position.nullCheck(),
        value
      )
      node.requirePresentValue && !node.serializedInputNullable -> requiredValue(
        position = node.position,
        value = value,
        missingMessage = node.missingMessage,
        nullMessage = node.nullMessage
      )
      else -> value
    }
  }

  private fun embeddedValue(
    node: CursorReadNode.Embedded,
    nullableValueGuarded: Boolean
  ): CodeBlock {
    val construction = construct(node.tree)
    return when {
      !node.isNullable || nullableValueGuarded -> construction
      node.skipWidthOnAbsent > 0 -> buildCodeBlock {
        beginControlFlow("if (%L)", absent(node.absence))
        add("columnOffset.value += %L\n", node.skipWidthOnAbsent)
        add("null\n")
        nextControlFlow("else")
        add("%L\n", construction)
        endControlFlow()
      }
      else -> CodeBlock.of("if (%L) null else %L", absent(node.absence), construction)
    }
  }

  private fun relationshipValue(
    node: CursorReadNode.PersistedRelationship,
    nullableValueGuarded: Boolean
  ): CodeBlock {
    val value = node.nestedTree?.let(::construct) ?: node.read()
    val position = node.position
    val valueOrMissing = when {
      node.readCanBeNull -> CodeBlock.of("%L ?: throw %T(%S)", value, SQL_EXCEPTION, node.missingMessage)
      else -> value
    }
    if (!node.isNullable) {
      val absentValue = when {
        node.skipWidthOnAbsent > 0 -> buildCodeBlock {
          beginControlFlow("run")
          add("columnOffset.value += %L\n", node.skipWidthOnAbsent)
          add("throw %T(%S)\n", SQL_EXCEPTION, node.nullMessage)
          endControlFlow()
        }
        else -> CodeBlock.of("throw %T(%S)", SQL_EXCEPTION, node.nullMessage)
      }
      return when {
        node.selected && position.mayBeMissing -> buildCodeBlock {
          beginControlFlow("if (%L)", position.missingCheck())
          add("throw %T(%S)\n", SQL_EXCEPTION, node.missingMessage)
          nextControlFlow("else if (%L)", position.presentNullCheck())
          add("throw %T(%S)\n", SQL_EXCEPTION, node.nullMessage)
          nextControlFlow("else")
          add("%L\n", valueOrMissing)
          endControlFlow()
        }
        node.selected -> CodeBlock.of(
          "if (%L) throw %T(%S) else %L",
          position.presentNullCheck(),
          SQL_EXCEPTION,
          node.nullMessage,
          valueOrMissing
        )
        node.skipWidthOnAbsent > 0 -> CodeBlock.of(
          "if (%L) %L else %L",
          position.presentNullCheck(),
          absentValue,
          value
        )
        else -> CodeBlock.of(
          "if (%L) throw %T(%S) else %L",
          position.presentNullCheck(),
          SQL_EXCEPTION,
          node.nullMessage,
          value
        )
      }
    }
    return when {
      !nullableValueGuarded && node.skipWidthOnAbsent > 0 -> buildCodeBlock {
        beginControlFlow("if (%L)", position.nullCheck())
        add("columnOffset.value += %L\n", node.skipWidthOnAbsent)
        add("null\n")
        nextControlFlow("else")
        add("%L\n", value)
        endControlFlow()
      }
      !nullableValueGuarded -> CodeBlock.of("if (%L) null else %L", position.nullCheck(), value)
      node.readCanBeNull -> valueOrMissing
      else -> value
    }
  }

  private fun projectionValue(node: CursorReadNode.CompleteProjection): CodeBlock = when {
    node.isNullable -> CodeBlock.of(
      "if (%L) null else %L",
      projectionAbsent(node),
      construct(node.nestedTree)
    )
    else -> construct(node.nestedTree)
  }

  private fun projectionAbsent(node: CursorReadNode.CompleteProjection) = absent(
    CursorAbsence.MappedValues(
      positions = node.descendantPositions
    )
  )

  private fun absent(absence: CursorAbsence): CodeBlock = when (absence) {
    is CursorAbsence.TableEmbedded -> {
      val nullCheck = absence.positions
        .map(CursorPosition::presentNullCheck)
        .joinToCode(separator = " && ")
      val missingCheck = when {
        absence.selected -> absence.positions
          .map(CursorPosition::missingCheck)
          .joinToCode(separator = " && ")
        else -> null
      }
      when (missingCheck) {
        null -> nullCheck
        else -> CodeBlock.of("(%L) || (%L)", missingCheck, nullCheck)
      }
    }
    is CursorAbsence.MappedValues -> absence.positions
      .map(CursorPosition::presentNullCheck)
      .joinToCode(separator = " &&\n")
  }

  private fun requiredValue(
    position: CursorPosition,
    value: CodeBlock,
    missingMessage: String,
    nullMessage: String
  ): CodeBlock = when {
    position.mayBeMissing -> buildCodeBlock {
      beginControlFlow("if (%L)", position.missingCheck())
      add("throw %T(%S)\n", SQL_EXCEPTION, missingMessage)
      nextControlFlow("else if (%L)", position.presentNullCheck())
      add("throw %T(%S)\n", SQL_EXCEPTION, nullMessage)
      nextControlFlow("else")
      add("%L\n", value)
      endControlFlow()
    }
    else -> CodeBlock.of(
      "if (%L) throw %T(%S) else %L",
      position.presentNullCheck(),
      SQL_EXCEPTION,
      nullMessage,
      value
    )
  }
}
