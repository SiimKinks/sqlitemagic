package com.siimkinks.sqlitemagic.utils

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.symbol.ClassKind.INTERFACE
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSBackingField
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
import com.google.devtools.ksp.symbol.Visibility.PUBLIC
import com.google.devtools.ksp.validate
import com.siimkinks.sqlitemagic.annotation.Embedded
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import kotlin.reflect.KCallable

internal inline fun <reified T : Annotation> KSAnnotated.findAnnotationWithType() =
  getAnnotationsByType(T::class)
    .firstOrNull()

internal inline fun <reified T : Annotation> KSAnnotated.firstUncheckedAnnotation(): KSAnnotation? {
  val annotationShortName = checkNotNull(T::class.simpleName)
  return annotations.firstOrNull {
    it.shortName.asString() == annotationShortName
  }
}

internal inline fun <reified T : Annotation> KSAnnotated.isUncheckedAnnotationPresent(): Boolean {
  val annotationShortName = checkNotNull(T::class.simpleName)
  return annotations.any {
    it.shortName.asString() == annotationShortName
  }
}

internal fun KSAnnotation.getArgument(
  property: KCallable<*>
) = arguments.first {
  it.name?.asString() == property.name
}

internal fun KSAnnotation.classArrayArgumentValue(
  property: KCallable<*>
) = arguments.arrayArgumentValue(property.name)
  ?: defaultArguments.arrayArgumentValue(property.name)

private fun List<KSValueArgument>.arrayArgumentValue(
  propertyName: String
) = (firstOrNull { it.name?.asString() == propertyName }?.value as? List<*>)
  ?.filterIsInstance<KSType>()

internal fun KSDeclaration.qualifiedNameOrSimpleName() =
  qualifiedName?.asString()
    ?: simpleName.asString()

internal fun KSClassDeclaration.displayName() =
  declarationPathNames()
    .joinToString(separator = ".")

internal fun KSDeclaration.resolveClassDeclaration(): KSClassDeclaration? = when (this) {
  is KSClassDeclaration -> this
  is KSTypeAlias -> type.resolve().declaration.resolveClassDeclaration()
  else -> null
}

internal fun KSClassDeclaration.modelConstructor() =
  primaryConstructor ?: getConstructors().singleOrNull()

/** Validates construction and mapping inputs without traversing unrelated direct helper members. */
internal fun KSClassDeclaration.validateModelMembers(
  annotations: ConsumedAnnotations,
  isRequiredProperty: (KSPropertyDeclaration) -> Boolean
) = when {
  hasUnresolvedModelAnnotationArguments(annotations) -> false
  // These collectors traverse additional declaration shapes. Preserve their existing readiness boundary.
  superTypes.any {
    it.resolve()
      .declaration.qualifiedName?.asString() != Any::class.qualifiedName
  } -> validateConsumedAnnotations()
  declarations
    .filterIsInstance<KSPropertyDeclaration>()
    .any { annotations.has<Embedded>(it) } -> validateConsumedAnnotations()
  else -> validate(
    predicate = { _, node ->
      val member = node as? KSDeclaration
      val owner = member?.parentDeclaration as? KSClassDeclaration
      val isDirectMember = owner == this || owner?.isCompanionObject == true && owner.parentDeclaration == this
      when {
        node is KSAnnotation -> node.isConsumedAnnotation()
        member == null -> true
        !isDirectMember -> true
        // Retain annotated declarations, including invalid annotation uses, for supported diagnostics.
        annotations.hasAny(member) -> true
        member is KSFunctionDeclaration -> member.isConstructor()
        member is KSPropertyDeclaration -> isRequiredProperty(member)
        else -> true
      }
    },
    enableNewFeatures = true
  )
}

/** Follow only annotation metadata from inherited and embedded model shapes consumed by collection. */
private fun KSClassDeclaration.hasUnresolvedModelAnnotationArguments(annotations: ConsumedAnnotations): Boolean {
  val visited = mutableSetOf<Pair<KSClassDeclaration, Boolean>>()

  fun inspect(
    declaration: KSClassDeclaration,
    includeClassArguments: Boolean
  ): Boolean = when {
    !visited.add(declaration to includeClassArguments) -> false
    includeClassArguments && annotations.hasUnresolvedArguments(declaration) -> true
    declaration.declarations.any { member ->
      when (member) {
        is KSClassDeclaration -> includeClassArguments && inspect(
          declaration = member,
          includeClassArguments = true
        )
        else -> annotations.hasUnresolvedArguments(member) || when {
          member is KSPropertyDeclaration && annotations.has<Embedded>(member) ->
            member.type
              .resolve()
              .declaration
              .resolveClassDeclaration()
              ?.let { target ->
                inspect(
                  declaration = target,
                  includeClassArguments = false
                )
              } == true
          else -> false
        }
      }
    } -> true
    else -> declaration
      .directColumnAncestors()
      .filterNot { it.qualifiedName?.asString() == Any::class.qualifiedName }
      .any {
        inspect(
          declaration = it,
          includeClassArguments = false
        )
      }
  }

  return inspect(
    declaration = this,
    includeClassArguments = true
  )
}

/** Generated code lives in the root package, so it reaches only public and module-internal declarations. */
internal fun KSDeclaration.isAccessibleFromGeneratedCode() = when (getVisibility()) {
  PUBLIC,
  INTERNAL -> true
  else -> false
}

internal fun KSDeclaration.declarationPath() = generateSequence(
  seed = this,
  nextFunction = KSDeclaration::parentDeclaration
)

internal fun KSDeclaration.isEffectivelyAccessibleFromGeneratedCode() =
  declarationPath().all(KSDeclaration::isAccessibleFromGeneratedCode)

internal fun KSDeclaration.isEffectivelyPublic() =
  declarationPath().all { it.getVisibility() == PUBLIC }

internal fun KSClassDeclaration.declarationPathNames() =
  declarationPath()
    .map { it.simpleName.asString() }
    .toList()
    .asReversed()

internal fun KSAnnotated.normalizePropertySymbol() = when (this) {
  is KSBackingField -> property
  else -> this
}

internal fun KSDeclaration?.typeParameterResolver(): TypeParameterResolver = when (this) {
  is KSClassDeclaration -> typeParameters.toTypeParameterResolver(
    parent = parentDeclaration.typeParameterResolver()
  )
  is KSFunctionDeclaration -> typeParameters.toTypeParameterResolver(
    parent = parentDeclaration.typeParameterResolver()
  )
  else -> TypeParameterResolver.EMPTY
}

internal fun KSClassDeclaration.getLocalAndInheritedFunctions(): Sequence<KSFunctionDeclaration> {
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
        .filter(KSFunctionDeclaration::isAccessibleFromGeneratedCode)
    )
  }
  return collectFunctions()
}

/** Returns inherited fields/properties before locally declared ones. */
internal fun KSClassDeclaration.getLocalAndInheritedProperties(): List<KSPropertyDeclaration> = buildList {
  getLocalAndInheritedColumnTypes()
    .flatMap { declaration ->
      declaration.declarations
        .filterIsInstance<KSPropertyDeclaration>()
        .filter { JAVA_STATIC !in it.modifiers }
    }
    .forEach { property ->
      if (property.findOverridee() != null) {
        val propertyName = property.simpleName.asString()
        removeAll { it.simpleName.asString() == propertyName }
      }
      add(property)
    }
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
  .filterNot {
    it.simpleName
      .asString()
      .startsWith(prefix = "Parcelable")
  }
  .sortedBy { it.classKind != INTERFACE }
