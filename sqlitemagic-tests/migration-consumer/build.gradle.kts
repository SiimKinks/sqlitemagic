import com.android.build.api.variant.BuildConfigField

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.ksp)
  alias(libs.plugins.sqlitemagic)
}

val javaVersion = JavaVersion.toVersion(libs.versions.java.version.get())

android {
  namespace = "com.siimkinks.sqlitemagic"
  compileSdk = libs.versions.android.compile.sdk.get().toInt()
  buildToolsVersion = libs.versions.android.build.tools.get()

  defaultConfig {
    applicationId = "com.siimkinks.sqlitemagic.migrationconsumer"
    minSdk = libs.versions.android.min.sdk.get().toInt()
    targetSdk = libs.versions.android.target.sdk.get().toInt()
    versionCode = 1
    versionName = "1.0"
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
  compileOptions {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
  }
}

androidComponents {
  onVariants { variant ->
    variant.buildConfigFields?.putAll(
        mapOf(
            "DB_VERSION" to BuildConfigField(
                type = "int",
                value = when {
                  variant.buildType == "release" -> "6"
                  else -> "7"
                },
                comment = null
            ),
            "DB_NAME" to BuildConfigField(
                type = "String",
                value = """"migration-consumer.db"""",
                comment = null
            )
        )
    )
  }
}

dependencies {
  implementation(project(":migration-consumer-feature"))
  implementation(libs.android.sqlite.framework)
  implementation(libs.rx.java2)

  testImplementation(libs.android.sqlite.bundled.jvm)
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.truth)
  testRuntimeOnly(libs.junit.platform.launcher)
}
