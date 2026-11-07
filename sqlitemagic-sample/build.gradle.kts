plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.sqlitemagic) apply false
}

tasks.named<Wrapper>("wrapper") {
  gradleVersion = "9.9.0"
  distributionUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-all.zip"
}