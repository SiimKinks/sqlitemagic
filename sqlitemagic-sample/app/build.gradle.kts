plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
  alias(libs.plugins.sqlitemagic)
}

android {
  namespace = "com.siimkinks.sqlitemagic.sample"
  compileSdk = 37
  buildToolsVersion = "37.0.0"

  defaultConfig {
    applicationId = "com.siimkinks.sqlitemagic.sample"
    minSdk = 24
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"
    buildConfigField("int", "DB_VERSION", "3")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  sourceSets.named("debug") {
    assets.directories.add("src/release/assets")
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  testOptions {
    unitTests.all { it.useJUnitPlatform() }
  }
}

kotlin {
  jvmToolchain(21)
}

sqlitemagic {
  migrateDebugDatabase = false
  migrationTesting {
    releaseVariant = "release"
  }
}

dependencies {
  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.material3)
  implementation(libs.activity.compose)
  implementation(libs.rx.java2)
  implementation(libs.rx.android2)
  implementation(libs.compose.runtime.rxjava2)
  implementation(libs.sqlite.framework)

  debugImplementation(libs.compose.ui.tooling)
  androidTestImplementation(libs.android.test.runner)
  androidTestImplementation(libs.android.test.junit)

  testImplementation(libs.sqlite.bundled.jvm)
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
}
