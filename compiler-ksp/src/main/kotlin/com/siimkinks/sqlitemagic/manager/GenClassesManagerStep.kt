package com.siimkinks.sqlitemagic.manager

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.siimkinks.sqlitemagic.AnnotationNames.DATABASE_ANNOTATION
import com.siimkinks.sqlitemagic.AnnotationNames.DB_VALUE_TO_OBJECT_ANNOTATION
import com.siimkinks.sqlitemagic.AnnotationNames.OBJECT_TO_DB_VALUE_ANNOTATION
import com.siimkinks.sqlitemagic.AnnotationNames.SUBMODULE_DATABASE_ANNOTATION
import com.siimkinks.sqlitemagic.AnnotationNames.TABLE_ANNOTATION
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Failed
import java.io.IOException

class GenClassesManagerStep(
  private val environment: Environment
) : ProcessingStep {
  override fun process(resolver: Resolver): ProcessingStepResult {
    val originatingFiles = MANAGER_ROOT_ANNOTATIONS
      .asSequence()
      .flatMap(resolver::getSymbolsWithAnnotation)
      .filterIsInstance<KSDeclaration>()
      .mapNotNull(KSDeclaration::containingFile)
      .distinctBy(KSFile::filePath)
      .toList()
    if (originatingFiles.isNotEmpty()) {
      val databaseClassName = environment.getGenClassesManagerClassName()
      environment.codeGenerator.associate(
        sources = originatingFiles,
        packageName = databaseClassName.packageName,
        fileName = databaseClassName.simpleName
      )
    }
    return Continue
  }

  override fun finish(): ProcessingStepResult {
    val database = GeneratedDatabaseElement.from(environment)
    if (!database.shouldGenerate) return Continue
    return try {
      val orderedTables = CreationOrderedTables.from(database.tables)
      val migrationOutcome = DebugMigrationCoordinator(
        configuration = DebugMigrationConfiguration.from(environment.options),
        logger = environment.logger
      ).handle(
        database = database,
        orderedTables = orderedTables
      )
      GenClassesManagerWriter(environment.codeGenerator)
        .write(
          database = database.withDatabaseVersion(
            version = migrationOutcome.databaseVersionOverride
          ),
          orderedTables = orderedTables
        )
      Continue
    } catch (exception: IOException) {
      environment.logger.exception(exception)
      Failed
    }
  }

  private companion object {
    val MANAGER_ROOT_ANNOTATIONS = listOf(
      DATABASE_ANNOTATION,
      SUBMODULE_DATABASE_ANNOTATION,
      TABLE_ANNOTATION,
      OBJECT_TO_DB_VALUE_ANNOTATION,
      DB_VALUE_TO_OBJECT_ANNOTATION
    )
  }
}
