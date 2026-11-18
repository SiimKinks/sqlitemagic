package com.siimkinks.sqlitemagic.writer

import com.google.devtools.ksp.processing.CodeGenerator
import com.siimkinks.sqlitemagic.model.writeModelSource
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.PropertySpec

internal class FullTextTableStructureWriter(
  private val className: ClassName,
  private val superclass: ClassName,
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
      tableStructureType(
        className = className,
        superclass = superclass,
        isPublic = isPublic,
        constructorArguments = constructorArguments,
        columns = columns,
        relationshipIdEncodings = relationshipIdEncodings,
        fieldName = fieldName
      )
    )
    .writeModelSource(
      codeGenerator = codeGenerator,
      originatingFiles = originatingFiles
    )
}
