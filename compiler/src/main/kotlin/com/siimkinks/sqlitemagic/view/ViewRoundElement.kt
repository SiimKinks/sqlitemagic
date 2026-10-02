package com.siimkinks.sqlitemagic.view

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
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
import com.siimkinks.sqlitemagic.model.RoundPropertyMetadata
import com.siimkinks.sqlitemagic.model.RoundPropertyShape
import com.siimkinks.sqlitemagic.utils.ConsumedAnnotations
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
    internal fun from(
      symbol: KSAnnotated,
      annotations: ConsumedAnnotations
    ) = with(annotations) {
      ViewPropertyRoundAnnotations(
        viewColumn = viewColumn(symbol),
        embedded = embedded(symbol),
        ignoreColumn = ignoreColumn(symbol),
        column = column(symbol),
        id = raw(
          symbol = symbol,
          annotationClass = Id::class
        ),
        index = index(symbol),
        unique = unique(symbol)
      )
    }
  }
}

internal data class ViewPropertyRoundElement(
  val metadata: RoundPropertyMetadata,
  val annotations: ViewPropertyRoundAnnotations,
) : RoundTypeElement by metadata, RoundPropertyShape by metadata

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
