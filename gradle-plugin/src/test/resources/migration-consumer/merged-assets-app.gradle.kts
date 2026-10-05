import com.android.build.api.variant.BuildConfigField
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.ksp)
  alias(libs.plugins.sqlitemagic)
}

val releaseVersion = providers.gradleProperty("releaseVersion").get().toInt()
val generateMigrationAsset = tasks.register<GenerateMigrationAsset>("generateMigrationAsset") {
  outputDirectory.set(layout.buildDirectory.dir("generated/migration-assets"))
}

android {
  namespace = "com.siimkinks.sqlitemagic"
  compileSdk = libs.versions.android.compile.sdk.get().toInt()
  buildToolsVersion = libs.versions.android.build.tools.get()
  defaultConfig {
    applicationId = "com.siimkinks.sqlitemagic.mergedassets"
    minSdk = libs.versions.android.min.sdk.get().toInt()
    targetSdk = libs.versions.android.target.sdk.get().toInt()
    versionCode = 1
    versionName = "1.0"
  }
  flavorDimensions += "tier"
  productFlavors {
    create("free") {
      dimension = "tier"
    }
    create("paid") {
      dimension = "tier"
    }
  }
  buildTypes {
    release {
      optimization {
        enable = false
      }
    }
  }
  buildFeatures {
    buildConfig = true
  }
  sourceSets.getByName("main").assets.srcDir("custom-assets")
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

androidComponents {
  onVariants { variant ->
    if (variant.name == "paidRelease") {
      checkNotNull(variant.sources.assets).addGeneratedSourceDirectory(
        taskProvider = generateMigrationAsset,
        wiredWith = GenerateMigrationAsset::outputDirectory
      )
    }
    variant.buildConfigFields?.putAll(
      mapOf(
        "DB_VERSION" to BuildConfigField(
          type = "int",
          value = when {
            variant.buildType == "release" -> releaseVersion.toString()
            else -> "1038"
          },
          comment = null
        ),
        "DB_NAME" to BuildConfigField(
          type = "String",
          value = "\"merged-assets.db\"",
          comment = null
        )
      )
    )
  }
}

sqlitemagic {
  migrationTesting {
    releaseVariant = "paidRelease"
  }
}

dependencies {
  implementation(project(":asset-library"))
  implementation(libs.android.sqlite.framework)
  implementation(libs.rx.java2)
  testImplementation(libs.android.sqlite.bundled.jvm)
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.truth)
  testRuntimeOnly(libs.junit.platform.launcher)
}

abstract class GenerateMigrationAsset : DefaultTask() {
  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun generate() {
    val directory = outputDirectory.get().asFile
    check(directory.isDirectory || directory.mkdirs())
    directory.resolve("8.sql").writeText("UPDATE asset_record SET value=value || '|generated'\n")
  }
}
