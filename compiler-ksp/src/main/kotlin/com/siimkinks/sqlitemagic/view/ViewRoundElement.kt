package com.siimkinks.sqlitemagic.view

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
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.element.RoundTypeElement
import com.siimkinks.sqlitemagic.model.PropertyPath
import com.siimkinks.sqlitemagic.utils.findAnnotationWithType
import com.siimkinks.sqlitemagic.utils.firstUncheckedAnnotation
import com.siimkinks.sqlitemagic.writer.OriginatingFiles

data class ViewPropertyRoundAnnotations(
  val viewColumn: ViewColumn?,
  val embedded: Embedded?,
  val ignoreColumn: IgnoreColumn?,
  val column: Column?,
  val id: KSAnnotation?,
  val index: Index?,
  val unique: Unique?
) {
  companion object {
    fun from(symbol: KSAnnotated) = with(symbol) {
      ViewPropertyRoundAnnotations(
        viewColumn = findAnnotationWithType<ViewColumn>(),
        embedded = findAnnotationWithType<Embedded>(),
        ignoreColumn = findAnnotationWithType<IgnoreColumn>(),
        column = findAnnotationWithType<Column>(),
        id = firstUncheckedAnnotation<Id>(),
        index = findAnnotationWithType<Index>(),
        unique = findAnnotationWithType<Unique>()
      )
    }
  }
}

data class ViewPropertyRoundElement(
  val sourceDeclaration: KSDeclaration,
  val roundTypeElement: RoundTypeElement,
  val isConstructorProperty: Boolean,
  val annotations: ViewPropertyRoundAnnotations,
  val isInherited: Boolean,
  val isMutable: Boolean,
  val isReadable: Boolean,
  val isWritable: Boolean
) : RoundTypeElement by roundTypeElement {
  val name get() = sourceDeclaration.simpleName.asString()
}

data class ViewQueryRoundElement(
  val sourceDeclaration: KSPropertyDeclaration,
  val roundTypeElement: RoundTypeElement
) : RoundTypeElement by roundTypeElement {
  val name get() = sourceDeclaration.simpleName.asString()
}

data class ViewRoundElement(
  val view: ViewElement,
  val originatingFiles: OriginatingFiles,
  val sourceDeclaration: KSClassDeclaration,
  val sourceProperties: Map<PropertyPath, KSPropertyDeclaration>,
  val query: ViewQueryRoundElement
)
