pluginManagement {
  repositories {
    exclusiveContent {
      forRepository { mavenLocal() }
      filter { includeGroup("com.siimkinks.sqlitemagic") }
    }
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    exclusiveContent {
      forRepository { mavenLocal() }
      filter { includeGroup("com.siimkinks.sqlitemagic") }
    }
    google()
    mavenCentral()
  }
}

rootProject.name = "SqliteMagicSample"
include(":app")
