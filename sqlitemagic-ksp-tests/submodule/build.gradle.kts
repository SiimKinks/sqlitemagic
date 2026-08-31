plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.ksp)
  alias(libs.plugins.sqlitemagic.ksp)
}

val javaVersion = JavaVersion.toVersion(libs.versions.java.version.get())

android {
  namespace = "com.siimkinks.sqlitemagic.runtime.fixture.submodule"
  compileSdk = libs.versions.android.compile.sdk.get().toInt()
  buildToolsVersion = libs.versions.android.build.tools.get()

  defaultConfig {
    minSdk = libs.versions.android.min.sdk.get().toInt()
  }

  compileOptions {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
  }
}

dependencies {
  implementation(libs.android.sqlite.framework)
  implementation(libs.rx.java2)
}

sqlitemagic {
  publicKotlinExtensionFunctions = true
  mainModulePath = "app/"
}
