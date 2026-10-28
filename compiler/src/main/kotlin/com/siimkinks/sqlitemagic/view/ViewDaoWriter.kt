package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_GENERATED_VIEW
import com.siimkinks.sqlitemagic.WriterTypes.GENERATED_VIEW
import com.siimkinks.sqlitemagic.WriterTypes.VIEW_BLOCK
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import com.siimkinks.sqlitemagic.model.ModelConstruction
import com.siimkinks.sqlitemagic.model.writeModelSource
import com.siimkinks.sqlitemagic.utils.callCode
import com.siimkinks.sqlitemagic.utils.namedArgument
import com.siimkinks.sqlitemagic.writer.CursorPosition
import com.siimkinks.sqlitemagic.writer.CursorReadNode
import com.siimkinks.sqlitemagic.writer.CursorReadTree
import com.siimkinks.sqlitemagic.writer.cursorPositionReader
import com.siimkinks.sqlitemagic.writer.descendantPositions
import com.siimkinks.sqlitemagic.writer.selectedPositionsReader
import com.siimkinks.sqlitemagic.writer.toCursorReadProperty
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.INTERNAL
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

internal class ViewDaoWriter(
  private val environment: Environment
) : ViewWriter {
  override fun write(roundElement: ViewRoundElement) {
    val view = roundElement.view
    FileSpec
      .builder(view.generationNames.daoClassName)
      .addType(
        TypeSpec
          .objectBuilder(view.generationNames.daoClassName)
          .addModifiers(INTERNAL)
          .addProperty(generatedView(view))
          .addFunction(reader(view))
          .apply {
            if (view.supportsPartialRead) {
              addFunction(selectedReader(view))
            }
          }
          .build()
      )
      .writeModelSource(
        codeGenerator = environment.codeGenerator,
        originatingFiles = roundElement.originatingFiles
      )
  }

  private fun generatedView(view: ViewElement) = PropertySpec
    .builder(name = FIELD_GENERATED_VIEW, type = GENERATED_VIEW)
    .addModifiers(INTERNAL)
    .initializer(
      callCode(
        callee = CodeBlock.of("%T", GENERATED_VIEW),
        arguments = buildList {
          add(namedArgument("viewName", "%S", view.viewName))
          add(namedArgument("temporary", "%L", view.schema == TEMPORARY))
          add(namedArgument("query", "{ %T.%N }", view.modelClassName, view.queryPropertyName))
          add(namedArgument("columns", "%L", columnNames(view)))
          if (view.hasBlocks) {
            add(
              namedArgument(
                "blocks",
                "%L",
                callCode(
                  callee = CodeBlock.of("listOf"),
                  arguments = view.blocks.map(::viewBlock)
                )
              )
            )
          }
        }
      )
    )
    .build()

  private fun columnNames(view: ViewElement) = when {
    view.scalarProperties.isEmpty() -> CodeBlock.of("emptyList()")
    else -> callCode(
      callee = CodeBlock.of("listOf"),
      arguments = view.scalarProperties.map { CodeBlock.of("%S", it.selectionKey) }
    )
  }

  private fun viewBlock(block: ViewProjectionPropertyElement) = callCode(
    callee = CodeBlock.of("%T", VIEW_BLOCK),
    arguments = listOf(
      namedArgument("key", "%S", block.selectionKey),
      namedArgument("target", "%L", block.target.tableHandle)
    )
  )

  private fun reader(view: ViewElement): FunSpec {
    val blockIndexes = view.blocks
      .withIndex()
      .associate { (index, block) -> block to index }
    return cursorPositionReader(
      returnType = view.modelClassName,
      readsRelated = view.hasBlocks,
      columns = view.scalarProperties,
      tree = { positions ->
        readTree(
          viewName = view.viewName,
          type = view.modelClassName,
          construction = view.construction,
          properties = view.properties,
          scalarPositions = positions,
          blockIndexes = blockIndexes
        )
      }
    )
  }

  /** Required columns are always selected, since the runtime checks them before SQL. */
  private fun selectedReader(view: ViewElement) = selectedPositionsReader(
    returnType = view.modelClassName,
    readsRelated = false,
    columns = view.scalarProperties,
    mayBeMissing = { it !in view.partialRequiredLeaves },
    tree = { positions ->
      readTree(
        viewName = view.viewName,
        type = view.modelClassName,
        construction = view.construction,
        properties = view.properties,
        scalarPositions = positions,
        blockIndexes = null
      )
    }
  )

  /** [blockIndexes] is null for a partial read, whose blocks read null. */
  private fun readTree(
    viewName: String,
    type: TypeName,
    construction: ModelConstruction,
    properties: List<ViewPropertyElement>,
    scalarPositions: Map<ViewScalarPropertyElement, CursorPosition>,
    blockIndexes: Map<ViewProjectionPropertyElement, Int>?
  ): CursorReadTree = CursorReadTree(
    type = type,
    construction = construction,
    nodes = properties.map { property ->
      when (property) {
        is ViewScalarPropertyElement -> CursorReadNode.Scalar(
          source = property.toCursorReadProperty(),
          position = scalarPositions.getValue(property),
          storageType = property.sqlStorageType,
          transformer = property.transformer,
          isSqlNullable = true,
          requiresPresentValue = property.transformer?.decoderInputNullable != true,
          missingMessage = """Selected columns did not contain view "$viewName" required column """ +
              """"${property.selectionKey}"""",
          nullMessage = """Column "${property.selectionKey}" was NULL"""
        )
        is ViewEmbeddedPropertyElement -> {
          val child = readTree(
            viewName = viewName,
            type = property.deserializedType.typeName,
            construction = property.construction,
            properties = property.properties,
            scalarPositions = scalarPositions,
            blockIndexes = blockIndexes
          )
          CursorReadNode.Embedded(
            source = property.toCursorReadProperty(),
            tree = child,
            positions = child.descendantPositions()
          )
        }
        is ViewProjectionPropertyElement -> CursorReadNode.Related(
          source = property.toCursorReadProperty(),
          read = blockIndexes?.let { indexes ->
            blockRead(
              block = property,
              index = indexes.getValue(property)
            )
          }
        )
      }
    }
  )

  private fun blockRead(block: ViewProjectionPropertyElement, index: Int): CodeBlock {
    val type = block.deserializedType.typeName.copy(nullable = false)
    return when {
      block.isNullable -> CodeBlock.of("related.readOrNull(cursor, %L) as %T?", index, type)
      else -> CodeBlock.of("related.read(cursor, %L) as %T", index, type)
    }
  }
}
