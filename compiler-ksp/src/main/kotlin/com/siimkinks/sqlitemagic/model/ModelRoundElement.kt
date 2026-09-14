package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Embedded
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
import com.siimkinks.sqlitemagic.annotation.Index
import com.siimkinks.sqlitemagic.annotation.Unique
import com.siimkinks.sqlitemagic.element.RoundTypeElement
import com.siimkinks.sqlitemagic.utils.findAnnotationWithType
import com.siimkinks.sqlitemagic.utils.firstUncheckedAnnotation
import com.siimkinks.sqlitemagic.utils.qualifiedNameOrSimpleName
import com.siimkinks.sqlitemagic.writer.OriginatingFiles

internal data class PropertySourceKey(
  val parentName: String,
  val propertyName: String
)

interface RoundPropertyShape {
  val sourceDeclaration: KSDeclaration
  val isConstructorProperty: Boolean
  val isInherited: Boolean
  val isMutable: Boolean
  val isReadable: Boolean
  val isWritable: Boolean
  val name: String
}

data class RoundPropertyMetadata(
  val roundTypeElement: RoundTypeElement,
  override val sourceDeclaration: KSDeclaration,
  override val isConstructorProperty: Boolean,
  override val isInherited: Boolean,
  override val isMutable: Boolean,
  override val isReadable: Boolean,
  override val isWritable: Boolean
) : RoundTypeElement by roundTypeElement, RoundPropertyShape {
  override val name get() = sourceDeclaration.simpleName.asString()
}

data class PropertyRoundAnnotations(
  val column: Column?,
  val embedded: Embedded?,
  val id: KSAnnotation?,
  val ignoreColumn: IgnoreColumn?,
  val index: Index?,
  val unique: Unique?,
) {
  companion object {
    fun from(symbol: KSAnnotated) = with(symbol) {
      PropertyRoundAnnotations(
        column = findAnnotationWithType<Column>(),
        embedded = findAnnotationWithType<Embedded>(),
        id = firstUncheckedAnnotation<Id>(),
        ignoreColumn = findAnnotationWithType<IgnoreColumn>(),
        index = findAnnotationWithType<Index>(),
        unique = findAnnotationWithType<Unique>()
      )
    }
  }
}

data class PropertyRoundElement(
  val metadata: RoundPropertyMetadata,
  val annotations: PropertyRoundAnnotations,
) : RoundTypeElement by metadata, RoundPropertyShape by metadata

data class TableRoundElement(
  val table: TableElement,
  val originatingFiles: OriginatingFiles,
  val sourceDeclaration: KSClassDeclaration,
  val sourceProperties: Map<PropertyPath, KSPropertyDeclaration>
)

internal fun KSPropertyDeclaration.toPropertySourceKey() = PropertySourceKey(
  parentName = parentDeclaration?.qualifiedNameOrSimpleName().orEmpty(),
  propertyName = simpleName.asString()
)
