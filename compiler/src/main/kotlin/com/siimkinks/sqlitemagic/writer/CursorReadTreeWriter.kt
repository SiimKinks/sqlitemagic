package com.siimkinks.sqlitemagic.writer

import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_OBJECT_FROM_CURSOR_POSITION
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_OBJECT_FROM_SELECTED_POSITIONS
import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.WriterTypes.CHECK_NOT_NULL
import com.siimkinks.sqlitemagic.WriterTypes.CURSOR
import com.siimkinks.sqlitemagic.WriterTypes.RELATED_ENTITIES
import com.siimkinks.sqlitemagic.WriterTypes.SQL_EXCEPTION
import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.model.ModelConstruction
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.MUTABLE_PROPERTIES
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.PRIMARY_CONSTRUCTOR
import com.siimkinks.sqlitemagic.model.PropertyAccess
import com.siimkinks.sqlitemagic.model.PropertyMetadata
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.siimkinks.sqlitemagic.utils.callCode
import com.siimkinks.sqlitemagic.utils.endControlFlowExpression
import com.siimkinks.sqlitemagic.utils.namedArgument
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.INT_ARRAY
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.buildCodeBlock

internal data class CursorReadTree(
  val type: TypeName,
  val construction: ModelConstruction,
  val nodes: List<CursorReadNode>
)

internal data class CursorReadProperty(
  override val access: PropertyAccess,
  override val deserializedType: ParsedType,
  override val isNullable: Boolean
) : PropertyMetadata

internal fun PropertyMetadata.toCursorReadProperty() = CursorReadProperty(
  access = access,
  deserializedType = deserializedType,
  isNullable = isNullable
)

internal sealed interface CursorReadNode : PropertyMetadata {
  val source: CursorReadProperty

  data class Scalar(
    override val source: CursorReadProperty,
    val position: CursorPosition,
    val storageType: SqlStorageType,
    val transformer: TransformerElement?,
    val requiresPresentValue: Boolean,
    val missingMessage: String,
    val nullMessage: String
  ) : CursorReadNode, PropertyMetadata by source {
    val decoderInputNullable get() = transformer?.decoderInputNullable == true
  }

  data class Embedded(
    override val source: CursorReadProperty,
    val tree: CursorReadTree,
    val positions: List<CursorPosition>
  ) : CursorReadNode, PropertyMetadata by source

  data class PersistedRelationship(
    override val source: CursorReadProperty,
    val position: CursorPosition,
    val read: CodeBlock,
    val nullMessage: String
  ) : CursorReadNode, PropertyMetadata by source

  data class Related(
    override val source: CursorReadProperty,
    val read: CodeBlock?
  ) : CursorReadNode, PropertyMetadata by source
}

internal fun CursorReadTree.descendantPositions(): List<CursorPosition> = nodes.flatMap { node ->
  when (node) {
    is CursorReadNode.Scalar -> listOf(node.position)
    is CursorReadNode.Embedded -> node.tree.descendantPositions()
    is CursorReadNode.PersistedRelationship -> listOf(node.position)
    is CursorReadNode.Related -> emptyList()
  }
}

internal fun <K> cursorPositionReader(
  returnType: TypeName,
  readsRelated: Boolean,
  columns: List<K>,
  tree: (Map<K, CursorPosition>) -> CursorReadTree
): FunSpec {
  val positions = CursorPositions()
  val columnPositions = columns
    .withIndex()
    .associate { (index, column) ->
      column to positions.positional(
        code = when (index) {
          0 -> CodeBlock.of("startIndex")
          else -> CodeBlock.of("startIndex + %L", index)
        },
        nullCheckName = "column${index}IsNull"
      )
    }
  return FunSpec
    .builder(METHOD_OBJECT_FROM_CURSOR_POSITION)
    .addParameter(name = "cursor", type = CURSOR)
    .addParameter(name = "startIndex", type = INT)
    .readingRelated(readsRelated)
    .returns(returnType)
    .returnRead(positions = positions) { tree(columnPositions) }
}

/** A negative position in `positions` is a column the query did not select. */
internal fun <K> selectedPositionsReader(
  returnType: TypeName,
  readsRelated: Boolean,
  columns: List<K>,
  mayBeMissing: (K) -> Boolean,
  tree: (Map<K, CursorPosition>) -> CursorReadTree
): FunSpec {
  val positions = CursorPositions()
  val columnPositions = columns
    .withIndex()
    .associate { (index, column) ->
      column to positions.indexed(
        indexName = "columnIndex$index",
        code = CodeBlock.of("positions[%L]", index),
        mayBeMissing = mayBeMissing(column)
      )
    }
  return FunSpec
    .builder(METHOD_OBJECT_FROM_SELECTED_POSITIONS)
    .addParameter(name = "cursor", type = CURSOR)
    .addParameter(name = "positions", type = INT_ARRAY)
    .readingRelated(readsRelated)
    .returns(returnType)
    .returnRead(positions = positions) { tree(columnPositions) }
}

private fun FunSpec.Builder.readingRelated(readsRelated: Boolean) = apply {
  if (readsRelated) {
    addParameter(name = "related", type = RELATED_ENTITIES)
  }
}

private fun FunSpec.Builder.returnRead(
  positions: CursorPositions,
  tree: () -> CursorReadTree
): FunSpec {
  val result = positions.generate { construct(tree()) }
  positions.addDeclarations(this)
  return this
    .addCode("return %L\n", result)
    .build()
}

private fun construct(tree: CursorReadTree): CodeBlock = when (tree.construction.strategy) {
  PRIMARY_CONSTRUCTOR -> {
    // Reverse iteration keeps the first node when duplicate paths overwrite an index entry.
    val nodesByPath = tree.nodes
      .asReversed()
      .associateBy { it.access.path }
    callCode(
      callee = CodeBlock.of("%T", tree.type.copy(nullable = false)),
      arguments = tree.construction.constructorParameters
        .mapNotNull(nodesByPath::get)
        .map { namedArgument(it.access.path.propertyName, "%L", value(it)) }
    )
  }
  MUTABLE_PROPERTIES -> buildCodeBlock {
    beginControlFlow("%T().apply", tree.type.copy(nullable = false))
    tree.nodes
      .mapNotNull(::mutableAssignment)
      .forEach { add("%L\n", it) }
    endControlFlowExpression()
  }
}

private fun mutableAssignment(node: CursorReadNode): CodeBlock? = when (node) {
  is CursorReadNode.Scalar -> guardedAssignment(
    node = node,
    guard = when {
      node.isNullable -> node.position.nullCheck()
      // A required mutable value whose column a partial read did not select keeps its initializer.
      node.position.mayBeMissing -> node.position.missingCheck()
      else -> null
    }
  )
  is CursorReadNode.Embedded -> guardedAssignment(
    node = node,
    guard = when {
      node.isNullable -> absent(node.positions)
      node.positions.allMayBeMissing() -> CursorCheck.all(node.positions.map(CursorPosition::missingCheck))
      else -> null
    }
  )
  is CursorReadNode.PersistedRelationship -> guardedAssignment(
    node = node,
    guard = when {
      node.isNullable -> node.position.nullCheck()
      else -> null
    }
  )
  is CursorReadNode.Related -> node.read?.let { read ->
    when {
      node.isNullable -> CodeBlock.of("(%L)?.let { this.%N = it }", read, node.access.path.propertyName)
      else -> CodeBlock.of("this.%N = %L", node.access.path.propertyName, read)
    }
  }
}

private fun guardedAssignment(
  node: CursorReadNode,
  guard: CursorCheck?
): CodeBlock {
  val assignment = CodeBlock.of(
    "this.%N = %L",
    node.access.path.propertyName,
    value(
      node = node,
      guarded = guard != null
    )
  )
  return when (guard) {
    null -> assignment
    else -> CodeBlock.of("if (%L) %L", guard.negation, assignment)
  }
}

private fun value(
  node: CursorReadNode,
  guarded: Boolean = false
): CodeBlock = when (node) {
  is CursorReadNode.Scalar -> scalarValue(
    node = node,
    guarded = guarded
  )
  is CursorReadNode.Embedded -> embeddedValue(
    node = node,
    guarded = guarded
  )
  is CursorReadNode.PersistedRelationship -> relationshipValue(
    node = node,
    guarded = guarded
  )
  is CursorReadNode.Related -> node.read ?: CodeBlock.of("null")
}

private fun scalarValue(
  node: CursorReadNode.Scalar,
  guarded: Boolean
): CodeBlock {
  val required = !node.isNullable && node.requiresPresentValue
  val databaseValue = databaseCursorGetter(
    storageType = node.storageType,
    index = node.position.code
  )
  val input = when {
    node.decoderInputNullable && !node.isNullable && !required -> CodeBlock.of(
      "if (%L) null else %L",
      node.position.presentNullCheck().condition,
      databaseValue
    )
    else -> databaseValue
  }
  val transformedValue = node.transformer?.deserializedValueGetter(input) ?: databaseValue
  val value = when {
    !node.isNullable && node.transformer?.deserializedType?.typeName?.isNullable == true -> {
      val decoder = node.transformer.dbValueToObjectMethod
      val callableName = "${decoder.ownerQualifiedName ?: decoder.packageName}.${decoder.methodName}"
      CodeBlock.of(
        "%M(%L) { %S }",
        CHECK_NOT_NULL,
        transformedValue,
        "Transformer $callableName returned null for required property ${node.access.path.displayName}"
      )
    }
    else -> transformedValue
  }
  return when {
    node.isNullable && !guarded -> CodeBlock.of(
      "if (%L) null else %L",
      node.position.nullCheck().condition,
      value
    )
    required -> requiredValue(
      position = node.position,
      value = value,
      missingMessage = node.missingMessage,
      nullMessage = node.nullMessage,
      presenceChecked = guarded
    )
    else -> value
  }
}

private fun embeddedValue(
  node: CursorReadNode.Embedded,
  guarded: Boolean
): CodeBlock {
  val construction = construct(node.tree)
  return when {
    !node.isNullable || guarded -> construction
    else -> CodeBlock.of("if (%L) null else %L", absent(node.positions).condition, construction)
  }
}

private fun relationshipValue(
  node: CursorReadNode.PersistedRelationship,
  guarded: Boolean
): CodeBlock = when {
  !node.isNullable -> CodeBlock.of(
    "if (%L) throw %T(%S) else %L",
    node.position.presentNullCheck().condition,
    SQL_EXCEPTION,
    node.nullMessage,
    node.read
  )
  guarded -> node.read
  else -> CodeBlock.of("if (%L) null else %L", node.position.nullCheck().condition, node.read)
}

private fun absent(positions: List<CursorPosition>): CursorCheck {
  val nullCheck = CursorCheck.all(positions.map(CursorPosition::presentNullCheck))
  return when {
    positions.allMayBeMissing() -> CursorCheck.either(
      first = CursorCheck.all(positions.map(CursorPosition::missingCheck)),
      second = nullCheck
    )
    else -> nullCheck
  }
}

private fun List<CursorPosition>.allMayBeMissing() = isNotEmpty() && all(CursorPosition::mayBeMissing)

private fun requiredValue(
  position: CursorPosition,
  value: CodeBlock,
  missingMessage: String,
  nullMessage: String,
  presenceChecked: Boolean
): CodeBlock = when {
  position.mayBeMissing && !presenceChecked -> buildCodeBlock {
    beginControlFlow("if (%L)", position.missingCheck().condition)
    add("throw %T(%S)\n", SQL_EXCEPTION, missingMessage)
    nextControlFlow("else if (%L)", position.presentNullCheck().condition)
    add("throw %T(%S)\n", SQL_EXCEPTION, nullMessage)
    nextControlFlow("else")
    add("%L\n", value)
    endControlFlowExpression()
  }
  else -> CodeBlock.of(
    "if (%L) throw %T(%S) else %L",
    position.presentNullCheck().condition,
    SQL_EXCEPTION,
    nullMessage,
    value
  )
}
