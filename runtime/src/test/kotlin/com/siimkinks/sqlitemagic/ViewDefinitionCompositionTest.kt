package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import org.junit.Test

class ViewDefinitionCompositionTest {
  @Test
  fun rebasesOnlyTheSelectedViewAndKeepsTheDefinitionLazy() {
    val definitionPositions = SimpleArrayMap<String, Int>().apply {
      put("name", 1)
      put("id", 0)
    }
    val definition = ViewDefinition(
      sql = "SELECT id, name FROM authors",
      args = null,
      observedTables = arrayOf("authors"),
      columns = definitionPositions,
      tableGraphNodeNames = null,
      queryDeep = false
    )
    var resolutions = 0
    val view = TestViewTable(
      alias = "left",
      definition = {
        resolutions++
        definition
      }
    )
    assertThat(resolutions).isEqualTo(0)

    val positions = SimpleArrayMap<String, Int>().apply {
      put("left", 3)
      put("tail", 5)
    }
    val observed = arrayListOf<String>()
    view.perfectSelection(
      observedTables = observed,
      tableGraphNodeNames = null,
      columnPositions = positions
    )

    assertThat(resolutions).isEqualTo(1)
    assertThat(positions["left"]).isNull()
    assertThat(positions["left.name"]).isEqualTo(4)
    assertThat(positions["left.id"]).isEqualTo(3)
    assertThat(positions["tail"]).isEqualTo(5)
    assertThat(definitionPositions["name"]).isEqualTo(1)
    assertThat(definitionPositions["id"]).isEqualTo(0)
    assertThat(observed).containsExactly("authors")

    val joinedPositions = SimpleArrayMap<String, Int>()
    view.perfectSelection(
      observedTables = arrayListOf(),
      tableGraphNodeNames = null,
      columnPositions = joinedPositions,
      implicitOffset = 3,
      implicitSelection = true
    )
    assertThat(joinedPositions["left.name"]).isEqualTo(4)
    assertThat(joinedPositions["left.id"]).isEqualTo(3)
  }

  @Test
  fun explicitFullSelectionKeepsPositionalSpanWhenDefinitionHasNoPositions() {
    val definition = ViewDefinition(
      sql = "SELECT * FROM authors",
      args = null,
      observedTables = arrayOf("authors"),
      columns = null,
      tableGraphNodeNames = null,
      queryDeep = false
    )
    val view = TestViewTable(
      alias = null,
      definition = { definition }
    )
    val atZero = Select
      .columns(view.all())
      .from(view)
      .compile() as CompiledSelectImpl<*, *>
    assertThat(atZero.columns?.get("report")).isEqualTo(0)
    assertThat(atZero.columns?.size()).isEqualTo(1)

    val root = testTable(
      name = "authors",
      nrOfColumns = 3,
      mapper = { _, _, _ -> Query.Mapper { Any() } }
    )
    val afterRoot = Select
      .columns(root.all(), view.all())
      .from(root)
      .innerJoin(view)
      .compile() as CompiledSelectImpl<*, *>
    assertThat(afterRoot.columns?.get("authors")).isEqualTo(0)
    assertThat(afterRoot.columns?.get("report")).isEqualTo(3)
    assertThat(afterRoot.columns?.size()).isEqualTo(2)
  }

  @Test
  fun compiledSelectionsRebaseRepeatedAndJoinedViewInstances() {
    val definitionPositions = SimpleArrayMap<String, Int>().apply {
      put("name", 1)
      put("id", 0)
    }
    val definition = ViewDefinition(
      sql = "SELECT id, name FROM authors",
      args = null,
      observedTables = arrayOf("authors"),
      columns = definitionPositions,
      tableGraphNodeNames = null,
      queryDeep = false
    )
    val view = TestViewTable(
      alias = null,
      definition = { definition }
    )
    val left = view.`as`("left")
    val right = view.`as`("right")
    val rootView = Select
      .all()
      .from(view)
      .compile() as CompiledSelectImpl<*, *>
    assertThat(rootView.columns?.get("report.id")).isEqualTo(0)
    assertThat(rootView.columns?.get("report.name")).isEqualTo(1)
    val implicitDefinition = ViewDefinition(
      sql = "SELECT * FROM authors",
      args = null,
      observedTables = arrayOf("authors"),
      columns = null,
      tableGraphNodeNames = null,
      queryDeep = false
    )
    val positionalView = TestViewTable(
      alias = null,
      definition = { implicitDefinition }
    )
    val positional = Select
      .all()
      .from(positionalView)
      .compile() as CompiledSelectImpl<*, *>
    assertThat(positional.columns).isNull()
    val repeated = Select
      .columns(left.all(), right.all())
      .from(left)
      .innerJoin(right)
      .compile() as CompiledSelectImpl<*, *>
    assertThat(repeated.columns?.get("left.id")).isEqualTo(0)
    assertThat(repeated.columns?.get("left.name")).isEqualTo(1)
    assertThat(repeated.columns?.get("right.id")).isEqualTo(2)
    assertThat(repeated.columns?.get("right.name")).isEqualTo(3)
    assertThat(repeated.columns?.get("left")).isNull()
    assertThat(repeated.columns?.get("right")).isNull()

    val root = testTable(
      name = "authors",
      nrOfColumns = 3,
      mapper = { _, _, _ -> Query.Mapper { Any() } }
    )
    val joined = Select
      .all()
      .from(root)
      .innerJoin(right)
      .compile() as CompiledSelectImpl<*, *>
    assertThat(joined.columns).isNull()
    val selectedJoin = Select
      .columns(root.all(), right.all())
      .from(root)
      .innerJoin(right)
      .compile() as CompiledSelectImpl<*, *>
    assertThat(selectedJoin.columns?.get("right.id")).isEqualTo(3)
    assertThat(selectedJoin.columns?.get("right.name")).isEqualTo(4)
    assertThat(definitionPositions["id"]).isEqualTo(0)
    assertThat(definitionPositions["name"]).isEqualTo(1)
  }

  @Test
  fun joinedDeepViewKeepsOuterTableTraversalLocal() {
    val deepDefinition = ViewDefinition(
      sql = "SELECT id, name FROM authors",
      args = null,
      observedTables = arrayOf("authors"),
      columns = SimpleArrayMap<String, Int>().apply {
        put("id", 0)
        put("name", 1)
      },
      tableGraphNodeNames = null,
      queryDeep = true
    )
    val joinedView = TestViewTable(
      alias = null,
      definition = { deepDefinition }
    )
    val mappedModes = arrayListOf<Boolean>()
    val graphModes = arrayListOf<String>()
    val root = testTable(
      name = "books",
      nrOfColumns = 1,
      mapper = { _, _, queryDeep ->
        mappedModes.add(queryDeep)
        Query.Mapper { Any() }
      },
      addDeepQueryParts = { _, _, _ -> graphModes.add("deep") },
      addShallowQueryParts = { _, _, _ -> graphModes.add("shallow") }
    )

    val shallowRoot = Select
      .all()
      .from(root)
      .innerJoin(joinedView)
      .compile() as CompiledSelectImpl<*, *>
    assertThat(shallowRoot.queryDeep).isFalse()
    assertThat(mappedModes).containsExactly(false)
    assertThat(graphModes).containsExactly("shallow")
    assertThat(shallowRoot.observedTables).asList().contains("authors")

    val deepRoot = Select
      .all()
      .from(joinedView)
      .compile() as CompiledSelectImpl<*, *>
    assertThat(deepRoot.queryDeep).isTrue()

    val explicitOuterDeep = Select
      .all()
      .from(root)
      .innerJoin(joinedView)
      .queryDeep()
      .compile() as CompiledSelectImpl<*, *>
    assertThat(explicitOuterDeep.queryDeep).isTrue()
    assertThat(mappedModes).containsExactly(false, true).inOrder()
    assertThat(graphModes).containsExactly("shallow", "deep").inOrder()
  }

  private class TestViewTable(
    alias: String?,
    private val definition: () -> ViewDefinition
  ) : Table<Any>(
    name = "report",
    alias = alias,
    nrOfColumns = 2,
    mapper = { _, _, _ -> Query.Mapper { Any() } },
    viewDefinition = definition
  ) {
    override fun `as`(alias: String) = TestViewTable(
      alias = alias,
      definition = definition
    )
  }
}
