package com.siimkinks.sqlitemagic

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.SqliteObjectKind.TABLE
import com.siimkinks.sqlitemagic.SqliteObjectKind.VIEW
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

internal class GeneratedViewResolverTest {
  @Test
  fun registrationPermutationsPreserveViewCreationAndRecreationDependencies() {
    val base = generatedView(
      name = "base",
      sources = listOf(tableSource("books"))
    )
    val left = generatedView(
      name = "left",
      sources = listOf(viewSource("base"))
    )
    val right = generatedView(
      name = "right",
      sources = listOf(viewSource("base"))
    )
    val top = generatedView(
      name = "top",
      sources = listOf(viewSource("left"), viewSource("right"))
    )
    val independent = generatedView(
      name = "independent",
      sources = listOf(tableSource("authors"))
    )
    val cases = listOf(
      "dependencies first" to listOf(base, left, right, top, independent),
      "dependents first" to listOf(top, right, left, base, independent),
      "independent first" to listOf(independent, top, right, left, base),
      "interleaved" to listOf(left, independent, top, base, right)
    )
    val names = listOf("base", "left", "right", "top", "independent")
    val edges = listOf("base" to "left", "base" to "right", "left" to "top", "right" to "top")
    val expectedCreates = names.map(::createSql)
    val expectedDrops = names.map { """DROP VIEW IF EXISTS main."$it"""" }

    for ((label, registry) in cases) {
      val statements = mutableListOf<String>()
      val database = mock<SupportSQLiteDatabase>()
      doAnswer { invocation ->
        statements += invocation.getArgument<String>(0)
      }.whenever(database)
        .execSQL(any())

      SqlUtil.createViews(
        db = database,
        views = registry,
        temporary = false
      )
      assertThat(statements).containsExactlyElementsIn(expectedCreates)
      for ((dependency, dependent) in edges) {
        assertWithMessage("$label: create $dependency before $dependent")
          .that(statements.indexOf(createSql(dependency)))
          .isLessThan(statements.indexOf(createSql(dependent)))
      }

      statements.clear()
      SqlUtil.recreateViews(
        db = database,
        views = registry
      )
      assertThat(statements.take(names.size)).containsExactlyElementsIn(expectedDrops)
      assertThat(statements.drop(names.size)).containsExactlyElementsIn(expectedCreates)
      for ((dependency, dependent) in edges) {
        assertWithMessage("$label: drop $dependent before $dependency")
          .that(statements.indexOf("""DROP VIEW IF EXISTS main."$dependent""""))
          .isLessThan(statements.indexOf("""DROP VIEW IF EXISTS main."$dependency""""))
        assertWithMessage("$label: recreate $dependency before $dependent")
          .that(statements.indexOf(createSql(dependency)))
          .isLessThan(statements.indexOf(createSql(dependent)))
      }
    }
  }

  @Test
  fun tableReusesItsGeneratedViewIdentity() {
    val view = generatedView(name = "books")

    assertThat(ViewTable(view).objectIdentity).isSameInstanceAs(view.identity)
  }

  @Test
  fun createViewsOrdersChainsAndDiamondsBeforeIssuingDdl() {
    val base = generatedView(
      name = "base",
      sources = listOf(tableSource("books"))
    )
    val left = generatedView(
      name = "left",
      sources = listOf(viewSource("base"))
    )
    val right = generatedView(
      name = "right",
      sources = listOf(viewSource("base"))
    )
    val top = generatedView(
      name = "top",
      sources = listOf(viewSource("left"), viewSource("right"))
    )
    val independent = generatedView(
      name = "independent",
      sources = listOf(tableSource("authors"))
    )
    val database = mock<SupportSQLiteDatabase>()

    SqlUtil.createViews(
      db = database,
      views = listOf(top, independent, right, left, base, base),
      temporary = false
    )

    inOrder(database) {
      verify(database)
        .execSQL(createSql("base"))
      verify(database)
        .execSQL(createSql("left"))
      verify(database)
        .execSQL(createSql("right"))
      verify(database)
        .execSQL(createSql("top"))
      verify(database)
        .execSQL(createSql("independent"))
      verifyNoMoreInteractions()
    }
  }

  @Test
  fun recreateViewsDropsDependentsFirstAndCreatesDependenciesFirst() {
    val base = generatedView(
      name = "base",
      sources = listOf(tableSource("books"))
    )
    val middle = generatedView(
      name = "middle",
      sources = listOf(viewSource("base"))
    )
    val top = generatedView(
      name = "top",
      sources = listOf(viewSource("middle"))
    )
    val database = mock<SupportSQLiteDatabase>()

    SqlUtil.recreateViews(
      db = database,
      views = listOf(top, middle, base)
    )

    inOrder(database) {
      verify(database)
        .execSQL("""DROP VIEW IF EXISTS main."top"""")
      verify(database)
        .execSQL("""DROP VIEW IF EXISTS main."middle"""")
      verify(database)
        .execSQL("""DROP VIEW IF EXISTS main."base"""")
      verify(database)
        .execSQL(createSql("base"))
      verify(database)
        .execSQL(createSql("middle"))
      verify(database)
        .execSQL(createSql("top"))
      verifyNoMoreInteractions()
    }
  }

  @Test
  fun acyclicStaticQueryInitializationDoesNotWaitForAnotherViewsProvider() {
    val initializerStarted = CountDownLatch(1)
    val releaseInitializer = CountDownLatch(1)
    val providerStarted = CountDownLatch(1)
    StaticAcyclicState.base = generatedView(
      name = "base",
      sources = listOf(tableSource("books"))
    )
    StaticAcyclicState.initializerStarted = initializerStarted
    StaticAcyclicState.releaseInitializer = releaseInitializer
    StaticAcyclicState.outer = generatedView(name = "outer") {
      providerStarted.countDown()
      SqlUtil.viewDefinition(
        query = StaticAcyclicQueryOwner.query,
        viewName = "outer"
      )
    }
    val executor = Executors.newFixedThreadPool(2) { task ->
      Thread(task)
        .apply { isDaemon = true }
    }
    try {
      val directResult = executor.submit<Any> { StaticAcyclicQueryOwner.query }
      check(initializerStarted.await(5, SECONDS))
      val definitionResult = executor.submit<ViewDefinition> { StaticAcyclicState.outer.definition }
      check(providerStarted.await(5, SECONDS))
      releaseInitializer.countDown()

      assertThat(directResult.get(5, SECONDS)).isNotNull()
      assertThat(definitionResult.get(5, SECONDS).queryDependencies.directSources)
        .containsExactly(viewSource("base"))
    } finally {
      releaseInitializer.countDown()
      executor.shutdownNow()
    }
  }

  @Test
  fun concurrentDefinitionsOfOneViewCanShareAnUnresolvedDependency() {
    val firstProviderStarted = CountDownLatch(1)
    val releaseFirstProvider = CountDownLatch(1)
    val dependencyProviderStarted = CountDownLatch(1)
    val contenderProviderStarted = CountDownLatch(1)
    val releaseDependency = CountDownLatch(1)
    val viewProviderCalls = AtomicInteger()
    val dependencyProviderCalls = AtomicInteger()

    fun definition() = ViewDefinition(
      sql = "SELECT 1",
      args = null,
      queryDependencies = QueryDependencies.Builder().build(),
      columns = null,
      tableGraphNodeNames = null,
      queryDeep = false
    )

    val dependency = generatedView(name = "dependency") {
      when (dependencyProviderCalls.incrementAndGet()) {
        1 -> dependencyProviderStarted.countDown()
        2 -> contenderProviderStarted.countDown()
      }
      check(releaseDependency.await(10, SECONDS))
      definition()
    }
    val view = generatedView(name = "view") {
      if (viewProviderCalls.incrementAndGet() == 1) {
        firstProviderStarted.countDown()
        check(releaseFirstProvider.await(10, SECONDS))
      }
      dependency.definition
      definition()
    }
    val executor = Executors.newFixedThreadPool(2) { task ->
      Thread(task)
        .apply { isDaemon = true }
    }
    try {
      val firstResult = executor.submit<ViewDefinition> { view.definition }
      check(firstProviderStarted.await(5, SECONDS))
      val secondResult = executor.submit<ViewDefinition> { view.definition }
      check(dependencyProviderStarted.await(5, SECONDS))
      releaseFirstProvider.countDown()
      check(contenderProviderStarted.await(5, SECONDS))
      releaseDependency.countDown()

      assertThat(firstResult.get(5, SECONDS)).isSameInstanceAs(secondResult.get(5, SECONDS))
    } finally {
      releaseFirstProvider.countDown()
      releaseDependency.countDown()
      executor.shutdownNow()
    }
  }

  @Test
  fun directSelfCycleAndMetadataGraphCycleFailBeforeDdl() {
    val self = generatedView(
      name = "self",
      sources = listOf(viewSource("self"))
    )
    val first = generatedView(
      name = "first",
      sources = listOf(viewSource("second"))
    )
    val second = generatedView(
      name = "second",
      sources = listOf(viewSource("first"))
    )
    val cases = listOf(
      "main.self -> main.self" to listOf(self),
      "main.first -> main.second -> main.first" to listOf(first, second)
    )

    for ((path, views) in cases) {
      val database = mock<SupportSQLiteDatabase>()
      val failure = assertThrows(IllegalStateException::class.java) {
        SqlUtil.createViews(
          db = database,
          views = views,
          temporary = false
        )
      }
      assertThat(failure)
        .hasMessageThat()
        .contains(path)
      verifyNoInteractions(database)
    }
  }

  @Test
  fun metadataCycleReportsOnlyTheCyclicSuffixOfThePath() {
    val root = generatedView(
      name = "root",
      sources = listOf(viewSource("first"))
    )
    val first = generatedView(
      name = "first",
      sources = listOf(viewSource("second"))
    )
    val second = generatedView(
      name = "second",
      sources = listOf(viewSource("first"))
    )
    val database = mock<SupportSQLiteDatabase>()

    val failure = assertThrows(IllegalStateException::class.java) {
      SqlUtil.createViews(
        db = database,
        views = listOf(root, first, second),
        temporary = false
      )
    }

    assertThat(failure)
      .hasMessageThat()
      .isEqualTo("Generated view dependency cycle: main.first -> main.second -> main.first")
    verifyNoInteractions(database)
  }

  @Test
  fun rejectsMissingSameSchemaViewBeforeDdl() {
    val missing = generatedView(
      name = "outer",
      sources = listOf(viewSource("inner"))
    )
    val database = mock<SupportSQLiteDatabase>()

    val failure = assertThrows(IllegalArgumentException::class.java) {
      SqlUtil.createViews(
        db = database,
        views = listOf(missing),
        temporary = false
      )
    }

    assertThat(failure)
      .hasMessageThat()
      .contains("unregistered generated view")
    verifyNoInteractions(database)
  }

  @Test
  fun validatesEveryDefinitionBeforeAnyDdl() {
    val valid = generatedView(
      name = "valid",
      sources = listOf(tableSource("books"))
    )
    val incomplete = generatedView(
      name = "incomplete",
      sources = listOf(tableSource("authors")),
      complete = false
    )
    val bound = generatedView(
      name = "bound",
      sources = listOf(tableSource("authors")),
      args = arrayOf("argument")
    )
    val cases = listOf(
      listOf(valid, incomplete) to "incomplete direct sources",
      listOf(valid, bound) to "bound arguments"
    )

    for ((views, message) in cases) {
      val database = mock<SupportSQLiteDatabase>()
      val failure = assertThrows(IllegalArgumentException::class.java) {
        SqlUtil.createViews(
          db = database,
          views = views,
          temporary = false
        )
      }
      assertThat(failure)
        .hasMessageThat()
        .contains(message)
      verifyNoInteractions(database)
    }
  }

  @Test
  fun temporaryBatchRejectsIncompleteSourcesBeforeAnyDdl() {
    val valid = generatedView(
      name = "valid",
      temporary = true,
      sources = listOf(tableSource("books"))
    )
    val incomplete = generatedView(
      name = "incomplete",
      temporary = true,
      complete = false
    )
    val database = mock<SupportSQLiteDatabase>()

    val failure = assertThrows(IllegalArgumentException::class.java) {
      SqlUtil.createViews(
        db = database,
        views = listOf(valid, incomplete),
        temporary = true
      )
    }

    assertThat(failure)
      .hasMessageThat()
      .contains("incomplete direct sources")
    verifyNoInteractions(database)
  }

  @Test
  fun persistentViewsRejectTemporaryTablesAndViewsWhileTemporaryViewsAcceptBothSchemas() {
    val tempTable = generatedView(
      name = "bad_table",
      sources = listOf(
        tableSource(
          name = "drafts",
          temporary = true
        )
      )
    )
    val tempView = generatedView(
      name = "draft_view",
      temporary = true
    )
    val badView = generatedView(
      name = "bad_view",
      sources = listOf(
        viewSource(
          name = "draft_view",
          temporary = true
        )
      )
    )
    for (view in listOf(tempTable, badView)) {
      val database = mock<SupportSQLiteDatabase>()
      val failure = assertThrows(IllegalArgumentException::class.java) {
        SqlUtil.createViews(
          db = database,
          views = listOf(view, tempView),
          temporary = false
        )
      }
      assertThat(failure)
        .hasMessageThat()
        .contains("temporary")
      verifyNoInteractions(database)
    }

    val main = generatedView(
      name = "main_view",
      sources = listOf(tableSource("books"))
    )
    val temporary = generatedView(
      name = "temp_view",
      temporary = true,
      sources = listOf(
        viewSource("main_view"),
        tableSource(
          name = "drafts",
          temporary = true
        ),
        viewSource(
          name = "draft_view",
          temporary = true
        )
      )
    )
    val database = mock<SupportSQLiteDatabase>()
    SqlUtil.createViews(
      db = database,
      views = listOf(temporary, tempView, main),
      temporary = true
    )
    inOrder(database) {
      verify(database)
        .execSQL(
          createSql(
            name = "draft_view",
            temporary = true
          )
        )
      verify(database)
        .execSQL(
          createSql(
            name = "temp_view",
            temporary = true
          )
        )
      verifyNoMoreInteractions()
    }
  }

  @Test
  fun mainAndTemporaryViewsWithTheSameNormalizedNameRemainDistinct() {
    val main = generatedView(
      name = "Report",
      sources = listOf(tableSource("books"))
    )
    val temporary = generatedView(
      name = "REPORT",
      temporary = true,
      sources = listOf(viewSource("report"))
    )
    val database = mock<SupportSQLiteDatabase>()

    SqlUtil.createViews(
      db = database,
      views = listOf(temporary, main),
      temporary = true
    )

    inOrder(database) {
      verify(database)
        .execSQL(
          createSql(
            name = "REPORT",
            temporary = true
          )
        )
      verifyNoMoreInteractions()
    }
  }

  @Test
  fun temporaryViewRejectsAnUnregisteredMainViewBeforeDdl() {
    val database = mock<SupportSQLiteDatabase>()
    val temporary = generatedView(
      name = "session",
      temporary = true,
      sources = listOf(viewSource("missing_main"))
    )

    val failure = assertThrows(IllegalArgumentException::class.java) {
      SqlUtil.createViews(
        db = database,
        views = listOf(temporary),
        temporary = true
      )
    }

    assertThat(failure)
      .hasMessageThat()
      .contains("main.missing_main")
    verifyNoInteractions(database)
  }

  @Test
  fun recreatedViewsIgnoreTemporaryDescriptorsInTheCompleteRegistry() {
    val database = mock<SupportSQLiteDatabase>()
    val temporary = generatedView(
      name = "session",
      temporary = true
    )
    val persistent = generatedView(
      name = "report",
      sources = listOf(tableSource("books"))
    )

    SqlUtil.recreateViews(
      db = database,
      views = listOf(temporary, persistent)
    )

    inOrder(database) {
      verify(database)
        .execSQL("""DROP VIEW IF EXISTS main."report"""")
      verify(database)
        .execSQL(createSql("report"))
      verifyNoMoreInteractions()
    }
  }

  @Test
  fun compiledViewSourcesKeepDirectIdentitySeparateFromTransitiveObservation() {
    val base = generatedView(name = "base") {
      compiledDefinition(
        name = "base",
        source = testTable(
          name = "books",
          mapper = { _, _, _ -> Query.Mapper { Any() } }
        )
      )
    }
    val outer = generatedView(name = "outer") {
      compiledDefinition(
        name = "outer",
        source = ViewTable(base)
      )
    }
    val originalBaseDependencies = base.definition.queryDependencies
    val compiled = Select
      .from(ViewTable(outer))
      .compile() as CompiledSelectDetails

    assertThat(compiled.queryDependencies.directSources)
      .containsExactly(viewSource("outer"))
    assertThat(compiled.observedTables.asList())
      .containsExactly("books")
    assertThat(outer.definition.queryDependencies.directSources)
      .containsExactly(viewSource("base"))
    assertThat(originalBaseDependencies.directSources)
      .containsExactly(tableSource("books"))
    assertThat(base.definition.queryDependencies)
      .isSameInstanceAs(originalBaseDependencies)
  }

  private fun generatedView(
    name: String,
    temporary: Boolean = false,
    sources: List<SqliteQuerySource> = emptyList(),
    complete: Boolean = true,
    args: Array<String?>? = null
  ) = generatedView(
    name = name,
    temporary = temporary,
    definitionProvider = {
      val dependencies = QueryDependencies.Builder()
      sources.forEach(dependencies::addSource)
      if (!complete) dependencies.markDirectSourcesIncomplete()
      ViewDefinition(
        sql = "SELECT 1",
        args = args,
        queryDependencies = dependencies.build(),
        columns = null,
        tableGraphNodeNames = null,
        queryDeep = false
      )
    }
  )

  private fun generatedView(
    name: String,
    temporary: Boolean = false,
    definitionProvider: () -> ViewDefinition
  ) = GeneratedView(
    viewName = name,
    temporary = temporary,
    definitionProvider = definitionProvider
  )

  private fun compiledDefinition(
    name: String,
    source: Table<Any>
  ) = SqlUtil.viewDefinition(
    query = Select.from(source)
      .compile(),
    viewName = name
  )

  private fun tableSource(
    name: String,
    temporary: Boolean = false
  ) = SqliteQuerySource(
    schema = when {
      temporary -> TEMPORARY
      else -> MAIN
    },
    name = name,
    kind = TABLE
  )

  private fun viewSource(
    name: String,
    temporary: Boolean = false
  ) = SqliteQuerySource(
    schema = when {
      temporary -> TEMPORARY
      else -> MAIN
    },
    name = name,
    kind = VIEW
  )

  private fun createSql(
    name: String,
    temporary: Boolean = false
  ) = when {
    temporary -> """CREATE TEMPORARY VIEW IF NOT EXISTS "$name" AS SELECT 1"""
    else -> """CREATE VIEW IF NOT EXISTS "$name" AS SELECT 1"""
  }

  private class ViewTable(view: GeneratedView) : Table<Any>(
    name = view.viewName,
    alias = null,
    nrOfColumns = 1,
    mapper = { _, _, _ -> Query.Mapper { Any() } },
    generatedView = view,
    temporary = view.temporary
  )

  private object StaticAcyclicState {
    lateinit var outer: GeneratedView
    lateinit var base: GeneratedView
    lateinit var initializerStarted: CountDownLatch
    lateinit var releaseInitializer: CountDownLatch
  }

  private object StaticAcyclicQueryOwner {
    val query = run {
      StaticAcyclicState.initializerStarted.countDown()
      check(StaticAcyclicState.releaseInitializer.await(10, SECONDS))
      Select.from(ViewTable(StaticAcyclicState.base)).compile()
    }
  }
}
