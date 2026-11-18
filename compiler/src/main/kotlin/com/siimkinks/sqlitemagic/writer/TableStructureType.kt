package com.siimkinks.sqlitemagic.writer

import com.siimkinks.sqlitemagic.GeneratedNames.CLASS_RELATIONSHIP_ID_ENCODINGS
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_AS
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_ALIAS
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.INFIX
import com.squareup.kotlinpoet.KModifier.INTERNAL
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

internal fun tableStructureType(
  className: ClassName,
  superclass: TypeName,
  isPublic: Boolean,
  constructorArguments: List<CodeBlock>,
  columns: List<PropertySpec>,
  relationshipIdEncodings: List<PropertySpec>,
  fieldName: String,
  members: TypeSpec.Builder.() -> Unit = {},
  companionMembers: TypeSpec.Builder.() -> Unit = {}
) = TypeSpec
  .classBuilder(className)
  .primaryConstructor(
    FunSpec
      .constructorBuilder()
      .addModifiers(PRIVATE)
      .addParameter(name = VARIABLE_ALIAS, type = STRING.copy(nullable = true))
      .build()
  )
  .superclass(superclass)
  .apply {
    if (!isPublic) {
      addModifiers(INTERNAL)
    }
    constructorArguments.forEach {
      addSuperclassConstructorParameter("%L", it)
    }
  }
  .addProperties(columns)
  .apply(members)
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
      .apply(companionMembers)
      .build()
  )
  .build()
