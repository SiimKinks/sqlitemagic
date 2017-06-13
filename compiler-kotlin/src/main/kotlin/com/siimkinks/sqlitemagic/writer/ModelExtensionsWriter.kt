package com.siimkinks.sqlitemagic.writer

import com.siimkinks.sqlitemagic.*
import com.siimkinks.sqlitemagic.element.TableElement
import com.siimkinks.sqlitemagic.util.NameConst.*
import com.squareup.kotlinpoet.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelExtensionsWriter @Inject constructor() {
  fun writeSource(targetDir: File, tableElement: TableElement) {
    val className = tableElement.tableElement.simpleName.toString()
    targetDir.writeSource(fileName = "_$className") { fileBuilder ->
      val entityEnvironment = EntityEnvironment(tableElement, tableElement.tableElementTypeName)
      Method.values()
          .forEach {
            fileBuilder.addFun(generateMethod(tableElement, entityEnvironment, it))
          }
      fileBuilder.addType(TypeSpec.classBuilder("${className}s")
          .companionObject(TypeSpec.companionObjectBuilder()
              .also { companionBuilder ->
                companionBuilder.addFun(generateDeleteTable(entityEnvironment))
                BulkMethod.values()
                    .forEach {
                      companionBuilder.addFun(generateBulkMethod(tableElement, entityEnvironment, it))
                    }
              }
              .build())
          .build())
    }
  }

  private fun generateMethod(tableElement: TableElement,
                             entityEnvironment: EntityEnvironment,
                             method: Method): FunSpec = FunSpec
      .builder(method.funName)
      .addModifiers(KModifier.INLINE)
      .addAnnotation(NOTHING_TO_INLINE)
      .receiver(tableElement.tableElement.asTypeName())
      .returns(method.returnType)
      .addStatement("return %T.create(this)",
          getHandlerInnerClassName(entityEnvironment, method.invocationClassName))
      .build()

  private fun generateBulkMethod(tableElement: TableElement,
                                 entityEnvironment: EntityEnvironment,
                                 method: BulkMethod): FunSpec {
    val tableElementType = tableElement.tableElement.asTypeName()
    return FunSpec
        .builder(method.funName)
        .addModifiers(KModifier.INLINE)
        .addAnnotation(NOTHING_TO_INLINE)
        .returns(if (method.returnTypeWithGenerics) ParameterizedTypeName.get(method.returnType, tableElementType) else method.returnType)
        .addParameter("o", ParameterizedTypeName.get(method.parameterType, tableElementType))
        .addStatement("return %T.create(o)",
            getHandlerInnerClassName(entityEnvironment, method.invocationClassName))
        .build()
  }

  private fun generateDeleteTable(entityEnvironment: EntityEnvironment): FunSpec = FunSpec
      .builder(METHOD_DELETE_TABLE)
      .addModifiers(KModifier.INLINE)
      .addAnnotation(NOTHING_TO_INLINE)
      .returns(ENTITY_DELETE_TABLE_BUILDER)
      .addStatement("return %T.create()",
          getHandlerInnerClassName(entityEnvironment, CLASS_DELETE_TABLE))
      .build()

  private enum class Method(val funName: String,
                            val returnType: ClassName,
                            val invocationClassName: String) {
    INSERT(funName = METHOD_INSERT, returnType = ENTITY_INSERT_BUILDER, invocationClassName = CLASS_INSERT),
    UPDATE(funName = METHOD_UPDATE, returnType = ENTITY_UPDATE_BUILDER, invocationClassName = CLASS_UPDATE),
    PERSIST(funName = METHOD_PERSIST, returnType = ENTITY_PERSIST_BUILDER, invocationClassName = CLASS_PERSIST),
    DELETE(funName = METHOD_DELETE, returnType = ENTITY_DELETE_BUILDER, invocationClassName = CLASS_DELETE)
  }

  private enum class BulkMethod(val funName: String,
                                val returnType: ClassName,
                                val invocationClassName: String,
                                val parameterType: ClassName,
                                val returnTypeWithGenerics: Boolean = true) {
    BULK_INSERT(funName = METHOD_INSERT, returnType = ENTITY_BULK_INSERT_BUILDER, invocationClassName = CLASS_BULK_INSERT, parameterType = ITERABLE),
    BULK_UPDATE(funName = METHOD_UPDATE, returnType = ENTITY_BULK_UPDATE_BUILDER, invocationClassName = CLASS_BULK_UPDATE, parameterType = ITERABLE, returnTypeWithGenerics = false),
    BULK_PERSIST(funName = METHOD_PERSIST, returnType = ENTITY_BULK_PERSIST_BUILDER, invocationClassName = CLASS_BULK_PERSIST, parameterType = ITERABLE),
    BULK_DELETE(funName = METHOD_DELETE, returnType = ENTITY_BULK_DELETE_BUILDER, invocationClassName = CLASS_BULK_DELETE, parameterType = COLLECTION)
  }
}