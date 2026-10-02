package com.siimkinks.sqlitemagic.utils

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.validate
import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Embedded
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
import com.siimkinks.sqlitemagic.annotation.Index
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.TableOption
import com.siimkinks.sqlitemagic.annotation.Unique
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewOption
import com.siimkinks.sqlitemagic.annotation.ViewQuery
import kotlin.reflect.KCallable
import kotlin.reflect.KClass

/** One collection pass owns this cache */
internal class ConsumedAnnotations {
  private val declarations = mutableMapOf<KSAnnotated, Map<String, KSAnnotation>>()
  private val decoded = mutableMapOf<KSAnnotation, Annotation>()

  inline fun <reified T : Annotation> has(symbol: KSAnnotated) = raw(
    symbol = symbol,
    annotationClass = T::class
  ) != null

  fun raw(
    symbol: KSAnnotated,
    annotationClass: KClass<out Annotation>
  ) = scan(symbol)[annotationClass.simpleName]

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
    .any(KSAnnotation::hasUnresolvedArguments)

  fun table(symbol: KSAnnotated) = raw(
    symbol = symbol,
    annotationClass = Table::class
  )?.decode {
    Table(
      value = argument(Table::value),
      persistAll = argument(Table::persistAll),
      options = enumArguments<TableOption>(Table::options)
        .toTypedArray()
    )
  }

  fun view(symbol: KSAnnotated) = raw(
    symbol = symbol,
    annotationClass = View::class
  )?.decode {
    View(
      value = argument(View::value),
      options = enumArguments<ViewOption>(View::options)
        .toTypedArray()
    )
  }

  fun column(symbol: KSAnnotated) = raw(
    symbol = symbol,
    annotationClass = Column::class
  )?.decode {
    Column(
      value = argument(Column::value),
      defaultValue = argument(Column::defaultValue),
      handleRecursively = argument(Column::handleRecursively),
      onDeleteCascade = argument(Column::onDeleteCascade),
      belongsToIndex = argument(Column::belongsToIndex)
    )
  }

  fun embedded(symbol: KSAnnotated) = raw(
    symbol = symbol,
    annotationClass = Embedded::class
  )?.decode {
    Embedded(prefix = argument(Embedded::prefix))
  }

  fun index(symbol: KSAnnotated) = raw(
    symbol = symbol,
    annotationClass = Index::class
  )?.decode {
    Index(
      value = argument(Index::value),
      unique = argument(Index::unique)
    )
  }

  fun viewColumn(symbol: KSAnnotated) = raw(
    symbol = symbol,
    annotationClass = ViewColumn::class
  )?.decode {
    ViewColumn(value = argument(ViewColumn::value))
  }

  fun ignoreColumn(symbol: KSAnnotated) = raw(
    symbol = symbol,
    annotationClass = IgnoreColumn::class
  )?.decode { IgnoreColumn() }

  fun unique(symbol: KSAnnotated) = raw(
    symbol = symbol,
    annotationClass = Unique::class
  )?.decode { Unique() }

  private fun scan(symbol: KSAnnotated) = declarations.getOrPut(symbol) {
    buildMap {
      symbol.annotations.forEach { annotation ->
        val name = annotation.shortName.asString()
        if (name in CONSUMED_ANNOTATION_NAMES) {
          getOrPut(name) { annotation }
        }
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T : Annotation> KSAnnotation.decode(create: KSAnnotation.() -> T) =
    decoded.getOrPut(this) { create() } as T
}

internal val CONSUMED_ANNOTATION_NAMES = setOf(
  checkNotNull(Table::class.simpleName),
  checkNotNull(View::class.simpleName),
  checkNotNull(Column::class.simpleName),
  checkNotNull(Embedded::class.simpleName),
  checkNotNull(Id::class.simpleName),
  checkNotNull(IgnoreColumn::class.simpleName),
  checkNotNull(Index::class.simpleName),
  checkNotNull(Unique::class.simpleName),
  checkNotNull(ViewColumn::class.simpleName),
  checkNotNull(ViewQuery::class.simpleName)
)

internal fun KSAnnotation.isConsumedAnnotation() = shortName.asString() in CONSUMED_ANNOTATION_NAMES

@Suppress("UNCHECKED_CAST")
private fun <T> KSAnnotation.argument(property: KCallable<*>) = checkNotNull(getArgument(property).value) as T

private inline fun <reified T : Enum<T>> KSAnnotation.enumArguments(
  property: KCallable<*>
) = argument<List<*>>(property)
  .map { value ->
    enumValueOf<T>(
      checkNotNull(value)
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

internal fun KSAnnotated.validateConsumedAnnotations() = validate { _, node ->
  node !is KSAnnotation || node.isConsumedAnnotation()
}
