package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.siimkinks.sqlitemagic.element.RoundTypeElement

data class PropertyRoundAnnotations(
  val column: KSAnnotation?,
  val embedded: KSAnnotation?,
  val id: KSAnnotation?,
  val ignoreColumn: KSAnnotation?,
  val index: KSAnnotation?,
  val unique: KSAnnotation?
)

data class PropertyRoundElement(
  val propertyDeclaration: KSPropertyDeclaration,
  val roundTypeElement: RoundTypeElement,
  val constructorParameter: KSValueParameter?,
  val annotations: PropertyRoundAnnotations,
  val isInherited: Boolean,
  val hasBackingField: Boolean
) : RoundTypeElement by roundTypeElement

data class TableRoundElement(
  val classDeclaration: KSClassDeclaration,
  val roundTypeElement: RoundTypeElement,
  val tableAnnotation: KSAnnotation,
  val primaryConstructor: KSFunctionDeclaration?,
  val properties: List<PropertyRoundElement>,
  val originatingFiles: Set<KSFile>
) : RoundTypeElement by roundTypeElement
