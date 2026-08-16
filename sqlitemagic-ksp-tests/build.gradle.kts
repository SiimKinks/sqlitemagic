import org.gradle.api.tasks.testing.Test

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.sqlitemagic.ksp) apply false
}

allprojects {
  repositories {
    mavenLocal()
    mavenCentral()
    google()
  }
}

subprojects {
  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
  }
}

tasks.named<Wrapper>("wrapper") {
  gradleVersion = "9.7.0"
  distributionUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-all.zip"
}
