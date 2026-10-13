package com.siimkinks.sqlitemagic.manager

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.siimkinks.sqlitemagic.AnnotationNames.DATABASE_ANNOTATION
import com.siimkinks.sqlitemagic.AnnotationNames.DB_VALUE_TO_OBJECT_ANNOTATION
import com.siimkinks.sqlitemagic.AnnotationNames.OBJECT_TO_DB_VALUE_ANNOTATION
import com.siimkinks.sqlitemagic.AnnotationNames.SUBMODULE_DATABASE_ANNOTATION
import com.siimkinks.sqlitemagic.AnnotationNames.TABLE_ANNOTATION
import com.siimkinks.sqlitemagic.AnnotationNames.VIEW_ANNOTATION
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Failed
import java.io.IOException

class GenClassesManagerStep(
  private val environment: Environment
) : ProcessingStep {
  // Every originating file is associated with the manager, so finish() must write it. Otherwise incremental KSP fails
  // when restoring the never-written manager from its backups. Paths are kept because KSP2 invalidates KSFiles after
  // their round.
  private val originatingFilePaths = linkedSetOf<String>()

  override fun process(resolver: Resolver): ProcessingStepResult {
    val originatingFiles = MANAGER_ROOT_ANNOTATIONS
      .asSequence()
      .flatMap(resolver::getSymbolsWithAnnotation)
      .filterIsInstance<KSDeclaration>()
      .mapNotNull(KSDeclaration::containingFile)
      .distinctBy(KSFile::filePath)
      .toList()
    if (originatingFiles.isNotEmpty()) {
      originatingFiles.mapTo(
        destination = originatingFilePaths,
        transform = KSFile::filePath
      )
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
    if (!database.shouldGenerate) {
      if (originatingFilePaths.isEmpty()) return Continue
      environment.logger.error(
        message = buildString {
          append("SqliteMagic annotations require at least one @Table or @View in the same module ")
          append("(or @Database submodules). Remove the annotations or move them to a module with tables: ")
          append(originatingFilePaths.joinToString())
        }
      )
      return Failed
    }
    return try {
      val orderedTables = CreationOrderedTables.from(database.tables)
      val currentStructure = DatabaseStructure.from(
        orderedTables = orderedTables,
        indexes = database.indices,
        views = database.views
      )
      if (!validateConfiguredSubmoduleStructures(environment, database, currentStructure)) {
        return Failed
      }
      DebugMigrationCoordinator(
        configuration = DebugMigrationConfiguration.from(environment.options),
        logger = environment.logger
      ).handle(
        database = database,
        currentStructure = currentStructure,
        completion = { migrationOutcome ->
          GenClassesManagerWriter(environment.codeGenerator)
            .write(
              database = database.withDatabaseVersion(
                version = migrationOutcome.databaseVersionOverride
              ),
              orderedTables = orderedTables
            )
        }
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
      VIEW_ANNOTATION,
      OBJECT_TO_DB_VALUE_ANNOTATION,
      DB_VALUE_TO_OBJECT_ANNOTATION
    )
  }
}
