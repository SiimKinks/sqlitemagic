# Build and setup

Dependencies, the SQLite driver, the reactive adapters and transactions. Do this first: the rest of the upgrade
compiles only against the new runtime and regenerated code. The upgrade of databases already installed is in
[database-upgrade.md](database-upgrade.md).

## Contents

- [Dependencies](#dependencies)
- [Choose a driver](#choose-a-driver)
- [Reactive adapters](#reactive-adapters)
- [Transactions](#transactions)
- [Connection behavior](#connection-behavior)

## Dependencies

Keep the `com.google.devtools.ksp` plugin next to the SqliteMagic plugin and raise the SqliteMagic plugin to 3.x. The
plugin still adds the runtime, annotations and KSP processor; the `sqlitemagic { }` options are unchanged. Every
`com.siimkinks.sqlitemagic:*` artifact a module declares uses the plugin's version.

```kotlin
dependencies {
  // the device's SQLite
  implementation("com.siimkinks.sqlitemagic:sqlitemagic-framework-driver:<plugin version>")
  // or a SQLite shipped with the app
  implementation("androidx.sqlite:sqlite-bundled:<androidx.sqlite version>")

  // where code observes or runs queries reactively, one or more of
  implementation("com.siimkinks.sqlitemagic:sqlitemagic-rxjava2:<plugin version>")
  implementation("com.siimkinks.sqlitemagic:sqlitemagic-rxjava3:<plugin version>")
  implementation("com.siimkinks.sqlitemagic:sqlitemagic-coroutines:<plugin version>")
}
```

- The module that builds the `SqliteMagic` setup declares its driver. Library modules with models need no driver of
  their own: the runtime brings `androidx.sqlite` for the generated code; a JVM test driver such as
  `androidx.sqlite:sqlite-bundled-jvm` keeps its own version.
- Remove `androidx.sqlite:sqlite-framework`, which only served `sqliteFactory(…)`.
- The toolchain is the one 2.0 needed: minimum Android API 24 and a Kotlin compiler that reads Kotlin 2.4 metadata.
- The adapters bring neither RxJava nor coroutines: a module that uses their types keeps declaring RxJava or
  `kotlinx-coroutines-core`, as before. A module that declared RxJava only for the runtime's signatures can drop it.

## Choose a driver

The database runs on an androidx `SQLiteDriver` instead of a SupportSQLite helper factory:

```kotlin
// legacy
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

SqliteMagic.builder(app)
    .database(SqliteMagicDatabase())
    .sqliteFactory(FrameworkSQLiteOpenHelperFactory())
    .openDefaultConnection()

// new: the device's SQLite, as before
import com.siimkinks.sqlitemagic.framework.FrameworkSQLiteDriver

SqliteMagic.builder(app)
    .database(SqliteMagicDatabase())
    .driver(FrameworkSQLiteDriver())
    .openDefaultConnection()

// or a SQLite shipped with the app
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

    .driver(BundledSQLiteDriver())
```

- Default to `FrameworkSQLiteDriver` for a mechanical upgrade: it runs the same SQLite as legacy, with Android's typed
  `SQLiteException` subclasses, the `LOCALIZED` and `UNICODE` collations, ICU case folding in `lower`, `upper` and
  `LIKE`, and the Database Inspector.
- `BundledSQLiteDriver` gives every Android version a recent SQLite (`RIGHT_JOIN`, `FULL_JOIN`, `RETURNING`, newer
  functions) and costs about 1.3 MB per ABI. It has no ICU case folding, no Android collations (`LOCALIZED`,
  `UNICODE`) or functions such as `PHONE_NUMBERS_EQUAL`, and no Database Inspector, so legacy raw SQL that uses them
  fails or matches differently. Its errors are plain `SQLException`s. Once the schema uses SQL the device's SQLite
  does not understand, only a newer SQLite can open the database. Never open the same file through Android's
  database APIs (`SQLiteDatabase`, `SQLiteOpenHelper`, other libraries) while the app runs it on the bundled driver:
  the two SQLite libraries do not see each other's locks and can corrupt the file. Choosing it is a product decision;
  settle it with the user rather than switching silently.
- `driver(…)` takes any `SQLiteDriver`. A custom `SupportSQLiteOpenHelper.Factory` (for example an encrypted or
  requery SQLite) has no adapter; it needs a `SQLiteDriver` for that SQLite.
- A custom `DbDowngrader` receives a `SQLiteConnection` instead of a `SupportSQLiteDatabase`: run statements with
  `connection.execSQL(…)` (`import androidx.sqlite.execSQL`) or `connection.prepare(…)`.

## Reactive adapters

`observe()` and the one-shot reactive calls moved out of the runtime into adapters with the same operators:

| Legacy | New |
| --- | --- |
| `observe()`, `Query.run()`, `scheduleRxQueriesOn`, `ListQueryObservable`, `SingleItemQueryObservable`, `CountQueryObservable` from `com.siimkinks.sqlitemagic` | the same names from `com.siimkinks.sqlitemagic.rxjava2` (`sqlitemagic-rxjava2`) |
| — | RxJava 3: the same API from `com.siimkinks.sqlitemagic.rxjava3` (`sqlitemagic-rxjava3`) |
| — | coroutines: `observe()` returns a cold `Flow` with the same `runQuery` operators; one-shot work uses suspend `runQueryOnce()`, `Query.run()` and `await()`; `dispatchCoroutineQueriesOn` sets the dispatcher; from `com.siimkinks.sqlitemagic.coroutines` (`sqlitemagic-coroutines`) |

- Add the import where code calls `observe()` or `run()`: the compiler reports them as unresolved. Entity operations,
  UPDATE/DELETE statements and raw queries take the import too. The operators on the result (`runQuery()`,
  `runQueryOnce()`, `isZero()`, …) are members and need no import; with coroutines, import them from
  `com.siimkinks.sqlitemagic.coroutines` along with `observe` and `await`.
- `run()` returns `Maybe` of the non-null result type. Count streams drop `runQueryOrDefault(…)` and
  `runQueryOnceOrDefault(…)`, whose default a count never used: call `runQuery()` or `runQueryOnce()`. A count's
  `runQueryOnce()` returns `Single<Long>` instead of `Maybe<Long>`: drop a `toSingle()` or `defaultIfEmpty(…)` after
  it.
- Keep the project's reactive library: RxJava 2 code takes `rxjava2`. Moving to RxJava 3 or coroutines is a separate
  change.
- A file that uses two adapters imports one of them with an alias.

## Transactions

- `inTransaction {}` blocks can no longer suspend or `return`/`break`/`continue` out of the enclosing function. A
  legacy `return` inside the block rolled the transaction back silently; replace it with `return@inTransaction`, which
  commits, or throw to roll back. Move suspend calls out of the block, use the blocking `execute()` forms inside it, or
  run suspending work in `withTransaction { }` from `sqlitemagic-coroutines`.
- A transaction started with `newTransaction()` or `inTransaction {}` belongs to its thread. Reading a reactive query
  on that thread (collecting it, `runQueryOnce()`, `Query.run()`, a raw SELECT's `await()`) fails with
  `IllegalStateException`; use `execute()` inside the transaction.
- The transaction behavior changes are in [behavior.md](behavior.md#smaller-behavior-changes).

## Connection behavior

File databases now use WAL with one writer and up to four reader connections. The changes this makes are
[behavior items](behavior.md#smaller-behavior-changes); the builder options that tune them are:

- `readerConnections(count)`: `0` runs every statement on one connection and leaves the journal mode to
  `configureConnection`.
- `connectionPoolTimeout(duration)`: how long a statement waits for a connection held by other threads (30 s).
- `configureConnection { connection -> … }`: runs after the defaults on every connection, for example
  `connection.execSQL("PRAGMA synchronous = FULL")` to keep every committed transaction through a power loss.
