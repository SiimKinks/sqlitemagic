package com.siimkinks.sqlitemagic

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqliteObjectKind.TABLE
import com.siimkinks.sqlitemagic.SqliteObjectKind.VIEW
import com.siimkinks.sqlitemagic.internal.GeneratedView
import com.siimkinks.sqlitemagic.internal.SqlUtil.createViews
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import com.siimkinks.sqlitemagic.internal.ViewDefinition
import com.siimkinks.sqlitemagic.internal.parameterEncoding
import com.siimkinks.sqlitemagic.internal.sql.compileViewDefinition
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

internal class GeneratedViewResolverTest {
  @Test
  fun `registration permutations create every view once after its dependencies`() {
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
      "interleaved" to listOf(left, independent, top, base, right),
      "dependents first with a repeated registration" to listOf(top, independent, right, left, base, base)
    )
    val names = listOf("base", "left", "right", "top", "independent")
    val edges = listOf("base" to "left", "base" to "right", "left" to "top", "right" to "top")
    val expectedCreates = names.map(::createSql)

    for ((label, registry) in cases) {
      val statements = mutableListOf<String>()
      val database = mock<SupportSQLiteDatabase>()
      doAnswer {
        statements += it.getArgument<String>(0)
      }.whenever(database)
        .execSQL(any())

      createViews(
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
    }
  }

  @Test
  fun `table reuses its generated view identity`() {
    val view = generatedView(name = "books")

    assertThat(ViewTable(view).objectIdentity).isSameInstanceAs(view.identity)
  }

  @Test
  fun `acyclic static query initialization does not wait for another view's provider`() {
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
      compileViewDefinition(
        query = StaticAcyclicQueryOwner.query,
        viewName = "outer",
        columns = listOf("base.id"),
        blocks = emptyList()
      )
    }
    withWorkers { executor ->
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
      }
    }
  }

  @Test
  fun `concurrent definitions of one view can share an unresolved dependency`() {
    val firstProviderStarted = CountDownLatch(1)
    val releaseFirstProvider = CountDownLatch(1)
    val dependencyProviderStarted = CountDownLatch(1)
    val contenderProviderStarted = CountDownLatch(1)
    val releaseDependency = CountDownLatch(1)
    val viewProviderCalls = AtomicInteger()
    val dependencyProviderCalls = AtomicInteger()

    fun definition() = ViewDefinition(
      sql = "SELECT 1",
      queryDependencies = QueryDependencies.Builder().build(),
      columnNames = emptyArray(),
      blocks = null
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
    withWorkers { executor ->
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
      }
    }
  }

  @Test
  fun `dependency cycles fail before DDL with only their cyclic path`() {
    val self = generatedView(
      name = "self",
      sources = listOf(viewSource("self"))
    )
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
    val cases = listOf(
      "direct self cycle" to listOf(self),
      "cycle reached from a root" to listOf(root, first, second)
    )

    assertThat(
      cases.map { (label, views) ->
        label to failureBeforeDdl<IllegalStateException>(
          views = views,
          temporary = false
        )
      }
    ).isEqualTo(
      listOf(
        "direct self cycle" to "Generated view dependency cycle: main.self -> main.self",
        "cycle reached from a root" to "Generated view dependency cycle: main.first -> main.second -> main.first"
      )
    )
  }

  @Test
  fun `unregistered view dependencies fail before DDL`() {
    val persistent = generatedView(
      name = "outer",
      sources = listOf(viewSource("inner"))
    )
    val temporary = generatedView(
      name = "session",
      temporary = true,
      sources = listOf(viewSource("missing_main"))
    )

    assertThat(
      listOf(
        "missing same schema view" to failureBeforeDdl<IllegalArgumentException>(
          views = listOf(persistent),
          temporary = false
        ),
        "temporary view missing a main view" to failureBeforeDdl<IllegalArgumentException>(
          views = listOf(temporary),
          temporary = true
        )
      )
    ).isEqualTo(
      listOf(
        "missing same schema view" to "Generated view 'outer' references unregistered generated view 'main.inner'",
        "temporary view missing a main view" to
            "Generated view 'session' references unregistered generated view 'main.missing_main'"
      )
    )
  }

  @Test
  fun `persistent views reject temporary tables and views while temporary views accept both schemas`() {
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
    assertThat(
      listOf(tempTable, badView).map { view ->
        failureBeforeDdl<IllegalArgumentException>(
          views = listOf(view, tempView),
          temporary = false
        )
      }
    ).isEqualTo(
      listOf(
        "Cannot create persistent view 'bad_table': defining query references temporary table 'drafts'",
        "Cannot create persistent view 'bad_view': defining query references temporary view 'draft_view'"
      )
    )

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
    createViews(
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
  fun `main and temporary views with the same normalized name remain distinct`() {
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

    createViews(
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
  fun `persistent view creation ignores temporary descriptors in the complete registry`() {
    val database = mock<SupportSQLiteDatabase>()
    val temporary = generatedView(
      name = "session",
      temporary = true
    )
    val persistent = generatedView(
      name = "report",
      sources = listOf(tableSource("books"))
    )

    createViews(
      db = database,
      views = listOf(temporary, persistent),
      temporary = false
    )

    inOrder(database) {
      verify(database)
        .execSQL(createSql("report"))
      verifyNoMoreInteractions()
    }
  }

  @Test
  fun `compiled view sources keep direct identity separate from transitive observation`() {
    val base = generatedView(name = "base") {
      val books = SourceTable("books")
      compileViewDefinition(
        query = SELECT COLUMN books.ID FROM books,
        viewName = "base",
        columns = listOf("books.id"),
        blocks = emptyList()
      )
    }
    val outer = generatedView(name = "outer") {
      val baseTable = ViewTable(base)
      compileViewDefinition(
        query = SELECT COLUMN baseTable.ID FROM baseTable,
        viewName = "outer",
        columns = listOf("base.id"),
        blocks = emptyList()
      )
    }
    val originalBaseDependencies = base.definition.queryDependencies
    val outerTable = ViewTable(outer)

    assertThat((SELECT COLUMN outerTable.ID FROM outerTable).observedTables()).isEqualTo(listOf("books"))
    assertThat(
      listOf(
        dependencySnapshot(outer.definition.queryDependencies),
        dependencySnapshot(originalBaseDependencies)
      )
    ).isEqualTo(
      listOf(
        DependencySnapshot(
          observed = listOf("books"),
          direct = listOf(viewSource("base"))
        ),
        DependencySnapshot(
          observed = listOf("books"),
          direct = listOf(tableSource("books"))
        )
      )
    )
  }

  private inline fun <reified T : Throwable> failureBeforeDdl(
    views: List<GeneratedView>,
    temporary: Boolean
  ): String? {
    val database = mock<SupportSQLiteDatabase>()
    val failure = assertThrows(T::class.java) {
      createViews(
        db = database,
        views = views,
        temporary = temporary
      )
    }
    verifyNoInteractions(database)
    return failure.message
  }

  private fun generatedView(
    name: String,
    temporary: Boolean = false,
    sources: List<SqliteQuerySource> = emptyList()
  ) = generatedView(
    name = name,
    temporary = temporary,
    definitionProvider = {
      val dependencies = QueryDependencies.Builder()
      sources.forEach(dependencies::addSource)
      ViewDefinition(
        sql = "SELECT 1",
        queryDependencies = dependencies.build(),
        columnNames = emptyArray(),
        blocks = null
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

  private fun tableSource(
    name: String,
    temporary: Boolean = false
  ) = SqliteQuerySource(
    schema = schema(temporary),
    name = name,
    kind = TABLE
  )

  private fun viewSource(
    name: String,
    temporary: Boolean = false
  ) = SqliteQuerySource(
    schema = schema(temporary),
    name = name,
    kind = VIEW
  )

  private fun schema(temporary: Boolean) = when {
    temporary -> TEMPORARY
    else -> MAIN
  }

  private fun createSql(
    name: String,
    temporary: Boolean = false
  ) = when {
    temporary -> """CREATE TEMPORARY VIEW IF NOT EXISTS "$name" AS SELECT 1"""
    else -> """CREATE VIEW IF NOT EXISTS "$name" AS SELECT 1"""
  }

  private class SourceTable(name: String) : Table<Any>(
    name = name,
    alias = null
  ) {
    val ID = Column<Any, Long, Required, Long, Long, Required, Long>(
      table = this,
      name = "id",
      nullness = Required,
      parameterEncoding = LONG.parameterEncoding()
    )
  }

  private class ViewTable(view: GeneratedView) : Table<Any>(
    name = view.viewName,
    alias = null,
    generatedView = view,
    temporary = view.temporary
  ) {
    val ID = Column<Any, Long, SqlNullable, Long, Long, Required, Long>(
      table = this,
      name = "base.id",
      nullness = SqlNullable,
      parameterEncoding = LONG.parameterEncoding()
    )
  }

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
      val base = ViewTable(StaticAcyclicState.base)
      SELECT COLUMN base.ID FROM base
    }
  }
}
