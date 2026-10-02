package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_GENERATED_VIEW
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_VIEW_QUERY
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION
import com.siimkinks.sqlitemagic.WriterTypes.CURSOR
import com.siimkinks.sqlitemagic.WriterTypes.GENERATED_VIEW
import com.siimkinks.sqlitemagic.WriterTypes.MUTABLE_INT
import com.siimkinks.sqlitemagic.WriterTypes.SIMPLE_ARRAY_MAP
import com.siimkinks.sqlitemagic.WriterTypes.SQL_UTIL
import com.siimkinks.sqlitemagic.WriterTypes.VIEW_DEFINITION
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import com.siimkinks.sqlitemagic.model.CompleteProjectionColumn
import com.siimkinks.sqlitemagic.model.ModelConstruction
import com.siimkinks.sqlitemagic.model.ModelDaoCursorWriter
import com.siimkinks.sqlitemagic.model.writeModelSource
import com.siimkinks.sqlitemagic.writer.CursorAbsence
import com.siimkinks.sqlitemagic.writer.CursorPosition
import com.siimkinks.sqlitemagic.writer.CursorPositions
import com.siimkinks.sqlitemagic.writer.CursorReadNode
import com.siimkinks.sqlitemagic.writer.CursorReadProperty
import com.siimkinks.sqlitemagic.writer.CursorReadTree
import com.siimkinks.sqlitemagic.writer.CursorReadTreeWriter
import com.siimkinks.sqlitemagic.writer.descendantPositions
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier.INTERNAL
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

internal class ViewDaoWriter(
  private val environment: Environment
) : ViewWriter {
  private val cursorReadTreeWriter = CursorReadTreeWriter()
  private val modelDaoCursorWriter = ModelDaoCursorWriter(environment)

  override fun write(roundElement: ViewRoundElement) {
    val view = roundElement.view
    val dao = TypeSpec
      .objectBuilder(view.generationNames.daoClassName)
      .addModifiers(INTERNAL)
      .addProperty(generatedViewProperty(view))
      .addProperty(queryProperty())
      .addFunctions(
        listOf(
          shallowCursorReader(
            view = view,
            selected = false
          ),
          shallowCursorReader(
            view = view,
            selected = true
          )
        )
      )
    FileSpec
      .builder(view.generationNames.daoClassName)
      .addType(dao.build())
      .writeModelSource(
        codeGenerator = environment.codeGenerator,
        originatingFiles = roundElement.originatingFiles
      )
  }

  private fun generatedViewProperty(view: ViewElement) = PropertySpec
    .builder(name = FIELD_GENERATED_VIEW, type = GENERATED_VIEW)
    .addModifiers(INTERNAL)
    .initializer(
      "%T(viewName = %S, temporary = %L, definitionProvider = { %T.viewDefinition(query = %T.%N, viewName = %S) })",
      GENERATED_VIEW,
      view.viewName,
      view.schema == TEMPORARY,
      SQL_UTIL,
      view.query.ownerType,
      view.query.propertyName,
      view.viewName
    )
    .build()

  private fun queryProperty() = PropertySpec
    .builder(name = FIELD_VIEW_QUERY, type = VIEW_DEFINITION)
    .addModifiers(INTERNAL)
    .getter(
      FunSpec
        .getterBuilder()
        .addStatement("return %N.definition", FIELD_GENERATED_VIEW)
        .build()
    )
    .build()

  private fun shallowCursorReader(
    view: ViewElement,
    selected: Boolean
  ): FunSpec {
    val function = FunSpec
      .builder(METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION)
      .addParameter(name = "cursor", type = CURSOR)
      .apply {
        when {
          selected -> {
            addParameter(name = "columns", type = SIMPLE_ARRAY_MAP.parameterizedBy(STRING, INT))
            addParameter(
              name = "tableGraphNodeNames",
              type = SIMPLE_ARRAY_MAP
                .parameterizedBy(STRING, STRING)
                .copy(nullable = true)
            )
            addParameter(name = "nodeName", type = STRING)
          }
          else -> addParameter(
            ParameterSpec
              .builder(name = "columnOffset", type = MUTABLE_INT)
              .defaultValue("%T()", MUTABLE_INT)
              .build()
          )
        }
        if (!selected) {
          addStatement("val thisTableOffset = columnOffset.value")
          addStatement("columnOffset.value += %L", view.expandedWidth)
        }
      }
      .returns(view.modelClassName.copy(nullable = selected))
    val scope = ViewCursorScope(
      function = function,
      positions = CursorPositions(),
      selected = selected
    )
    val tree = readTree(
      type = view.modelClassName,
      construction = view.construction,
      properties = view.properties,
      scope = scope,
      namespace = CodeBlock.of("%N", "nodeName"),
      viewSpan = CodeBlock.of("%N[%N]", "columns", "nodeName"),
      absoluteOffset = 0,
      relativeOffset = 0,
      requireAll = false,
      insideEmbedded = false
    )
    val result = scope.positions.generate {
      cursorReadTreeWriter.construct(tree)
    }
    scope.positions.addNullCheckDeclarations(function)
    return function
      .addStatement("return %L", result)
      .build()
  }

  private fun readTree(
    type: TypeName,
    construction: ModelConstruction,
    properties: List<ViewPropertyElement>,
    scope: ViewCursorScope,
    namespace: CodeBlock,
    viewSpan: CodeBlock,
    absoluteOffset: Int,
    relativeOffset: Int,
    requireAll: Boolean,
    insideEmbedded: Boolean
  ): CursorReadTree {
    val offsets = properties.runningFold(initial = 0) { acc, property ->
      acc + property.expandedWidth
    }
    val nodes = properties.mapIndexed { index, property ->
      val propertyAbsoluteOffset = absoluteOffset + offsets[index]
      val propertyRelativeOffset = relativeOffset + offsets[index]
      when (property) {
        is ViewScalarPropertyElement -> scalarNode(
          property = property,
          scope = scope,
          namespace = namespace,
          viewSpan = viewSpan,
          absoluteOffset = propertyAbsoluteOffset,
          relativeOffset = propertyRelativeOffset,
          required = requireAll || insideEmbedded || !property.isNullable
        )
        is ViewProjectionPropertyElement -> projectionNode(
          property = property,
          scope = scope,
          namespace = namespace,
          viewSpan = viewSpan,
          absoluteOffset = propertyAbsoluteOffset,
          relativeOffset = propertyRelativeOffset
        )
        is ViewEmbeddedPropertyElement -> {
          val child = readTree(
            type = property.deserializedType.typeName,
            construction = property.construction,
            properties = property.properties,
            scope = scope,
            namespace = namespace,
            viewSpan = viewSpan,
            absoluteOffset = propertyAbsoluteOffset,
            relativeOffset = propertyRelativeOffset,
            requireAll = requireAll,
            insideEmbedded = true
          )
          CursorReadNode.Embedded(
            source = CursorReadProperty.from(property),
            tree = child,
            absence = CursorAbsence.MappedValues(positions = child.descendantPositions()),
            skipWidthOnAbsent = 0,
            mutableValueGuarded = false
          )
        }
      }
    }
    return CursorReadTree(
      type = type,
      construction = construction,
      nodes = nodes
    )
  }

  private fun scalarNode(
    property: ViewScalarPropertyElement,
    scope: ViewCursorScope,
    namespace: CodeBlock,
    viewSpan: CodeBlock,
    absoluteOffset: Int,
    relativeOffset: Int,
    required: Boolean
  ): CursorReadNode.Scalar {
    val position = viewPosition(
      scope = scope,
      namespace = namespace,
      viewSpan = viewSpan,
      selectionKey = property.selectionKey,
      absoluteOffset = absoluteOffset,
      relativeOffset = relativeOffset,
      required = required
    )
    return CursorReadNode.Scalar(
      source = CursorReadProperty.from(property),
      position = position,
      storageType = checkNotNull(property.serializedType.sqlStorageType),
      transformer = property.transformer,
      serializedInputNullable = property.transformer?.serializedTypeCanBeNull == true,
      enclosingPathNullable = false,
      requirePresentValue = true,
      missingMessage = "Selected columns did not contain required column \"${property.selectionKey}\"",
      nullMessage = "Column \"${property.selectionKey}\" was NULL",
      mutableValueGuarded = false
    )
  }

  private fun viewPosition(
    scope: ViewCursorScope,
    namespace: CodeBlock,
    viewSpan: CodeBlock,
    selectionKey: String,
    absoluteOffset: Int,
    relativeOffset: Int,
    required: Boolean
  ): CursorPosition {
    if (!scope.selected) {
      return scope.positions.positional(
        code = CodeBlock.of("thisTableOffset + %L", absoluteOffset),
        nullCheckName = scope.nextNullCheckName()
      )
    }
    val physical = selectionKey.substringAfterLast('.')
    return scope.positions.selected(
      function = scope.function,
      indexName = scope.nextIndexName(),
      lookup = CodeBlock.of(
        "columns[%L + %S] ?: columns[%L + %S] ?: %L?.plus(%L)",
        namespace,
        ".$selectionKey",
        namespace,
        ".$physical",
        viewSpan,
        relativeOffset
      ),
      required = required,
      missingMessage = "Selected columns did not contain required column \"$selectionKey\""
    )
  }

  private fun projectionNode(
    property: ViewProjectionPropertyElement,
    scope: ViewCursorScope,
    namespace: CodeBlock,
    viewSpan: CodeBlock,
    absoluteOffset: Int,
    relativeOffset: Int
  ): CursorReadNode.CompleteProjection = when (property.targetKind) {
    ViewProjectionKind.VIEW -> {
      val target = checkNotNull(environment.viewElements[property.deserializedType.typeKey])
      val childNamespace = CodeBlock.of("%L + %S", namespace, ".${property.selectionKey}")
      val childSpan = when {
        scope.selected -> {
          val name = scope.nextSpanName()
          scope.function.addStatement(
            "val %N = columns[%L] ?: %L?.plus(%L)",
            name,
            childNamespace,
            viewSpan,
            relativeOffset
          )
          CodeBlock.of("%N", name)
        }
        else -> CodeBlock.of("thisTableOffset + %L", absoluteOffset)
      }
      val child = readTree(
        type = target.modelClassName,
        construction = target.construction,
        properties = target.properties,
        scope = scope,
        namespace = childNamespace,
        viewSpan = childSpan,
        absoluteOffset = absoluteOffset,
        relativeOffset = 0,
        requireAll = true,
        insideEmbedded = false
      )
      CursorReadNode.CompleteViewProjection(
        source = CursorReadProperty.from(property),
        nestedTree = child,
        descendantPositions = child.descendantPositions()
      )
    }
    ViewProjectionKind.TABLE -> {
      val target = checkNotNull(environment.tableElements[property.deserializedType.typeKey])
      val graphPathName = when {
        scope.selected -> projectionGraphPath(
          scope = scope,
          namespace = namespace,
          selectionKey = property.selectionKey
        )
        else -> null
      }
      val child = modelDaoCursorWriter.completeProjectionTree(
        table = target,
        recursive = target.hasRecursiveRelationships,
        resolvePosition = { column ->
          tablePosition(
            scope = scope,
            namespace = namespace,
            viewSpan = viewSpan,
            selectionKey = property.selectionKey,
            graphPathName = graphPathName,
            propertyAbsoluteOffset = absoluteOffset,
            propertyRelativeOffset = relativeOffset,
            column = column
          )
        }
      )
      CursorReadNode.CompleteTableProjection(
        source = CursorReadProperty.from(property),
        nestedTree = child,
        descendantPositions = child.descendantPositions()
      )
    }
  }

  private fun projectionGraphPath(
    scope: ViewCursorScope,
    namespace: CodeBlock,
    selectionKey: String
  ) = scope.nextGraphName().also { name ->
    scope.function
      .beginControlFlow("val %N = tableGraphNodeNames?.let { graph ->", name)
      .beginControlFlow("val matchingIndex = (0 until graph.size()).firstOrNull { index ->")
      .addStatement(
        "graph.keyAt(index).startsWith(%L + %S) && graph.valueAt(index) == %S",
        namespace,
        ".",
        selectionKey
      )
      .endControlFlow()
      .addStatement("matchingIndex?.let(graph::keyAt)")
      .endControlFlow()
  }

  private fun tablePosition(
    scope: ViewCursorScope,
    namespace: CodeBlock,
    viewSpan: CodeBlock,
    selectionKey: String,
    graphPathName: String?,
    propertyAbsoluteOffset: Int,
    propertyRelativeOffset: Int,
    column: CompleteProjectionColumn
  ): CursorPosition {
    if (!scope.selected) {
      return scope.positions.positional(
        code = CodeBlock.of("thisTableOffset + %L", propertyAbsoluteOffset + column.expandedOffset),
        nullCheckName = scope.nextNullCheckName()
      )
    }
    val indexName = scope.nextIndexName()
    val scopedLookup = when {
      column.relationshipPath.isEmpty() -> CodeBlock.of(
        "columns[%L + %S] ?: columns[%L + %S]?.plus(%L)",
        namespace,
        ".$selectionKey.${column.column.columnName}",
        namespace,
        ".$selectionKey",
        column.columnIndex
      )
      else -> {
        val aliasName = "${indexName}Alias"
        val lookupName = "${indexName}Lookup"
        scope.function
          .beginControlFlow("val %N = %N?.let { path ->", aliasName, checkNotNull(graphPathName))
          .addStatement("tableGraphNodeNames.get(path + %S)", column.relationshipPath.joinToString(separator = ""))
          .endControlFlow()
          .beginControlFlow("val %N = %N?.let { alias ->", lookupName, aliasName)
          .addStatement(
            "columns[%L + %S + alias + %S] ?: columns[%L + %S + alias]?.plus(%L)",
            namespace,
            ".",
            ".${column.column.columnName}",
            namespace,
            ".",
            column.columnIndex
          )
          .endControlFlow()
        CodeBlock.of("%N", lookupName)
      }
    }
    return scope.positions.selected(
      function = scope.function,
      indexName = indexName,
      lookup = CodeBlock.of(
        "%L ?: %L?.plus(%L)",
        scopedLookup,
        viewSpan,
        propertyRelativeOffset + column.expandedOffset
      ),
      required = true,
      missingMessage = "Selected columns did not contain required column \"${column.column.columnName}\""
    )
  }
}

private class ViewCursorScope(
  val function: FunSpec.Builder,
  val positions: CursorPositions,
  val selected: Boolean
) {
  private var nextIndex = 0
  private var nextGraph = 0
  private var nextSpan = 0

  fun nextIndexName() = "columnIndex${nextIndex++}"
  fun nextNullCheckName() = "column${nextIndex++}IsNull"
  fun nextGraphName() = "projectionGraphPath${nextGraph++}"
  fun nextSpanName() = "nestedViewOffset${nextSpan++}"
}
