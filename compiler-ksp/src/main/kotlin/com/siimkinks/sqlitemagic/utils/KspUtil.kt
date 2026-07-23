package com.siimkinks.sqlitemagic.utils

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.ClassKind.INTERFACE
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.Modifier.JAVA_STATIC
import com.google.devtools.ksp.symbol.Visibility.INTERNAL
import com.google.devtools.ksp.symbol.Visibility.JAVA_PACKAGE
import com.google.devtools.ksp.symbol.Visibility.PRIVATE
import com.google.devtools.ksp.symbol.Visibility.PUBLIC
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import kotlin.reflect.KCallable

/** Returns the first annotation of type [T], or `null`. */
inline fun <reified T : Annotation> KSAnnotated.findAnnotationWithType(): T? =
  getAnnotationsByType(T::class)
    .firstOrNull()

/** Returns the first annotation with [T]'s short name without resolving it. */
inline fun <reified T : Annotation> KSAnnotated.firstUncheckedAnnotation(): KSAnnotation? {
  val annotationShortName = checkNotNull(T::class.simpleName)
  return annotations.firstOrNull {
    it.shortName.asString() == annotationShortName
  }
}

/** Returns whether an annotation with [T]'s short name is present without resolving it. */
inline fun <reified T : Annotation> KSAnnotated.isUncheckedAnnotationPresent(): Boolean {
  val annotationShortName = checkNotNull(T::class.simpleName)
  return annotations.any {
    it.shortName.asString() == annotationShortName
  }
}

fun KSAnnotated.hasAnyAnnotationWithSimpleName(names: Set<String>): Boolean = annotations.any { annotation ->
  annotation.shortName.asString() in names
}

fun KSAnnotation.getArgument(
  property: KCallable<*>
): KSValueArgument = arguments.first {
  it.name?.asString() == property.name
}

/**
 * Returns an array-valued class argument from an annotation.
 *
 * For example:
 * Java: `Class<?>[] externalTransformers() default {}`
 * Kotlin: `val externalTransformers: Array<KClass<*>> = []`
 */
fun KSAnnotation.classArrayArgumentValue(
  property: KCallable<*>
): List<KSType>? =
  arguments.arrayArgumentValue(property.name)
    ?: defaultArguments.arrayArgumentValue(property.name)

/**
 * Returns the type list named [propertyName].
 *
 * Example: `[name = "id", types = [String]] -> [String]` for `propertyName = "types"`.
 */
@Suppress("UNCHECKED_CAST")
private fun List<KSValueArgument>.arrayArgumentValue(
  propertyName: String
): List<KSType>? =
  firstOrNull { it.name?.asString() == propertyName }
    ?.value as? List<KSType>

/**
 * Returns the qualified name when available.
 *
 * Example: `User in com.example -> "com.example.User"`.
 */
fun KSDeclaration.qualifiedNameOrSimpleName(): String =
  qualifiedName?.asString()
    ?: simpleName.asString()

fun KSClassDeclaration.displayName() =
  declarationPathNames()
    .joinToString(separator = ".")

/**
 * Resolves a class or type alias to its class declaration.
 *
 * Example: `typealias UserId = String -> String declaration`.
 */
fun KSDeclaration.resolveClassDeclaration(): KSClassDeclaration? = when (this) {
  is KSClassDeclaration -> this
  is KSTypeAlias -> type.resolve().declaration.resolveClassDeclaration()
  else -> null
}

fun KSClassDeclaration.modelConstructor(): KSFunctionDeclaration? =
  primaryConstructor ?: getConstructors().singleOrNull()

fun KSDeclaration.isAccessibleFromGeneratedCode() = when (getVisibility()) {
  PUBLIC,
  INTERNAL,
  JAVA_PACKAGE -> true
  else -> false
}

fun KSDeclaration.isEffectivelyAccessibleFromGeneratedCode() =
  generateSequence(
    seed = this as KSDeclaration?,
    nextFunction = KSDeclaration::parentDeclaration
  ).all(KSDeclaration::isAccessibleFromGeneratedCode)

fun KSClassDeclaration.declarationPathNames() =
  generateSequence(this as KSDeclaration?) { declaration ->
    declaration.parentDeclaration as? KSClassDeclaration
  }
    .filterIsInstance<KSClassDeclaration>()
    .map { it.simpleName.asString() }
    .toList()
    .asReversed()

fun KSDeclaration?.typeParameterResolver(): TypeParameterResolver = when (this) {
  is KSClassDeclaration -> typeParameters.toTypeParameterResolver(
    parent = parentDeclaration.typeParameterResolver()
  )
  is KSFunctionDeclaration -> typeParameters.toTypeParameterResolver(
    parent = parentDeclaration.typeParameterResolver()
  )
  else -> TypeParameterResolver.EMPTY
}

fun KSClassDeclaration.getLocalAndInheritedFunctions(): Sequence<KSFunctionDeclaration> {
  val targetPackageName = packageName.asString()
  val visitedTypes = mutableSetOf<String>()

  fun KSClassDeclaration.collectFunctions(): Sequence<KSFunctionDeclaration> = sequence {
    val typeName = qualifiedNameOrSimpleName()
    if (!visitedTypes.add(typeName)) {
      return@sequence
    }
    for (superType in superTypes) {
      val superClass = superType
        .resolve()
        .declaration as? KSClassDeclaration
        ?: continue
      yieldAll(superClass.collectFunctions())
    }
    yieldAll(
      declarations
        .filterIsInstance<KSFunctionDeclaration>()
        .filter { function ->
          function.isVisibleFromPackage(
            targetPackageName = targetPackageName
          )
        }
    )
  }
  return collectFunctions()
}

/** Returns inherited fields/properties before locally declared ones. */
fun KSClassDeclaration.getLocalAndInheritedProperties(): List<KSPropertyDeclaration> {
  val properties = getLocalAndInheritedColumnTypes()
    .flatMap { declaration ->
      declaration.declarations
        .filterIsInstance<KSPropertyDeclaration>()
        .filter { JAVA_STATIC !in it.modifiers }
        .toList()
    }
  val visibleProperties = mutableListOf<KSPropertyDeclaration>()
  for (property in properties) {
    if (property.findOverridee() != null) {
      val propertyName = property.simpleName.asString()
      visibleProperties.removeAll { candidate ->
        candidate.simpleName.asString() == propertyName
      }
    }
    visibleProperties.add(property)
  }
  return visibleProperties
}

/**
 * Returns visible inherited methods before locally declared ones and removes overridden ancestors.
 */
fun KSClassDeclaration.getLocalAndInheritedColumnFunctions(): List<KSFunctionDeclaration> {
  val targetPackageName = packageName.asString()
  val methods = getLocalAndInheritedColumnTypes()
    .flatMap { declaration ->
      declaration.declarations
        .filterIsInstance<KSFunctionDeclaration>()
        .filter { JAVA_STATIC !in it.modifiers }
        .filter { function ->
          function.isVisibleFromPackage(
            targetPackageName = targetPackageName
          )
        }
        .toList()
    }
  val overriddenMethods = methods
    .mapNotNull(KSFunctionDeclaration::findOverridee)
    .toSet()
  return methods.filterNot(overriddenMethods::contains)
}

private fun KSDeclaration.isVisibleFromPackage(
  targetPackageName: String
) = when (getVisibility()) {
  PRIVATE -> false
  JAVA_PACKAGE -> packageName.asString() == targetPackageName
  else -> true
}

private fun KSClassDeclaration.getLocalAndInheritedColumnTypes(): List<KSClassDeclaration> {
  val visitedTypes = mutableSetOf<String>()
  val types = mutableListOf<KSClassDeclaration>()

  fun collect(type: KSClassDeclaration) {
    val typeName = type.qualifiedNameOrSimpleName()
    if (!visitedTypes.add(typeName)) return
    type
      .directColumnAncestors()
      .forEach(::collect)
    types.add(type)
  }

  collect(this)
  return types
}

private fun KSClassDeclaration.directColumnAncestors() = superTypes
  .map(KSTypeReference::resolve)
  .map(KSType::declaration)
  .mapNotNull(KSDeclaration::resolveClassDeclaration)
  .filterNot { declaration ->
    declaration.simpleName
      .asString()
      .startsWith(prefix = "Parcelable")
  }
  .sortedBy { declaration ->
    when (declaration.classKind) {
      INTERFACE -> 0
      else -> 1
    }
  }
