package com.siimkinks.sqlitemagic.writer

import com.google.devtools.ksp.processing.CodeGenerator
import com.siimkinks.sqlitemagic.GeneratedNames.CLASS_RELATIONSHIP_ID_ENCODINGS
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_AS
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_ALIAS
import com.siimkinks.sqlitemagic.WriterTypes.FTS5_TABLE
import com.siimkinks.sqlitemagic.model.writeModelSource
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.INFIX
import com.squareup.kotlinpoet.KModifier.INTERNAL
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec

internal class FullTextTableStructureWriter(
  private val className: ClassName,
  private val fieldName: String,
  private val isPublic: Boolean,
  private val constructorArguments: List<CodeBlock>,
  private val columns: List<PropertySpec>,
  private val relationshipIdEncodings: List<PropertySpec>
) {
  fun write(
    codeGenerator: CodeGenerator,
    originatingFiles: OriginatingFiles
  ) = FileSpec
    .builder(className)
    .addType(
      TypeSpec
        .classBuilder(className)
        .primaryConstructor(
          FunSpec
            .constructorBuilder()
            .addModifiers(PRIVATE)
            .addParameter(name = VARIABLE_ALIAS, type = STRING.copy(nullable = true))
            .build()
        )
        .superclass(FTS5_TABLE)
        .apply {
          if (!isPublic) {
            addModifiers(INTERNAL)
          }
          constructorArguments.forEach {
            addSuperclassConstructorParameter("%L", it)
          }
        }
        .addProperties(columns)
        .addFunction(
          FunSpec
            .builder(METHOD_AS)
            .addModifiers(OVERRIDE, INFIX)
            .addParameter(name = VARIABLE_ALIAS, type = STRING)
            .returns(className)
            .addStatement("return %T(%N)", className, VARIABLE_ALIAS)
            .build()
        )
        .apply {
          if (relationshipIdEncodings.isNotEmpty()) {
            addType(
              TypeSpec
                .objectBuilder(CLASS_RELATIONSHIP_ID_ENCODINGS)
                .addModifiers(PRIVATE)
                .addProperties(relationshipIdEncodings)
                .build()
            )
          }
        }
        .addType(
          TypeSpec
            .companionObjectBuilder()
            .addProperty(
              PropertySpec
                .builder(name = fieldName, type = className)
                .initializer("%T(null)", className)
                .build()
            )
            .build()
        )
        .build()
    )
    .writeModelSource(
      codeGenerator = codeGenerator,
      originatingFiles = originatingFiles
    )
}
