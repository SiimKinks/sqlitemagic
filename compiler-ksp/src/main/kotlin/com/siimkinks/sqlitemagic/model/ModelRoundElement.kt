package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSDeclaration
import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Embedded
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
import com.siimkinks.sqlitemagic.annotation.Index
import com.siimkinks.sqlitemagic.annotation.Unique
import com.siimkinks.sqlitemagic.element.RoundTypeElement
import com.siimkinks.sqlitemagic.utils.findAnnotationWithType
import com.siimkinks.sqlitemagic.utils.firstUncheckedAnnotation
import com.siimkinks.sqlitemagic.writer.OriginatingFiles

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
  val sourceDeclaration: KSDeclaration,
  val roundTypeElement: RoundTypeElement,
  val isConstructorProperty: Boolean,
  val annotations: PropertyRoundAnnotations,
  val isInherited: Boolean,
  val isMutable: Boolean,
  val isReadable: Boolean,
  val isWritable: Boolean
) : RoundTypeElement by roundTypeElement {
  val name get() = sourceDeclaration.simpleName.asString()
}

data class TableRoundElement(
  val tableElement: TableElement,
  val originatingFiles: OriginatingFiles
)
