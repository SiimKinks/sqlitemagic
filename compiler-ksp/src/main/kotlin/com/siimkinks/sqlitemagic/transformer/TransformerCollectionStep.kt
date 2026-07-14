package com.siimkinks.sqlitemagic.transformer

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.Types.DATABASE_ANNOTATION
import com.siimkinks.sqlitemagic.Types.DB_VALUE_TO_OBJECT_ANNOTATION
import com.siimkinks.sqlitemagic.Types.OBJECT_TO_DB_VALUE_ANNOTATION
import com.siimkinks.sqlitemagic.Types.SUBMODULE_DATABASE_ANNOTATION
import com.siimkinks.sqlitemagic.annotation.Database
import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase
import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Failed
import com.siimkinks.sqlitemagic.utils.classArrayArgumentValue
import com.siimkinks.sqlitemagic.utils.firstUncheckedAnnotation
import com.siimkinks.sqlitemagic.utils.getLocalAndInheritedFunctions
import com.siimkinks.sqlitemagic.utils.isUncheckedAnnotationPresent

class TransformerCollectionStep(
  private val environment: Environment
) : ProcessingStep {
  override fun process(resolver: Resolver): ProcessingStepResult {
    val databaseConfigurationSymbol = resolver.getDatabaseConfigurationSymbol()
    val collector = TransformerCollector(environment)
    collector.collect(
      objectToDbValueElements = resolver.getTransformerFunctions(OBJECT_TO_DB_VALUE_ANNOTATION),
      dbValueToObjectElements = resolver.getTransformerFunctions(DB_VALUE_TO_OBJECT_ANNOTATION)
    )

    val externalTransformerMethods = databaseConfigurationSymbol
      ?.getExternalTransformerDeclarations()
      .orEmpty()
      .flatMap(KSClassDeclaration::getLocalAndInheritedFunctions)
      .toSet()
    collector.collect(
      objectToDbValueElements = externalTransformerMethods.filter {
        it.isUncheckedAnnotationPresent<ObjectToDbValue>()
      },
      dbValueToObjectElements = externalTransformerMethods.filter {
        it.isUncheckedAnnotationPresent<DbValueToObject>()
      },
      additionalOriginatingFiles = setOfNotNull(databaseConfigurationSymbol?.containingFile)
    )
    return when {
      collector.addToEnvironment() -> Continue
      else -> Failed
    }
  }

  private fun Resolver.getTransformerFunctions(annotationName: String) =
    getSymbolsWithAnnotation(annotationName)
      .filterIsInstance<KSFunctionDeclaration>()
      .toList()

  private fun KSDeclaration.getExternalTransformerDeclarations() =
    when {
      environment.isSubmodule -> firstUncheckedAnnotation<SubmoduleDatabase>()
        ?.classArrayArgumentValue(SubmoduleDatabase::externalTransformers)
      else -> firstUncheckedAnnotation<Database>()
        ?.classArrayArgumentValue(Database::externalTransformers)
    }
      ?.map(KSType::declaration)
      ?.filterIsInstance<KSClassDeclaration>()
      ?: emptyList()

  private fun Resolver.getDatabaseConfigurationSymbol() =
    getSymbolsWithAnnotation(
      annotationName = when {
        environment.isSubmodule -> SUBMODULE_DATABASE_ANNOTATION
        else -> DATABASE_ANNOTATION
      }
    )
      .filterIsInstance<KSDeclaration>()
      .firstOrNull()
}
