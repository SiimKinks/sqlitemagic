package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.GeneratedNames.CLASS_BULK_DELETE
import com.siimkinks.sqlitemagic.GeneratedNames.CLASS_BULK_INSERT
import com.siimkinks.sqlitemagic.GeneratedNames.CLASS_BULK_PERSIST
import com.siimkinks.sqlitemagic.GeneratedNames.CLASS_BULK_UPDATE
import com.siimkinks.sqlitemagic.GeneratedNames.CLASS_DELETE
import com.siimkinks.sqlitemagic.GeneratedNames.CLASS_DELETE_TABLE
import com.siimkinks.sqlitemagic.GeneratedNames.CLASS_INSERT
import com.siimkinks.sqlitemagic.GeneratedNames.CLASS_PERSIST
import com.siimkinks.sqlitemagic.GeneratedNames.CLASS_UPDATE
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_DELETE
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_DELETE_TABLE
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_INSERT
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_PERSIST
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_UPDATE
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
import com.squareup.kotlinpoet.COLLECTION
import com.squareup.kotlinpoet.ClassName
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
        packageName = generationNames.handlerClassName.packageName,
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
    .addStatement(
      "return %T(this)",
      table.generationNames
        .handlerClassName
        .nestedClass(operation.invocationClassName)
    )
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
      val builderType = table.generationNames
        .handlerClassName
        .nestedClass(operation.invocationClassName)
      when {
        operation.parameterType != null -> function
          .addParameter(
            name = "o",
            type = operation.parameterType.parameterizedBy(table.modelClassName)
          )
          .addStatement("return %T(o)", builderType)
        else -> function.addStatement("return %T()", builderType)
      }
    }
    .build()

  private enum class OperationFunction(
    val isIdentityOperation: Boolean = true,
    val funName: String,
    val invocationClassName: String,
    val returnType: ClassName,
    val byColumnReturnType: ClassName? = null
  ) {
    INSERT(
      isIdentityOperation = false,
      funName = METHOD_INSERT,
      invocationClassName = CLASS_INSERT,
      returnType = ENTITY_INSERT_BUILDER
    ),
    UPDATE(
      funName = METHOD_UPDATE,
      invocationClassName = CLASS_UPDATE,
      returnType = ENTITY_UPDATE_BUILDER,
      byColumnReturnType = ENTITY_UPDATE_BY_COLUMN_BUILDER
    ),
    PERSIST(
      funName = METHOD_PERSIST,
      invocationClassName = CLASS_PERSIST,
      returnType = ENTITY_PERSIST_BUILDER,
      byColumnReturnType = ENTITY_PERSIST_BY_COLUMN_BUILDER
    ),
    DELETE(
      funName = METHOD_DELETE,
      invocationClassName = CLASS_DELETE,
      returnType = ENTITY_DELETE_BUILDER,
      byColumnReturnType = ENTITY_DELETE_BY_COLUMN_BUILDER
    );

    fun returnType(table: TableElement): TypeName = when {
      table.requiresByColumnTerminal && byColumnReturnType != null -> byColumnReturnType
        .parameterizedBy(table.modelClassName)
      else -> returnType
    }
  }

  private enum class BulkOperationFunction(
    val isIdentityOperation: Boolean = true,
    val funName: String,
    val invocationClassName: String,
    val parameterType: ClassName?,
    val returnType: ClassName,
    val byColumnReturnType: ClassName? = null
  ) {
    BULK_INSERT(
      isIdentityOperation = false,
      funName = METHOD_INSERT,
      invocationClassName = CLASS_BULK_INSERT,
      parameterType = ITERABLE,
      returnType = ENTITY_BULK_INSERT_BUILDER
    ),
    BULK_UPDATE(
      funName = METHOD_UPDATE,
      invocationClassName = CLASS_BULK_UPDATE,
      parameterType = ITERABLE,
      returnType = ENTITY_BULK_UPDATE_BUILDER,
      byColumnReturnType = ENTITY_BULK_UPDATE_BY_COLUMN_BUILDER
    ),
    BULK_PERSIST(
      funName = METHOD_PERSIST,
      invocationClassName = CLASS_BULK_PERSIST,
      parameterType = ITERABLE,
      returnType = ENTITY_BULK_PERSIST_BUILDER,
      byColumnReturnType = ENTITY_BULK_PERSIST_BY_COLUMN_BUILDER
    ),
    BULK_DELETE(
      funName = METHOD_DELETE,
      invocationClassName = CLASS_BULK_DELETE,
      parameterType = COLLECTION,
      returnType = ENTITY_BULK_DELETE_BUILDER,
      byColumnReturnType = ENTITY_BULK_DELETE_BY_COLUMN_BUILDER
    ),
    DELETE_TABLE(
      isIdentityOperation = false,
      funName = METHOD_DELETE_TABLE,
      invocationClassName = CLASS_DELETE_TABLE,
      parameterType = null,
      returnType = ENTITY_DELETE_TABLE_BUILDER
    );

    fun returnType(table: TableElement): TypeName = when {
      table.requiresByColumnTerminal && byColumnReturnType != null -> byColumnReturnType
        .parameterizedBy(table.modelClassName)
      else -> returnType
    }
  }
}
