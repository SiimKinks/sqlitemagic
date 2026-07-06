package com.siimkinks.sqlitemagic.writer

import com.siimkinks.sqlitemagic.*
import com.siimkinks.sqlitemagic.BaseProcessor.PUBLIC_EXTENSIONS
import com.siimkinks.sqlitemagic.element.TableElement
import com.siimkinks.sqlitemagic.util.NameConst.*
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.stream.XMLEventWriter

@Singleton
class ModelExtensionsWriter @Inject constructor() {
  fun writeSource(targetDir: File, tableElement: TableElement) {
    val className = tableElement.tableElement.simpleName.toString()
    targetDir.writeSource(fileName = "_$className") { fileBuilder ->
      val entityEnvironment = EntityEnvironment(tableElement, tableElement.tableElementTypeName)
      Method.entries.forEach {
        fileBuilder.addFunction(generateMethod(tableElement, entityEnvironment, it))
      }
      fileBuilder
        .addAnnotation(NOTHING_TO_INLINE)
        .addType(
          TypeSpec.objectBuilder("${className}s")
            .also { objectBuilder ->
              if (!PUBLIC_EXTENSIONS) {
                objectBuilder.addModifiers(KModifier.INTERNAL)
              }
              objectBuilder.addFunction(generateDeleteTable(entityEnvironment))
              BulkMethod.entries.forEach {
                objectBuilder.addFunction(generateBulkMethod(tableElement, entityEnvironment, it))
              }
            }
            .build()
        )
    }
  }

  private fun generateMethod(
    tableElement: TableElement,
    entityEnvironment: EntityEnvironment,
    method: Method
  ): FunSpec = FunSpec
    .builder(method.funName)
    .addModifiers(EXTENSION_FUN_MODIFIERS)
    .receiver(tableElement.tableElement.asTypeName())
    .returns(method.returnType)
    .addStatement(
      "return %T.create(this)",
      getHandlerInnerClassName(entityEnvironment, method.invocationClassName)
    )
    .build()

  private fun generateBulkMethod(
    tableElement: TableElement,
    entityEnvironment: EntityEnvironment,
    method: BulkMethod
  ): FunSpec {
    val tableElementType = tableElement.tableElement.asTypeName()
    return FunSpec
      .builder(method.funName)
      .addModifiers(EXTENSION_FUN_MODIFIERS)
      .addParameter("o", method.parameterType.parameterizedBy(tableElementType))
      .returns(method.returnType)
      .addStatement(
        "return %T.create(o)",
        getHandlerInnerClassName(entityEnvironment, method.invocationClassName)
      )
      .build()
  }

  private fun generateDeleteTable(entityEnvironment: EntityEnvironment): FunSpec = FunSpec
    .builder(METHOD_DELETE_TABLE)
    .addModifiers(EXTENSION_FUN_MODIFIERS)
    .returns(ENTITY_DELETE_TABLE_BUILDER)
    .addStatement(
      "return %T.create()",
      getHandlerInnerClassName(entityEnvironment, CLASS_DELETE_TABLE)
    )
    .build()

  private enum class Method(
    val funName: String,
    val invocationClassName: String,
    val returnType: ClassName
  ) {
    INSERT(
      funName = METHOD_INSERT,
      invocationClassName = CLASS_INSERT,
      returnType = ENTITY_INSERT_BUILDER
    ),
    UPDATE(
      funName = METHOD_UPDATE,
      invocationClassName = CLASS_UPDATE,
      returnType = ENTITY_UPDATE_BUILDER
    ),
    PERSIST(
      funName = METHOD_PERSIST,
      invocationClassName = CLASS_PERSIST,
      returnType = ENTITY_PERSIST_BUILDER
    ),
    DELETE(
      funName = METHOD_DELETE,
      invocationClassName = CLASS_DELETE,
      returnType = ENTITY_DELETE_BUILDER
    )
  }

  private enum class BulkMethod(
    val funName: String,
    val invocationClassName: String,
    val parameterType: ClassName,
    val returnType: ClassName
  ) {
    BULK_INSERT(
      funName = METHOD_INSERT,
      invocationClassName = CLASS_BULK_INSERT,
      parameterType = ITERABLE,
      returnType = ENTITY_BULK_INSERT_BUILDER
    ),
    BULK_UPDATE(
      funName = METHOD_UPDATE,
      invocationClassName = CLASS_BULK_UPDATE,
      parameterType = ITERABLE,
      returnType = ENTITY_BULK_UPDATE_BUILDER
    ),
    BULK_PERSIST(
      funName = METHOD_PERSIST,
      invocationClassName = CLASS_BULK_PERSIST,
      parameterType = ITERABLE,
      returnType = ENTITY_BULK_PERSIST_BUILDER
    ),
    BULK_DELETE(
      funName = METHOD_DELETE,
      invocationClassName = CLASS_BULK_DELETE,
      parameterType = COLLECTION,
      returnType = ENTITY_BULK_DELETE_BUILDER
    )
  }
}