package com.siimkinks.sqlitemagic.dbconfig

import com.google.devtools.ksp.KSTypesNotPresentException
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.NameConst
import com.siimkinks.sqlitemagic.Types
import com.siimkinks.sqlitemagic.annotation.Database
import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.utils.findAnnotationWithType
import com.siimkinks.sqlitemagic.utils.firstCharToUpperCase

class DatabaseConfigurationCollectionStep(
  private val environment: Environment
) : ProcessingStep {
  override fun process(resolver: Resolver): ProcessingStepResult {
    val submoduleDatabaseSymbols = resolver
      .getSymbolsWithAnnotation(Types.SUBMODULE_DATABASE_ANNOTATION)
      .toList()
    if (!parseSubmodules(submoduleDatabaseSymbols)) {
      return ProcessingStepResult.Failed
    }

    val databaseSymbols = resolver
      .getSymbolsWithAnnotation(Types.DATABASE_ANNOTATION)
      .toList()
    if (submoduleDatabaseSymbols.isNotEmpty() && databaseSymbols.isNotEmpty()) {
      environment.logger.error(
        message = "Module can have either $DATABASE_ANNOTATION_NAME or $SUBMODULE_DATABASE_ANNOTATION_NAME annotated element, but not both"
      )
      return ProcessingStepResult.Failed
    }
    if (databaseSymbols.size > 1) {
      environment.logger.error(ERR_SINGLE_DB_ALLOWED)
      return ProcessingStepResult.Failed
    }
    for (symbol in databaseSymbols) {
      if (environment.submoduleDatabases != null) {
        environment.logger.error(
          message = ERR_SINGLE_DB_ALLOWED,
          symbol = symbol
        )
        return ProcessingStepResult.Failed
      }
      val databaseAnnotation = checkNotNull(symbol.findAnnotationWithType<Database>())
      val submodules = resolver
        .getSubmodules(
          databaseAnnotation = databaseAnnotation,
          databaseSymbol = symbol
        )
        ?: return ProcessingStepResult.Failed
      if (submodules.isNotEmpty()) {
        environment.submoduleDatabases = submodules
      }
      environment.setDatabaseMetadata(
        dbName = databaseAnnotation.name,
        dbVersion = databaseAnnotation.version
      )
    }
    return ProcessingStepResult.Continue
  }

  private fun parseSubmodules(submoduleDatabaseSymbols: List<KSAnnotated>): Boolean {
    if (submoduleDatabaseSymbols.size > 1) {
      environment.logger.error(ERR_SINGLE_SUBMODULE_DB_ALLOWED)
      return false
    }
    for (symbol in submoduleDatabaseSymbols) {
      if (environment.submoduleName != null) {
        environment.logger.error(
          message = ERR_SINGLE_SUBMODULE_DB_ALLOWED,
          symbol = symbol
        )
        return false
      }

      val submoduleDatabase = checkNotNull(symbol.findAnnotationWithType<SubmoduleDatabase>())
      val moduleName = submoduleDatabase.value
      if (moduleName.isEmpty()) {
        environment.logger.error(
          message = "Submodule name cannot be empty or null",
          symbol = symbol
        )
        return false
      }
      environment.setSubmoduleName(moduleName)
    }
    return true
  }

  private fun Resolver.getSubmodules(
    databaseAnnotation: Database,
    databaseSymbol: KSAnnotated
  ): List<SubmoduleDatabaseMetadata>? = buildList {
    val submoduleDeclarations = getSubmoduleDeclarations(
      databaseAnnotation = databaseAnnotation,
      databaseSymbol = databaseSymbol
    ) ?: return null
    for (submoduleDeclaration in submoduleDeclarations) {
      add(getSubmoduleMetadata(submoduleDeclaration) ?: return null)
    }
  }

  private fun Resolver.getSubmoduleDeclarations(
    databaseAnnotation: Database,
    databaseSymbol: KSAnnotated
  ): List<KSClassDeclaration>? = try {
    databaseAnnotation.submodules.map { submoduleClass ->
      val submoduleQualifiedName = submoduleClass.qualifiedName
        ?: run {
          environment.logger.error(
            message = "Database submodule ${submoduleClass.simpleName} could not be resolved",
            symbol = databaseSymbol
          )
          return null
        }
      getClassDeclarationByName(submoduleQualifiedName)
        ?: run {
          environment.logger.error(
            message = "Database submodule $submoduleQualifiedName could not be resolved",
            symbol = databaseSymbol
          )
          return null
        }
    }
  } catch (e: KSTypesNotPresentException) {
    e.ksTypes.map { submoduleType ->
      submoduleType.declaration as? KSClassDeclaration
        ?: run {
          environment.logger.error(
            message = "Database submodule must be a class: ${submoduleType.declaration.qualifiedName?.asString()}"
          )
          return null
        }
    }
  }

  private fun getSubmoduleMetadata(
    annotatedDeclaration: KSClassDeclaration
  ): SubmoduleDatabaseMetadata? {
    val submoduleDatabase = annotatedDeclaration.findAnnotationWithType<SubmoduleDatabase>()
    if (submoduleDatabase == null) {
      environment.logger.error(
        message = "Database submodule ${annotatedDeclaration.qualifiedName?.asString()} must be annotated with $SUBMODULE_DATABASE_ANNOTATION_NAME",
        symbol = annotatedDeclaration
      )
      return null
    }

    val moduleName = submoduleDatabase.value
    if (moduleName.isEmpty()) {
      environment.logger.error(
        message = "Submodule name cannot be empty",
        symbol = annotatedDeclaration
      )
      return null
    }

    val capitalizedModuleName = moduleName.firstCharToUpperCase()
    val managerClassName = environment.getGenClassesManagerClassName(capitalizedModuleName)
    return SubmoduleDatabaseMetadata(
      managerQualifiedName = "${NameConst.PACKAGE_ROOT}.$managerClassName",
      moduleName = capitalizedModuleName
    )
  }

  private companion object {
    val DATABASE_ANNOTATION_NAME = "@${Database::class.simpleName}"
    val SUBMODULE_DATABASE_ANNOTATION_NAME = "@${SubmoduleDatabase::class.simpleName}"
    val ERR_SINGLE_SUBMODULE_DB_ALLOWED =
      "Only one element per module can be annotated with $SUBMODULE_DATABASE_ANNOTATION_NAME"
    val ERR_SINGLE_DB_ALLOWED = "Only one element per module can be annotated with $DATABASE_ANNOTATION_NAME"
  }
}