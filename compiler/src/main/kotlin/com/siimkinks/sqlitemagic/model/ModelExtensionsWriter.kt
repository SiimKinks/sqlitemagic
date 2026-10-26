package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_DELETE
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_DELETE_TABLE
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_INSERT
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_PERSIST
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_UPDATE
import com.siimkinks.sqlitemagic.GeneratedNames.PACKAGE_ROOT
import com.siimkinks.sqlitemagic.WriterTypes.BULK_DELETE_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.BULK_DELETE_BY_COLUMN_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.BULK_INSERT_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.BULK_PERSIST_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.BULK_PERSIST_BY_COLUMN_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.BULK_UPDATE_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.BULK_UPDATE_BY_COLUMN_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.DELETE_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.DELETE_BY_COLUMN_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.DELETE_TABLE_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_BULK_DELETE_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_BULK_DELETE_BY_COLUMN_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_BULK_INSERT_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_BULK_PERSIST_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_BULK_PERSIST_BY_COLUMN_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_BULK_UPDATE_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_BULK_UPDATE_BY_COLUMN_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_DELETE_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_DELETE_BY_COLUMN_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_DELETE_TABLE_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_INSERT_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_PERSIST_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_PERSIST_BY_COLUMN_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_UPDATE_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_UPDATE_BY_COLUMN_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.INSERT_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.PERSIST_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.PERSIST_BY_COLUMN_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.UPDATE_BUILDER
import com.siimkinks.sqlitemagic.WriterTypes.UPDATE_BY_COLUMN_BUILDER
import com.siimkinks.sqlitemagic.utils.callCode
import com.siimkinks.sqlitemagic.utils.namedArgument
import com.squareup.kotlinpoet.COLLECTION
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ITERABLE
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.KModifier.INTERNAL
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.buildCodeBlock

internal class ModelExtensionsWriter(
  private val environment: Environment
) : ModelWriter {
  private val publicExtensions = environment.options.publicExtensions

  override fun write(tableRoundElement: TableRoundElement) = with(tableRoundElement) {
    val visibility = when {
      publicExtensions && table.isPublic -> PUBLIC
      else -> INTERNAL
    }
    val supportsIdentityOperations = table.supportsIdentityOperations
    val generationNames = table.generationNames

    FileSpec
      .builder(
        packageName = PACKAGE_ROOT,
        fileName = generationNames.extensionsFileName
      )
      .addFunctions(
        OperationFunction
          .entries
          .filter { !it.isIdentityOperation || supportsIdentityOperations }
          .map { operation ->
            entityOperationExtension(
              table = table,
              operation = operation,
              visibility = visibility
            )
          }
      )
      .addType(
        TypeSpec
          .objectBuilder(generationNames.bulkOperationsObjectName)
          .addModifiers(visibility)
          .addFunctions(
            BulkOperationFunction
              .entries
              .filter { !it.isIdentityOperation || supportsIdentityOperations }
              .map { operation ->
                bulkOperationExtension(
                  table = table,
                  operation = operation,
                  visibility = visibility
                )
              }
          )
          .build()
      )
      .writeModelSource(
        codeGenerator = environment.codeGenerator,
        originatingFiles = originatingFiles
      )
  }

  private fun entityOperationExtension(
    table: TableElement,
    operation: OperationFunction,
    visibility: KModifier
  ) = FunSpec
    .builder(operation.funName)
    .receiver(table.modelClassName)
    .addModifiers(visibility)
    .returns(operation.returnType(table))
    .addCode(
      operationBuilderCall(
        table = table,
        operation = operation
      )
    )
    .build()

  private fun bulkOperationExtension(
    table: TableElement,
    operation: BulkOperationFunction,
    visibility: KModifier
  ) = FunSpec
    .builder(operation.funName)
    .addModifiers(visibility)
    .returns(operation.returnType(table))
    .apply {
      operation.parameterType?.let {
        addParameter(
          name = "o",
          type = it.parameterizedBy(table.modelClassName)
        )
      }
    }
    .addCode(
      bulkOperationBuilderCall(
        table = table,
        operation = operation
      )
    )
    .build()

  private fun operationBuilderCall(
    table: TableElement,
    operation: OperationFunction
  ) = builderCall(
    builderType = operation.builderType(table),
    arguments = listOf(
      namedArgument("adapter", "%T", table.generationNames.adapterClassName),
      namedArgument("entity", "this")
    )
  )

  private fun bulkOperationBuilderCall(
    table: TableElement,
    operation: BulkOperationFunction
  ) = builderCall(
    builderType = operation.builderType(table),
    arguments = listOfNotNull(
      namedArgument("adapter", "%T", table.generationNames.adapterClassName),
      operation.parameterType?.let { namedArgument("entities", "o") }
    )
  )

  private fun builderCall(
    builderType: ClassName,
    arguments: List<CodeBlock>
  ) = buildCodeBlock {
    addStatement(
      "return %L",
      callCode(
        callee = CodeBlock.of("%T", builderType),
        arguments = arguments
      )
    )
  }

  private interface OperationTypes {
    val returnType: ClassName
    val builderType: ClassName
    val byColumnReturnType: ClassName?
    val byColumnBuilderType: ClassName?

    fun returnType(table: TableElement) = byColumnReturnType
      ?.takeIf { table.requiresByColumnTerminal }
      ?.parameterizedBy(table.modelClassName)
      ?: returnType

    fun builderType(table: TableElement) = byColumnBuilderType
      ?.takeIf { table.requiresByColumnTerminal }
      ?: builderType
  }

  private enum class OperationFunction(
    val isIdentityOperation: Boolean = true,
    val funName: String,
    override val returnType: ClassName,
    override val builderType: ClassName,
    override val byColumnReturnType: ClassName? = null,
    override val byColumnBuilderType: ClassName? = null
  ) : OperationTypes {
    INSERT(
      isIdentityOperation = false,
      funName = METHOD_INSERT,
      returnType = ENTITY_INSERT_BUILDER,
      builderType = INSERT_BUILDER
    ),
    UPDATE(
      funName = METHOD_UPDATE,
      returnType = ENTITY_UPDATE_BUILDER,
      builderType = UPDATE_BUILDER,
      byColumnReturnType = ENTITY_UPDATE_BY_COLUMN_BUILDER,
      byColumnBuilderType = UPDATE_BY_COLUMN_BUILDER
    ),
    PERSIST(
      funName = METHOD_PERSIST,
      returnType = ENTITY_PERSIST_BUILDER,
      builderType = PERSIST_BUILDER,
      byColumnReturnType = ENTITY_PERSIST_BY_COLUMN_BUILDER,
      byColumnBuilderType = PERSIST_BY_COLUMN_BUILDER
    ),
    DELETE(
      funName = METHOD_DELETE,
      returnType = ENTITY_DELETE_BUILDER,
      builderType = DELETE_BUILDER,
      byColumnReturnType = ENTITY_DELETE_BY_COLUMN_BUILDER,
      byColumnBuilderType = DELETE_BY_COLUMN_BUILDER
    )
  }

  private enum class BulkOperationFunction(
    val isIdentityOperation: Boolean = true,
    val funName: String,
    val parameterType: ClassName?,
    override val returnType: ClassName,
    override val builderType: ClassName,
    override val byColumnReturnType: ClassName? = null,
    override val byColumnBuilderType: ClassName? = null
  ) : OperationTypes {
    BULK_INSERT(
      isIdentityOperation = false,
      funName = METHOD_INSERT,
      parameterType = ITERABLE,
      returnType = ENTITY_BULK_INSERT_BUILDER,
      builderType = BULK_INSERT_BUILDER
    ),
    BULK_UPDATE(
      funName = METHOD_UPDATE,
      parameterType = ITERABLE,
      returnType = ENTITY_BULK_UPDATE_BUILDER,
      builderType = BULK_UPDATE_BUILDER,
      byColumnReturnType = ENTITY_BULK_UPDATE_BY_COLUMN_BUILDER,
      byColumnBuilderType = BULK_UPDATE_BY_COLUMN_BUILDER
    ),
    BULK_PERSIST(
      funName = METHOD_PERSIST,
      parameterType = ITERABLE,
      returnType = ENTITY_BULK_PERSIST_BUILDER,
      builderType = BULK_PERSIST_BUILDER,
      byColumnReturnType = ENTITY_BULK_PERSIST_BY_COLUMN_BUILDER,
      byColumnBuilderType = BULK_PERSIST_BY_COLUMN_BUILDER
    ),
    BULK_DELETE(
      funName = METHOD_DELETE,
      parameterType = COLLECTION,
      returnType = ENTITY_BULK_DELETE_BUILDER,
      builderType = BULK_DELETE_BUILDER,
      byColumnReturnType = ENTITY_BULK_DELETE_BY_COLUMN_BUILDER,
      byColumnBuilderType = BULK_DELETE_BY_COLUMN_BUILDER
    ),
    DELETE_TABLE(
      isIdentityOperation = false,
      funName = METHOD_DELETE_TABLE,
      parameterType = null,
      returnType = ENTITY_DELETE_TABLE_BUILDER,
      builderType = DELETE_TABLE_BUILDER
    )
  }
}
