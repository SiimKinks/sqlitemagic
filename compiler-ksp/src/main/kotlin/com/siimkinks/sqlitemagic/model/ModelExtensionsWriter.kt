package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_DELETE
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_DELETE_TABLE
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_INSERT
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_PERSIST
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_UPDATE
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
import com.squareup.kotlinpoet.COLLECTION
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ITERABLE
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

internal class ModelExtensionsWriter(
  private val environment: Environment
) : ModelWriter {
  val publicExtensions = environment.options.publicExtensions

  override fun write(tableRoundElement: TableRoundElement) = with(tableRoundElement) {
    val visibility = when {
      publicExtensions && table.isPublic -> KModifier.PUBLIC
      else -> KModifier.INTERNAL
    }
    val supportsIdentityOperations = table.supportsIdentityOperations
    val generationNames = table.generationNames

    FileSpec
      .builder(
        packageName = generationNames.adapterClassName.packageName,
        fileName = generationNames.extensionsFileName
      )
      .also { file ->
        OperationFunction
          .entries
          .filter { !it.isIdentityOperation || supportsIdentityOperations }
          .forEach { operation ->
            file.addFunction(
              entityOperationExtension(
                table = table,
                operation = operation,
                visibility = visibility
              )
            )
          }

        file.addType(
          TypeSpec
            .objectBuilder(generationNames.bulkOperationsObjectName)
            .addModifiers(visibility)
            .also { bulkOperationsObject ->
              BulkOperationFunction
                .entries
                .filter { !it.isIdentityOperation || supportsIdentityOperations }
                .forEach { operation ->
                  bulkOperationsObject.addFunction(
                    bulkOperationExtension(
                      table = table,
                      operation = operation,
                      visibility = visibility
                    )
                  )
                }
            }
            .build()
        )
      }
      .build()
      .writeModelSource(
        codeGenerator = environment.codeGenerator,
        originatingFiles = originatingFiles
      )
  }

  private fun entityOperationExtension(
    table: TableElement,
    operation: OperationFunction,
    visibility: KModifier
  ): FunSpec = FunSpec
    .builder(operation.funName)
    .receiver(table.modelClassName)
    .addModifiers(visibility)
    .returns(operation.returnType(table))
    .addCode(operationBuilderCall(table, operation))
    .build()

  private fun bulkOperationExtension(
    table: TableElement,
    operation: BulkOperationFunction,
    visibility: KModifier
  ): FunSpec = FunSpec
    .builder(operation.funName)
    .addModifiers(visibility)
    .returns(operation.returnType(table))
    .also { function ->
      when {
        operation.parameterType != null -> function
          .addParameter(
            name = "o",
            type = operation.parameterType.parameterizedBy(table.modelClassName)
          )
          .addCode(bulkOperationBuilderCall(table, operation))
        else -> function.addCode(bulkOperationBuilderCall(table, operation))
      }
    }
    .build()

  private fun operationBuilderCall(
    table: TableElement,
    operation: OperationFunction
  ): CodeBlock {
    val adapterClassName = table.generationNames.adapterClassName
    return CodeBlock
      .builder()
      .add("return %T(\n", operation.builderType(table))
      .indent()
      .add("adapter = %T,\n", adapterClassName)
      .apply {
        when (operation) {
          OperationFunction.DELETE -> {
            add("entity = this")
            if (!table.requiresByColumnTerminal) {
              add(",\nbyColumn = %T.defaultIdentityColumn", adapterClassName)
            }
          }
          else -> add("entity = this")
        }
      }
      .unindent()
      .add("\n)")
      .build()
  }

  private fun bulkOperationBuilderCall(
    table: TableElement,
    operation: BulkOperationFunction
  ): CodeBlock {
    val adapterClassName = table.generationNames.adapterClassName
    return CodeBlock
      .builder()
      .add("return %T(\n", operation.builderType(table))
      .indent()
      .add("adapter = %T", adapterClassName)
      .apply {
        if (operation.parameterType != null) {
          add(",\nentities = o")
          if (operation == BulkOperationFunction.BULK_DELETE && !table.requiresByColumnTerminal) {
            add(",\nbyColumn = %T.defaultIdentityColumn", adapterClassName)
          }
        }
      }
      .unindent()
      .add("\n)")
      .build()
  }

  private enum class OperationFunction(
    val isIdentityOperation: Boolean = true,
    val funName: String,
    val returnType: ClassName,
    val builderType: ClassName,
    val byColumnReturnType: ClassName? = null,
    val byColumnBuilderType: ClassName? = null
  ) {
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
    );

    fun returnType(table: TableElement): TypeName = when {
      table.requiresByColumnTerminal && byColumnReturnType != null -> byColumnReturnType
        .parameterizedBy(table.modelClassName)
      else -> returnType
    }

    fun builderType(table: TableElement) = when {
      table.requiresByColumnTerminal && byColumnBuilderType != null -> byColumnBuilderType
      else -> builderType
    }
  }

  private enum class BulkOperationFunction(
    val isIdentityOperation: Boolean = true,
    val funName: String,
    val parameterType: ClassName?,
    val returnType: ClassName,
    val builderType: ClassName,
    val byColumnReturnType: ClassName? = null,
    val byColumnBuilderType: ClassName? = null
  ) {
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
    );

    fun returnType(table: TableElement): TypeName = when {
      table.requiresByColumnTerminal && byColumnReturnType != null -> byColumnReturnType
        .parameterizedBy(table.modelClassName)
      else -> returnType
    }

    fun builderType(table: TableElement) = when {
      table.requiresByColumnTerminal && byColumnBuilderType != null -> byColumnBuilderType
      else -> builderType
    }
  }
}
