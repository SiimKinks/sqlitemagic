# Setup

Dependencies, the SQLite driver, initialization, connection tuning, modules and logging.

## Contents

- [Dependencies](#dependencies)
- [Plugin options](#plugin-options)
- [Initialize](#initialize)
- [Choose a driver](#choose-a-driver)
- [Connections and their defaults](#connections-and-their-defaults)
- [Models in several modules](#models-in-several-modules)
- [Logging and downgrades](#logging-and-downgrades)

## Dependencies

SqliteMagic 3 supports Android API 24 and newer, Kotlin and KSP. Apply the KSP plugin next to the SqliteMagic plugin;
the plugin adds the runtime, annotations and KSP processor. Every `com.siimkinks.sqlitemagic:*` artifact uses the
plugin's version.

```kotlin
plugins {
  id("com.android.application")
  id("com.google.devtools.ksp")
  id("com.siimkinks.sqlitemagic")
}

dependencies {
  // one driver, in the module that builds the SqliteMagic setup
  implementation("com.siimkinks.sqlitemagic:sqlitemagic-framework-driver:<sqlitemagic version>") // device SQLite
  // or: implementation("androidx.sqlite:sqlite-bundled:<androidx.sqlite version>")               // bundled SQLite

  // the reactive adapter the module uses, if any
  implementation("com.siimkinks.sqlitemagic:sqlitemagic-coroutines:<sqlitemagic version>")
  // implementation("com.siimkinks.sqlitemagic:sqlitemagic-rxjava3:<sqlitemagic version>")
  // implementation("com.siimkinks.sqlitemagic:sqlitemagic-rxjava2:<sqlitemagic version>")
}
```

- The adapters do not bring kotlinx-coroutines or RxJava: a module that uses their types declares them itself.
- Library modules that only hold models need no driver; the plugin gives them the framework driver as `compileOnly`.
- SqliteMagic 3 is published from source to Maven local or a self-hosted repository (see the README); keep
  `mavenLocal()` or that repository in both `pluginManagement` and `dependencyResolutionManagement`.

## Plugin options

All optional; these are the defaults:

```kotlin
sqlitemagic {
  configureAutomatically = true          // add runtime, annotations and processor dependencies
  publicKotlinExtensionFunctions = false // generated insert()/update()/… are internal unless this is true
  migrateDebugDatabase = true            // debug builds migrate automatically when the schema changes
  mainModulePath = null                  // library module with models: path of the app module, e.g. "app"
  migrationTesting {
    enabled = true                       // generate JVM migration test support in application modules
    releaseVariant = null                // release variant whose history tests validate; null = first release variant
  }
}
```

Set `publicKotlinExtensionFunctions = true` in a module whose models other modules write: the generated entity
operations are `internal` by default.

## Initialize

Annotate one class with `@Database` (usually the `Application`), build the generated `SqliteMagicDatabase` into the
default connection once in `Application.onCreate`, and keep it for the process lifetime:

```kotlin
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.annotation.Database
import com.siimkinks.sqlitemagic.framework.FrameworkSQLiteDriver

@Database(
  name = "library.db",
  version = 1
)
class App : Application() {
  override fun onCreate() {
    super.onCreate()
    SqliteMagic.builder(this)
      .database(SqliteMagicDatabase())
      .driver(FrameworkSQLiteDriver())
      .openDefaultConnection()
  }
}
```

- `openDefaultConnection()` only configures; the database opens on first use, creating or migrating the schema then.
  There is no other hidden startup cost, and nothing to close.
- Every query, statement and entity operation uses the default connection unless given another with
  `usingConnection(connection)`. Do not close or replace the default connection during normal operation.
- `openNewConnection()` on the same builder returns a separate `DbConnection`, for example to a second database
  file with `.name("cache.db")`; close it when done. Pass it explicitly with `usingConnection(…)`.
- A reactive adapter adds builder options: `dispatchCoroutineQueriesOn(dispatcher)` (coroutines, default
  `Dispatchers.IO`), `scheduleRxQueriesOn(scheduler)` (RxJava, default `Schedulers.io()`). Import them from the
  adapter's package.

## Choose a driver

There is no default driver. Keep the project's choice; for a new project:

| | `FrameworkSQLiteDriver` (`sqlitemagic-framework-driver`) | `BundledSQLiteDriver` (`androidx.sqlite:sqlite-bundled`) |
| --- | --- | --- |
| SQLite version | the device's: 3.9 on API 24, 3.28 on API 30, 3.39 on API 34 | recent, the same on every device |
| APK size | nothing added | about 1.3 MB per ABI |
| Errors | Android's typed `SQLiteException` subclasses | plain `SQLException`, result code in the message |
| Text | ICU case folding in `LOWER`/`UPPER`/`LIKE`, `LOCALIZED` and `UNICODE` collations | ASCII-only folding, no Android collations |
| Tooling | Android Studio Database Inspector | none |
| Limits | a row larger than a cursor window (~2 MB) cannot be read; `RETURNING` re-runs per window | none of these |

- Choose bundled when the app needs newer SQL on every supported device: window functions (SQLite 3.25), `FILTER`
  (3.30), `RIGHT`/`FULL` joins (3.39), row values (3.15) or `RETURNING` (3.35). On the framework driver those fail on
  older devices when the statement is prepared; the library does not check versions or fall back.
- Choose framework when locale-aware text handling, minimal APK size or the Database Inspector matter.
- Switching an installed app from framework to bundled is safe for the file; once the schema uses SQL the device's
  SQLite lacks, only a SQLite that has it can open the file.
- With the bundled driver, never open the same file through Android's database APIs (`SQLiteDatabase`,
  `SQLiteOpenHelper`, other libraries): two SQLite libraries in one process do not see each other's locks and can
  corrupt it.

## Connections and their defaults

File databases run in WAL mode with one writer connection and up to four reader connections that open on demand:

- Reads run alongside writes and see the last committed data; the thread that runs a transaction reads its own
  changes. Reads of temporary tables and views, and raw SELECTs that declare no tables, run on the writer.
- A statement waits up to `connectionPoolTimeout` (30 s) for a connection other threads hold, then throws
  `SQLException`. A long transaction blocks every other writer for its duration: keep transactions short.
- `PRAGMA busy_timeout = 3000` for locks held by other connections or processes.
- `PRAGMA synchronous = NORMAL`, set together with WAL: a commit just before power loss can roll back, the file
  stays intact.
- Foreign keys are enforced (`PRAGMA foreign_keys = ON`) when any relationship uses `onDeleteCascade`, recursively
  handled or not.
- The database is a set of files: `<name>`, `<name>-wal`, `<name>-shm`. Backups and copies must include all three, or
  run `PRAGMA wal_checkpoint(TRUNCATE)` first.

Tune them on the builder; `configureConnection` runs on every connection after the defaults:

```kotlin
import androidx.sqlite.execSQL

SqliteMagic.builder(this)
  .database(SqliteMagicDatabase())
  .driver(BundledSQLiteDriver())
  .readerConnections(2)                 // 0: one connection, no WAL or NORMAL; both left to configureConnection
  .connectionPoolTimeout(10.seconds)    // Duration.INFINITE waits without limit
  .configureConnection { connection ->
    connection.execSQL("PRAGMA synchronous = FULL")
  }
  .openDefaultConnection()
```

`DbConnection.clearData()` deletes all rows of all tables, for example on sign-out; observers are notified.

## Models in several modules

- A library module with models applies the same plugins, declares `@SubmoduleDatabase("name")` on one class, and sets
  `mainModulePath` to the application module's path and usually `publicKotlinExtensionFunctions = true`.
- The application's `@Database` lists them:
  `@Database(name = …, version = …, submodules = [FeatureDatabase::class])`.
- Transformers declared in another module are registered with `externalTransformers = [ThatObject::class]` on
  `@Database` or `@SubmoduleDatabase`, naming the class or object that holds the functions.
- Generated tables, columns and `SqlType` selectors live in `com.siimkinks.sqlitemagic` in every module.

## Logging and downgrades

- `SqliteMagic.setLoggingEnabled(BuildConfig.DEBUG)` logs database actions; `SqliteMagic.setLogger(logger)` routes them
  (`Logger.logDebug`, `Logger.logError`). Every failing entity operation logs one `Operation failed` error;
  query and statement errors are only thrown. Query times are not logged; time them in the caller.
- When the installed database version is higher than the app's (a downgrade), the default downgrader drops and
  recreates every table and view, deleting all data. Provide `downgrader { connection, oldVersion, newVersion -> … }`
  (a `DbDowngrader`) to do something else; it runs in an exclusive transaction with a `SQLiteConnection`.
