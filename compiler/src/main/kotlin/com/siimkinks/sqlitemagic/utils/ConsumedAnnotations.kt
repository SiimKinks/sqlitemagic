package com.siimkinks.sqlitemagic.utils

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Origin.JAVA
import com.google.devtools.ksp.symbol.Origin.JAVA_LIB
import com.google.devtools.ksp.validate
import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Embedded
import com.siimkinks.sqlitemagic.annotation.FullText
import com.siimkinks.sqlitemagic.annotation.FullTextDetail
import com.siimkinks.sqlitemagic.annotation.FullTextIndex
import com.siimkinks.sqlitemagic.annotation.FullTextMatchinfo
import com.siimkinks.sqlitemagic.annotation.FullTextOrder
import com.siimkinks.sqlitemagic.annotation.FullTextRole
import com.siimkinks.sqlitemagic.annotation.FullTextVersion
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
import com.siimkinks.sqlitemagic.annotation.Index
import com.siimkinks.sqlitemagic.annotation.RowId
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.TableOption
import com.siimkinks.sqlitemagic.annotation.Unique
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewOption
import com.siimkinks.sqlitemagic.annotation.ViewQuery
import kotlin.reflect.KCallable
import kotlin.reflect.KClass

/** One collection pass owns this cache. */
internal class ConsumedAnnotations {
  private val declarations = mutableMapOf<KSAnnotated, Map<String, List<KSAnnotation>>>()
  private val decoded = mutableMapOf<KSAnnotation, Annotation>()

  inline fun <reified T : Annotation> has(symbol: KSAnnotated) = raw(
    symbol = symbol,
    annotationClass = T::class
  ) != null

  fun raw(
    symbol: KSAnnotated,
    annotationClass: KClass<out Annotation>
  ) = scan(symbol)[annotationClass.simpleName]?.first()

  fun hasAny(symbol: KSAnnotated) = scan(symbol)
    .isNotEmpty()

  fun hasAny(
    symbol: KSAnnotated,
    names: Set<String>
  ) = scan(symbol)
    .keys
    .any(names::contains)

  fun hasUnresolvedArguments(symbol: KSAnnotated) = scan(symbol)
    .values
    .any { annotations -> annotations.any(KSAnnotation::hasUnresolvedArguments) }

  fun table(symbol: KSAnnotated) = decode(symbol) {
    Table(
      value = argument(Table::value),
      persistAll = argument(Table::persistAll),
      options = enumArguments<TableOption>(Table::options)
        .toTypedArray()
    )
  }

  fun view(symbol: KSAnnotated) = decode(symbol) {
    View(
      value = argument(View::value),
      options = enumArguments<ViewOption>(View::options)
        .toTypedArray()
    )
  }

  fun column(symbol: KSAnnotated) = decode(symbol) {
    Column(
      value = argument(Column::value),
      defaultValue = argument(Column::defaultValue),
      handleRecursively = argument(Column::handleRecursively),
      onDeleteCascade = argument(Column::onDeleteCascade),
      belongsToIndex = argument(Column::belongsToIndex)
    )
  }

  fun embedded(symbol: KSAnnotated) = decode(symbol) {
    Embedded(prefix = argument(Embedded::prefix))
  }

  fun index(symbol: KSAnnotated) = decode(symbol) {
    Index(
      value = argument(Index::value),
      unique = argument(Index::unique)
    )
  }

  fun viewColumn(symbol: KSAnnotated) = decode(symbol) {
    ViewColumn(value = argument(ViewColumn::value))
  }

  fun ignoreColumn(symbol: KSAnnotated) = decode(symbol) { IgnoreColumn() }

  fun unique(symbol: KSAnnotated) = decode(symbol) { Unique() }

  fun rowId(symbol: KSAnnotated) = decode(symbol) { RowId() }

  fun fullTextIndexes(symbol: KSAnnotated) = decodeAll(symbol) {
    FullTextIndex(
      value = argument(FullTextIndex::value),
      version = enumArgument<FullTextVersion>(FullTextIndex::version),
      tokenize = argument(FullTextIndex::tokenize),
      prefix = argument<List<*>>(FullTextIndex::prefix)
        .map { checkNotNull(it) as Int }
        .toIntArray(),
      detail = enumArgument<FullTextDetail>(FullTextIndex::detail),
      columnSize = argument(FullTextIndex::columnSize),
      matchinfo = enumArgument<FullTextMatchinfo>(FullTextIndex::matchinfo),
      order = enumArgument<FullTextOrder>(FullTextIndex::order)
    )
  }

  fun fullTexts(symbol: KSAnnotated) = decodeAll(symbol) {
    FullText(
      index = argument(FullText::index),
      role = enumArgument<FullTextRole>(FullText::role)
    )
  }

  private fun scan(symbol: KSAnnotated) = declarations.getOrPut(symbol) {
    buildMap<String, MutableList<KSAnnotation>> {
      val property = symbol.normalizePropertySymbol()
      val backingFieldAnnotations = (property as? KSPropertyDeclaration)
        ?.takeUnless { it.origin == JAVA || it.origin == JAVA_LIB }
        ?.backingField
        ?.annotations
        .orEmpty()
      (property.annotations + backingFieldAnnotations).forEach { annotation ->
        val name = annotation.shortName.asString()
        if (name in CONSUMED_ANNOTATION_NAMES) {
          getOrPut(
            key = name,
            defaultValue = ::mutableListOf
          ) += annotation
        }
      }
    }
  }

  private inline fun <reified T : Annotation> decode(
    symbol: KSAnnotated,
    create: KSAnnotation.() -> T
  ) = raw(
    symbol = symbol,
    annotationClass = T::class
  )?.let { annotation ->
    decoded.getOrPut(annotation) { annotation.create() } as T
  }

  private inline fun <reified T : Annotation> decodeAll(
    symbol: KSAnnotated,
    create: KSAnnotation.() -> T
  ) = scan(symbol)[T::class.simpleName]
    ?.map { annotation -> decoded.getOrPut(annotation) { annotation.create() } as T }
    .orEmpty()
}

internal val CONSUMED_ANNOTATION_NAMES = listOf(
  Table::class,
  View::class,
  Column::class,
  Embedded::class,
  FullText::class,
  FullTextIndex::class,
  Id::class,
  IgnoreColumn::class,
  Index::class,
  RowId::class,
  Unique::class,
  ViewColumn::class,
  ViewQuery::class
)
  .map { checkNotNull(it.simpleName) }
  .toSet()

internal fun KSAnnotation.isConsumedAnnotation() = shortName.asString() in CONSUMED_ANNOTATION_NAMES

private inline fun <reified T> KSAnnotation.argument(property: KCallable<*>) =
  checkNotNull(getArgument(property).value) as T

private inline fun <reified T : Enum<T>> KSAnnotation.enumArgument(property: KCallable<*>) = enumValueOf<T>(
  argument<Any>(property).annotationEnumName()
)

private inline fun <reified T : Enum<T>> KSAnnotation.enumArguments(
  property: KCallable<*>
) = argument<List<*>>(property)
  .map {
    enumValueOf<T>(
      checkNotNull(it)
        .annotationEnumName()
    )
  }

private fun Any.annotationEnumName() = when (this) {
  is KSType -> declaration.simpleName.asString()
  is KSClassDeclaration -> simpleName.asString()
  else -> toString()
}

internal fun KSAnnotation.hasUnresolvedArguments() = arguments.any { it.value.isUnresolvedAnnotationValue() }

private fun Any?.isUnresolvedAnnotationValue(): Boolean = when (this) {
  null -> true
  is KSType -> isError
  is List<*> -> any(Any?::isUnresolvedAnnotationValue)
  is KSAnnotation -> hasUnresolvedArguments()
  else -> false
}

internal fun KSAnnotated.validateConsumedAnnotations() = validate(
  predicate = { _, node ->
    node !is KSAnnotation || node.isConsumedAnnotation()
  },
  enableNewFeatures = true
)
