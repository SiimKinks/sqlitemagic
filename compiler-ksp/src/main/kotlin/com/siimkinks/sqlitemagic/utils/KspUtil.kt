package com.siimkinks.sqlitemagic.utils

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.Visibility.JAVA_PACKAGE
import com.google.devtools.ksp.symbol.Visibility.PRIVATE
import kotlin.reflect.KCallable

/** Returns the first annotation of type [T], or `null`. */
inline fun <reified T : Annotation> KSAnnotated.findAnnotationWithType(): T? =
  getAnnotationsByType(T::class)
    .firstOrNull()

/** Returns whether an annotation with [T]'s short name is present without resolving its values. */
inline fun <reified T : Annotation> KSAnnotated.isUncheckedAnnotationPresent(): Boolean {
  val annotationShortName = checkNotNull(T::class.simpleName)
  return annotations.any {
    it.shortName.asString() == annotationShortName
  }
}

/** Returns the first annotation with [T]'s short name without resolving its values. */
inline fun <reified T : Annotation> KSAnnotated.firstUncheckedAnnotation(): KSAnnotation? {
  val annotationShortName = checkNotNull(T::class.simpleName)
  return annotations.firstOrNull {
    it.shortName.asString() == annotationShortName
  }
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
  arguments.argumentValue(property.name)
    ?: defaultArguments.argumentValue(property.name)

/**
 * Returns the type list named [propertyName].
 *
 * Example: `[name = "id", types = [String]] -> [String]` for `propertyName = "types"`.
 */
@Suppress("UNCHECKED_CAST")
private fun List<KSValueArgument>.argumentValue(
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
          when (function.getVisibility()) {
            PRIVATE -> false
            JAVA_PACKAGE -> function.packageName.asString() == targetPackageName
            else -> true
          }
        }
    )
  }
  return collectFunctions()
}
