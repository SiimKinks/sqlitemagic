package com.siimkinks.sqlitemagic

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.Component
import com.android.build.api.variant.HasUnitTest
import com.android.build.api.variant.ScopedArtifacts
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import java.util.Locale

internal fun configureMigrationTesting(
  project: Project,
  extension: SqliteMagicPluginExtension,
  components: AndroidComponentsExtension<*, *, *>
) {
  val releases = linkedMapOf<String, ReleaseExport>()
  val prepare by lazy {
    project.tasks.register("prepareSqliteMagicMigrationTests", PrepareSqliteMagicMigrationTests::class.java) { task ->
      task.group = "verification"
      task.description = "Prepares immutable selected-release evidence for local JVM migration tests"
      task.databaseId.set(project.path)
      task.releaseDirectory.set(project.layout.projectDirectory.dir("db/releases"))
      task.generatedSources.set(project.layout.buildDirectory.dir("sqlitemagic/migration-testing/sources"))
      task.generatedResources.set(project.layout.buildDirectory.dir("sqlitemagic/migration-testing/resources"))
      extension.migrationTesting.firstVersion?.let(task.firstVersion::set)
    }
  }
  components.onVariants { component ->
    if (!extension.migrationTesting.enabled) return@onVariants
    val variant = component as ApplicationVariant
    val tests = (variant.hostTests.values + listOfNotNull((variant as HasUnitTest).unitTest))
      .distinctBy(Component::name)
    tests.forEach { test ->
      val sources = checkNotNull(test.sources.kotlin) { "Local JVM migration tests require Kotlin sources" }
      sources.addGeneratedSourceDirectory(
        taskProvider = prepare,
        wiredWith = PrepareSqliteMagicMigrationTests::generatedSources
      )
      checkNotNull(test.sources.resources).addGeneratedSourceDirectory(
        taskProvider = prepare,
        wiredWith = PrepareSqliteMagicMigrationTests::generatedResources
      )
    }
    if (variant.buildType == "debug") return@onVariants
    val export = project.tasks.register(
      "exportSqliteMagic${variant.name.taskPart()}Schema",
      ExportSqliteMagicReleaseSchema::class.java
    ) { task ->
      task.group = "db"
      task.databaseId.set(project.path)
      task.releaseVariant.set(variant.name)
      task.generatedDatabaseClass.set("com.siimkinks.sqlitemagic.SqliteMagicDatabase")
      task.outputFile.set(project.layout.buildDirectory.file("sqlitemagic/${variant.name}/current.schema.json"))
      task.bootClasspath.from(components.sdkComponents.bootClasspath)
      task.toolingClasspath.from(
        project.configurations.detachedConfiguration(
          project.dependencies.create(
            "com.siimkinks.sqlitemagic:sqlitemagic-migration-testing:$PLUGIN_VERSION"
          )
        )
      )
    }
    variant.artifacts.forScope(ScopedArtifacts.Scope.ALL)
      .use(export)
      .toGet(
        type = ScopedArtifact.CLASSES,
        inputJars = ExportSqliteMagicReleaseSchema::classJars,
        inputDirectories = ExportSqliteMagicReleaseSchema::classDirectories
      )
    releases[variant.name] = ReleaseExport(
      variant = variant,
      export = export
    )
  }
  project.gradle.projectsEvaluated {
    if (!extension.migrationTesting.enabled) return@projectsEvaluated
    val selectedName = selectMigrationReleaseVariant(
      candidates = releases.keys.toList(),
      requested = extension.migrationTesting.releaseVariant
    )
    val selected = releases.getValue(selectedName)
    val taskPath = "${project.path.removeSuffix(":")}:migrate${selectedName.taskPart()}Db"
    val assetsDirectory = selected.variant.artifacts.get(SingleArtifact.ASSETS)
    prepare.configure { task ->
      task.releaseVariant.set(selectedName)
      task.assetsDirectory.set(assetsDirectory)
      task.dependsOn(assetsDirectory)
      task.migrationTaskPath.set(taskPath)
      task.compiledSchema.set(selected.export.flatMap(ExportSqliteMagicReleaseSchema::outputFile))
    }
    releases.forEach { (name, release) ->
      project.tasks.named("migrate${name.taskPart()}Db", PublishSqliteMagicRelease::class.java).configure { task ->
        task.selectedRelease.set(selectedName)
        if (name == selectedName) {
          task.compiledSchema.set(release.export.flatMap(ExportSqliteMagicReleaseSchema::outputFile))
        }
      }
    }
  }
}

internal fun selectMigrationReleaseVariant(
  candidates: List<String>,
  requested: String?
): String = when {
  requested != null -> {
    check(requested in candidates) {
      "Selected migration release variant '$requested' is unavailable. " +
          "Available release variants: ${candidates.sorted()}"
    }
    requested
  }
  candidates.isNotEmpty() -> candidates.first()
  else -> throw GradleException(
    "Migration testing requires an eligible non-debug release variant; none were registered."
  )
}

private data class ReleaseExport(
  val variant: ApplicationVariant,
  val export: TaskProvider<ExportSqliteMagicReleaseSchema>
)

private fun String.taskPart() = replaceFirstChar { it.titlecase(Locale.ROOT) }
