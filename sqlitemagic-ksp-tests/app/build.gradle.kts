plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.ksp)
  alias(libs.plugins.sqlitemagic.ksp)
}

val javaVersion = JavaVersion.toVersion(libs.versions.java.version.get())

android {
  namespace = "com.siimkinks.sqlitemagic"
  compileSdk = libs.versions.android.compile.sdk.get().toInt()
  buildToolsVersion = libs.versions.android.build.tools.get()

  defaultConfig {
    applicationId = "com.siimkinks.sqlitemagic"
    minSdk = libs.versions.android.min.sdk.get().toInt()
    targetSdk = libs.versions.android.target.sdk.get().toInt()
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      optimization {
        enable = false
      }
    }
  }
  compileOptions {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
  }
}

dependencies {
  implementation(libs.android.sqlite.framework)
  implementation(libs.rx.java2)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.truth)
  testImplementation(libs.mockito.kotlin)
  testRuntimeOnly(libs.junit.platform.launcher)

  androidTestImplementation(libs.android.test.runner)
  androidTestImplementation(libs.junit.runner)
}
