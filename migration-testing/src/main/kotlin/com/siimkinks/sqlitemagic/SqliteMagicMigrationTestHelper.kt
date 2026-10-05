package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import com.siimkinks.sqlitemagic.MigrationValidationMode.ADJACENT
import com.siimkinks.sqlitemagic.MigrationValidationMode.EVERY_VERSION_TO_CURRENT
import com.siimkinks.sqlitemagic.MigrationValidationMode.OLDEST_TO_CURRENT
import com.siimkinks.sqlitemagic.migration.MigrationDatabase
import com.siimkinks.sqlitemagic.migration.MigrationDatabaseDescriptor
import com.siimkinks.sqlitemagic.migration.MigrationExecutor
import com.siimkinks.sqlitemagic.migration.MigrationManifest
import com.siimkinks.sqlitemagic.migration.MigrationMetadataJson
import com.siimkinks.sqlitemagic.migration.MigrationPlanner
import com.siimkinks.sqlitemagic.migration.MigrationResource
import com.siimkinks.sqlitemagic.migration.MigrationScriptValidation
import com.siimkinks.sqlitemagic.migration.MigrationStep
import com.siimkinks.sqlitemagic.migration.MigrationViewSchema
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaResource
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaSnapshot
import com.siimkinks.sqlitemagic.migration.testing.SQLiteQueries
import com.siimkinks.sqlitemagic.migration.testing.SchemaComparator
import com.siimkinks.sqlitemagic.migration.testing.SchemaIntrospector
import com.siimkinks.sqlitemagic.migration.testing.createPersistentViews
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/** Local JVM migration validation using an explicitly supplied SQLite engine implementation. */
class SqliteMagicMigrationTestHelper(
  private val descriptor: MigrationDatabaseDescriptor,
  private val driver: SQLiteDriver
) : AutoCloseable {
  private val driverImplementation = driver.javaClass.name
  private var sqliteVersion: String? = null
  private val manifest = readManifest()
  private val versions = manifest.schemas.map(ReleaseSchemaResource::version)
  private val baseline = descriptor.firstVersion ?: versions.first()
  private val coveredVersions = versions.filter { it >= baseline }
  private val compiledCurrent = readCompiledCurrent()
  private val schemas = readCoveredSchemas()
  private val resources = readCoveredResources()
  private val directory = Files.createTempDirectory("sqlitemagic-migrations-")
  private val files = linkedMapOf<String, Path>()
  private val ownedFiles = linkedSetOf<Path>()
  private val connections = linkedMapOf<String, TestDatabase>()
  private var closed = false
  private var automaticRun = 0

  fun validateAllMigrations(
    mode: MigrationValidationMode = EVERY_VERSION_TO_CURRENT
  ): MigrationValidationReport = contextual(operation = "validating covered release history") {
    checkOpen()
    verifyCurrentSnapshot()
    val historical = coveredVersions.dropLast(1)
    val paths = when (mode) {
      EVERY_VERSION_TO_CURRENT -> historical.map { version ->
        MigrationValidationPath(
          fromVersion = version,
          toVersion = descriptor.currentVersion
        )
      }
      ADJACENT -> coveredVersions.zipWithNext { from, to ->
        MigrationValidationPath(
          fromVersion = from,
          toVersion = to
        )
      }
      OLDEST_TO_CURRENT -> historical.take(1)
        .map { version ->
          MigrationValidationPath(
            fromVersion = version,
            toVersion = descriptor.currentVersion
          )
        }
    }
    automaticRun++
    paths.forEach { path ->
      val name = "automatic-$automaticRun-${path.fromVersion}-${path.toVersion}"
      createDatabase(
        name = name,
        version = path.fromVersion
      ).close()
      runMigrationsAndValidate(
        name = name,
        fromVersion = path.fromVersion,
        toVersion = path.toVersion
      ).close()
    }
    MigrationValidationReport(
      databaseId = descriptor.databaseId,
      releaseVariant = descriptor.releaseVariant,
      baselineVersion = baseline,
      currentVersion = descriptor.currentVersion,
      excludedVersions = versions.filter { it < baseline },
      paths = paths,
      mode = mode,
      driverImplementation = driverImplementation,
      sqliteVersion = checkNotNull(sqliteVersion)
    )
  }

  fun createDatabase(
    name: String,
    version: Int
  ): MigrationTestDatabase = contextual(operation = "creating '$name' at release $version") {
    checkOpen()
    require(name.isNotBlank()) { "Database logical name must not be blank" }
    require(name !in files) { "Database logical name '$name' was already created" }
    val schema = schema(version)
    val file = Files.createTempFile(directory, "database-", ".sqlite")
    ownedFiles.add(file)
    files[name] = file
    val connection = open(
      file = file,
      schema = schema
    )
    try {
      createSchema(
        connection = connection,
        schema = schema
      )
      register(
        name = name,
        connection = connection
      )
    } catch (failure: Throwable) {
      closeAfterFailure(
        connection = connection,
        failure = failure
      )
      throw failure
    }
  }

  fun runMigrationsAndValidate(
    name: String,
    fromVersion: Int,
    toVersion: Int = descriptor.currentVersion
  ): MigrationTestDatabase = contextual(operation = "migration $fromVersion -> $toVersion for '$name'") {
    checkOpen()
    require(toVersion >= fromVersion) { "Invalid migration range $fromVersion -> $toVersion" }
    require(name !in connections) { "Database '$name' has an active connection; close it before migrating" }
    val source = schema(fromVersion)
    val target = expectedSchema(toVersion)
    val file = files[name] ?: error("No database named '$name'; create its historical schema first")
    val connection = open(
      file = file,
      schema = source
    )
    try {
      require(userVersion(connection) == fromVersion.toLong()) {
        "Database '$name' user_version differs from $fromVersion"
      }
      applyConfiguration(
        connection = connection,
        schema = target
      )
      val database = DriverMigrationDatabase(connection)
      val plan = MigrationPlanner.plan(
        fromVersion = fromVersion,
        toVersion = toVersion,
        submoduleNames = target.modules.dropLast(1)
      )
      transaction(connection = connection) {
        MigrationExecutor(
          resources = { resource ->
            ByteArrayInputStream(resources[resource.name] ?: throw java.io.FileNotFoundException(resource.name))
          },
          database = database
        ).execute(
          plan = plan,
          createTargetViews = { database.createPersistentViews(target.views) }
        )
        SQLiteQueries.execute(
          connection = connection,
          sql = "PRAGMA user_version = $toVersion"
        )
      }
      require(userVersion(connection) == toVersion.toLong()) { "Migration did not set user_version to $toVersion" }
      verifyCurrentSnapshot()
      withFreshSchema(schema = expectedSchema(toVersion)) { expected ->
        compareSchemas(
          expected = expected,
          actual = connection,
          operation = "Migration $fromVersion -> $toVersion completed with a different target schema"
        )
      }
      verifyIntegrity(connection)
      register(
        name = name,
        connection = connection
      )
    } catch (failure: Throwable) {
      closeAfterFailure(
        connection = connection,
        failure = failure
      )
      throw failure
    }
  }

  override fun close() {
    if (closed) return
    closed = true
    var failure: Throwable? = null
    fun cleanup(action: () -> Unit) {
      try {
        action()
      } catch (cleanupFailure: Throwable) {
        when (val original = failure) {
          null -> failure = cleanupFailure
          else -> original.addSuppressed(cleanupFailure)
        }
      }
    }
    connections.values
      .toList()
      .forEach { connection -> cleanup(connection::close) }
    ownedFiles.forEach { file ->
      cleanup {
        listOf(file, Path.of("$file-wal"), Path.of("$file-shm"), Path.of("$file-journal"))
          .forEach(Files::deleteIfExists)
      }
    }
    cleanup { Files.deleteIfExists(directory) }
    failure?.let { throw IllegalStateException("Migration database cleanup failed; retained path: $directory", it) }
  }

  private fun readManifest(): MigrationManifest = contextual(
    operation = "reading manifest ${descriptor.manifestResource}"
  ) {
    val result = MigrationMetadataJson.readManifest(readText(descriptor.manifestResource))
    require(
      result.databaseId == descriptor.databaseId && result.releaseVariant == descriptor.releaseVariant &&
          result.currentVersion == descriptor.currentVersion
    ) { "Manifest identity, selected release variant, or target version differs from generated descriptor" }
    result
  }

  private fun readCoveredSchemas(): Map<Int, ReleaseSchemaSnapshot> = contextual(
    operation = "reading retained schema history; incomplete evidence requires an explicit coverage baseline " +
        "or trusted historical export"
  ) {
    require(baseline in versions) { "Explicit migration coverage baseline $baseline is not a retained release" }
    coveredVersions.associateWith { version ->
      val resource = manifest.schema(version)
      contextual(operation = "reading release $version snapshot ${resource.name}") {
        MigrationMetadataJson.readStructure(
          text = readText(resource.name),
          manifest = manifest,
          reference = resource
        )
      }
    }
  }

  private fun readCompiledCurrent(): ReleaseSchemaSnapshot = contextual(
    operation = "reading independent compiled release export"
  ) {
    MigrationMetadataJson.readSchema(readText(descriptor.currentSchemaResource))
      .also { snapshot ->
        manifest.validateSchema(snapshot)
        require(snapshot.version == descriptor.currentVersion) {
          "Compiled release export has the wrong target version"
        }
      }
  }

  private fun readCoveredResources(): Map<String, ByteArray> = contextual(
    operation = "preparing covered migration resources"
  ) {
    val coveredSteps = manifest.resolve(
      fromVersion = baseline,
      toVersion = descriptor.currentVersion
    ).steps
    val plan = MigrationPlanner.plan(
      fromVersion = baseline,
      toVersion = descriptor.currentVersion,
      submoduleNames = manifest.modules.dropLast(1)
    )
    val evidence = coveredSteps.flatMap(MigrationStep::sqlResources)
      .associateBy(MigrationResource::name)
    val bytes = linkedMapOf<String, ByteArray>()
    fun readResource(
      name: String,
      stream: InputStream
    ): ByteArray {
      val contents = stream.use(InputStream::readBytes)
      val text = MigrationScriptValidation.readUtf8(ByteArrayInputStream(contents))
      MigrationScriptValidation.validate(
        text = text,
        resourceName = name,
        allowEmpty = evidence[name]?.allowEmpty ?: true
      )
      bytes[name] = contents
      return contents
    }
    coveredSteps.forEach { step ->
      step.sqlResources.forEach { resource ->
        contextual(operation = "reading manifest-listed SQL ${resource.name} for step ${step.toVersion}") {
          readResource(
            name = resource.name,
            stream = descriptor.openResource(resource.name)
          )
        }
      }
    }
    plan.steps.flatMap(MigrationStep::sqlResources)
      .filter { it.name !in bytes }
      .forEach { resource ->
        val stream = try {
          descriptor.openResource(resource.name)
        } catch (_: java.io.FileNotFoundException) {
          null
        }
        stream?.let {
          readResource(
            name = resource.name,
            stream = it
          )
        }
      }
    bytes
  }

  private fun readText(name: String) = MigrationScriptValidation.readUtf8(descriptor.openResource(name))

  private fun schema(version: Int) = schemas[version]
    ?: error("Release $version is not covered by the selected history starting at $baseline")

  private fun expectedSchema(version: Int) = when (version) {
    descriptor.currentVersion -> compiledCurrent
    else -> schema(version)
  }

  private fun verifyCurrentSnapshot() = contextual(
    operation = "verifying current snapshot against compiled release export"
  ) {
    withFreshSchema(schema = schema(descriptor.currentVersion)) { committed ->
      withFreshSchema(schema = compiledCurrent.copy(views = emptyList())) { compiled ->
        require(schema(descriptor.currentVersion).configuration == compiledCurrent.configuration) {
          "Current snapshot configuration differs from independent compiled release export"
        }
        require(schema(descriptor.currentVersion).modules == compiledCurrent.modules) {
          "Current snapshot module order differs from independent compiled release export"
        }
        compareSchemas(
          expected = compiled,
          actual = committed,
          operation = "Current release snapshot is stale relative to the independent compiled release export"
        )
        val names = MigrationMetadataJson.structureViewNames(
          readText(manifest.schema(descriptor.currentVersion).name)
        )
        require(names.toSet() == compiledCurrent.views.map(MigrationViewSchema::name).toSet()) {
          "Current snapshot view names differ from independent compiled release export"
        }
        verifyIntegrity(compiled)
      }
    }
  }

  private fun <T> withFreshSchema(
    schema: ReleaseSchemaSnapshot,
    action: (SQLiteConnection) -> T
  ): T {
    val file = Files.createTempFile(directory, "expected-", ".sqlite")
    // Register every owned path before opening, including failed native initialization.
    ownedFiles.add(file)
    val connection = open(
      file = file,
      schema = schema
    )
    return connection.use {
      createSchema(
        connection = connection,
        schema = schema
      )
      action(connection)
    }
  }

  private fun createSchema(
    connection: SQLiteConnection,
    schema: ReleaseSchemaSnapshot
  ) = transaction(connection = connection) {
    (schema.tableSql + schema.viewSql + schema.indexSql).forEach { sql ->
      SQLiteQueries.execute(
        connection = connection,
        sql = sql
      )
    }
    SQLiteQueries.execute(
      connection = connection,
      sql = "PRAGMA user_version = ${schema.version}"
    )
  }

  private fun open(
    file: Path,
    schema: ReleaseSchemaSnapshot
  ): SQLiteConnection {
    val connection = try {
      driver.open(file.toString())
    } catch (failure: Exception) {
      throw openingFailure(failure)
    } catch (failure: LinkageError) {
      throw openingFailure(failure)
    }
    try {
      val observedVersion = SQLiteQueries.query(
        connection = connection,
        sql = "SELECT sqlite_version()"
      )
        .rows
        .single()
        .single() as String
      require(sqliteVersion == null || sqliteVersion == observedVersion) {
        "Driver opened different SQLite engine versions"
      }
      sqliteVersion = observedVersion
      applyConfiguration(
        connection = connection,
        schema = schema
      )
      return connection
    } catch (failure: Throwable) {
      closeAfterFailure(
        connection = connection,
        failure = failure
      )
      throw failure
    }
  }

  private fun applyConfiguration(
    connection: SQLiteConnection,
    schema: ReleaseSchemaSnapshot
  ) {
    val setting = when {
      schema.configuration.foreignKeysEnabled -> 1
      else -> 0
    }
    SQLiteQueries.execute(
      connection = connection,
      sql = "PRAGMA foreign_keys = $setting"
    )
    val actual = SQLiteQueries.query(
      connection = connection,
      sql = "PRAGMA foreign_keys"
    ).rows
    require(actual == listOf(listOf(setting.toLong()))) { "Driver did not apply release foreign-key policy $setting" }
  }

  private fun userVersion(connection: SQLiteConnection) = SQLiteQueries.query(
    connection = connection,
    sql = "PRAGMA user_version"
  )
    .rows
    .single()
    .single() as Long

  private fun compareSchemas(
    expected: SQLiteConnection,
    actual: SQLiteConnection,
    operation: String
  ) {
    val differences = SchemaComparator.compare(
      expected = SchemaIntrospector.inspect(expected),
      actual = SchemaIntrospector.inspect(actual)
    )
    check(differences.isEmpty()) { "$operation:\n${differences.take(30).joinToString(separator = "\n")}" }
  }

  private fun verifyIntegrity(connection: SQLiteConnection) {
    val integrity = SQLiteQueries.query(
      connection = connection,
      sql = "PRAGMA integrity_check"
    ).rows
    check(integrity == listOf(listOf("ok"))) { "SQLite integrity_check failed: ${integrity.take(20)}" }
    val foreignKeys = SQLiteQueries.query(
      connection = connection,
      sql = "PRAGMA foreign_key_check"
    ).rows
    check(foreignKeys.isEmpty()) { "SQLite foreign_key_check failed: ${foreignKeys.take(20)}" }
  }

  private fun register(
    name: String,
    connection: SQLiteConnection
  ): MigrationTestDatabase = TestDatabase(
    connection = connection,
    onClose = { connections.remove(name) }
  )
    .also { connections[name] = it }

  private fun transaction(
    connection: SQLiteConnection,
    action: () -> Unit
  ) {
    SQLiteQueries.execute(
      connection = connection,
      sql = "BEGIN TRANSACTION"
    )
    try {
      action()
      SQLiteQueries.execute(
        connection = connection,
        sql = "COMMIT"
      )
    } catch (failure: Throwable) {
      try {
        SQLiteQueries.execute(
          connection = connection,
          sql = "ROLLBACK"
        )
      } catch (rollbackFailure: Throwable) {
        failure.addSuppressed(rollbackFailure)
      }
      throw failure
    }
  }

  private fun closeAfterFailure(
    connection: SQLiteConnection,
    failure: Throwable
  ) {
    try {
      connection.close()
    } catch (closeFailure: Throwable) {
      failure.addSuppressed(closeFailure)
    }
  }

  private fun checkOpen() = check(!closed) { "Migration helper was already closed" }

  private fun openingFailure(failure: Throwable) = IllegalStateException(
    "Cannot open SQLite with $driverImplementation on ${System.getProperty("os.name")}/" +
        "${System.getProperty("os.arch")}; SQLite version unavailable. " +
        "Verify your test driver dependency version and its native packaging for this host.",
    failure
  )

  private fun <T> contextual(
    operation: String,
    action: () -> T
  ): T = try {
    action()
  } catch (failure: Exception) {
    throw contextualFailure(
      operation = operation,
      failure = failure
    )
  } catch (failure: LinkageError) {
    throw contextualFailure(
      operation = operation,
      failure = failure
    )
  }

  private fun contextualFailure(
    operation: String,
    failure: Throwable
  ) = IllegalStateException(
    "Database ${descriptor.databaseId}, release ${descriptor.releaseVariant}: $operation failed. " +
        "Driver $driverImplementation, SQLite ${sqliteVersion ?: "unavailable"}. ${failure.message}",
    failure
  )
}

private class DriverMigrationDatabase(private val connection: SQLiteConnection) : MigrationDatabase {
  override fun execute(sql: String) = SQLiteQueries.execute(
    connection = connection,
    sql = sql
  )

  override fun persistentViewNames(): List<String> = SQLiteQueries.query(
    connection = connection,
    sql = "SELECT name FROM main.sqlite_schema WHERE type = 'view'"
  )
    .rows
    .map { it.single() as String }
}

private class TestDatabase(
  private val connection: SQLiteConnection,
  private val onClose: () -> Unit
) : MigrationTestDatabase {
  private var closed = false

  override fun execute(
    sql: String,
    bindArgs: List<Any?>
  ) {
    check(!closed) { "Migration test database connection was already closed" }
    SQLiteQueries.execute(
      connection = connection,
      sql = sql,
      bindArgs = bindArgs
    )
  }

  override fun query(
    sql: String,
    bindArgs: List<Any?>
  ): MigrationTestResult {
    check(!closed) { "Migration test database connection was already closed" }
    val result = SQLiteQueries.query(
      connection = connection,
      sql = sql,
      bindArgs = bindArgs
    )
    return MigrationTestResult(
      columns = result.columns,
      rows = result.rows
    )
  }

  override fun close() {
    if (closed) return
    closed = true
    try {
      connection.close()
    } finally {
      onClose()
    }
  }
}
