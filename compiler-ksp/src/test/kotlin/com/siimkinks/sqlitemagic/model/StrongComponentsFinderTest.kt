package com.siimkinks.sqlitemagic.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import org.junit.jupiter.api.Test

internal class StrongComponentsFinderTest {
  @Test
  fun `empty graph has no strong components`() {
    val finder = StrongComponentsFinder(emptyList())

    assertThat(finder.strongComponents)
      .isEmpty()
    assertThat(finder.hasStrongComponents)
      .isFalse()
  }

  @Test
  fun `acyclic graph has no strong components`() {
    val third = table("Third")
    val second = table("Second", edge("Third"))
    val first = table("First", edge("Second"), edge("Third"))
    val finder = StrongComponentsFinder(listOf(first, second, third))

    assertThat(finder.strongComponents)
      .isEmpty()
    assertThat(finder.hasStrongComponents)
      .isFalse()
  }

  @Test
  fun `non-recursive and unresolved references do not create graph edges`() {
    val first = table("First", ignoredEdge(), edge("Missing"), nonReference())
    val second = table("Second", edge("First"))
    val finder = StrongComponentsFinder(listOf(first, second))

    assertThat(finder.strongComponents)
      .isEmpty()
    assertThat(finder.hasStrongComponents)
      .isFalse()
  }

  @Test
  fun `recursive edge survives duplicate filtering regardless of column order`() {
    val second = table("Second", edge("First"))
    val ignoredFirst = table("First", ignoredEdge(), edge("Second"))
    val recursiveFirst = table("First", edge("Second"), ignoredEdge())

    assertWithMessage("ignored edge before recursive edge")
      .that(StrongComponentsFinder(listOf(ignoredFirst, second)).strongComponents)
      .containsExactly(listOf(ignoredFirst, second))

    assertWithMessage("recursive edge before ignored edge")
      .that(StrongComponentsFinder(listOf(recursiveFirst, second)).strongComponents)
      .containsExactly(listOf(recursiveFirst, second))
  }

  @Test
  fun `self-reference is reported as a strong component`() {
    val node = table("Node", edge("Node"))
    val finder = StrongComponentsFinder(listOf(node))

    assertThat(finder.strongComponents)
      .containsExactly(listOf(node))
    assertThat(finder.hasStrongComponents)
      .isTrue()
    assertThat(finder.diagnostic)
      .isEqualTo(expectedDiagnostic("Node-Node"))
  }

  @Test
  fun `self-reference remains separate from incoming and outgoing tables`() {
    val incoming = table("Incoming", edge("Node"))
    val node = table("Node", edge("Outgoing"), edge("Node"), edge("Node"))
    val outgoing = table("Outgoing")
    val finder = StrongComponentsFinder(listOf(incoming, node, outgoing))

    assertThat(finder.strongComponents)
      .containsExactly(listOf(node))
    assertThat(finder.diagnostic)
      .isEqualTo(expectedDiagnostic("Node-Node"))
  }

  @Test
  fun `component contains only mutually reachable tables`() {
    val incoming = table("Incoming", edge("First"))
    val first = table("First", edge("Second"))
    val second = table("Second", edge("Third"))
    val third = table("Third", edge("First"), edge("Outgoing"))
    val outgoing = table("Outgoing")
    val finder = StrongComponentsFinder(
      listOf(incoming, first, second, third, outgoing)
    )

    assertThat(finder.strongComponents)
      .containsExactly(listOf(first, second, third))
    assertThat(finder.diagnostic)
      .isEqualTo(expectedDiagnostic("First-Second-Third"))
  }

  @Test
  fun `duplicate edges do not duplicate component members`() {
    val first = table("First", edge("Second"), edge("Second"), edge("Second"))
    val second = table("Second", edge("First"), edge("First"))
    val finder = StrongComponentsFinder(listOf(first, second))

    assertThat(finder.strongComponents)
      .containsExactly(listOf(first, second))
    assertThat(finder.diagnostic)
      .isEqualTo(expectedDiagnostic("First-Second"))
  }

  @Test
  fun `separate components and their members follow table input order`() {
    val third = table("Third", edge("First"))
    val first = table("First", edge("Second"))
    val second = table("Second", edge("Fourth"), edge("Third"))
    val fourth = table("Fourth", edge("Fifth"))
    val fifth = table("Fifth", edge("Fourth"))
    val self = table("Self", edge("Self"))
    val finder = StrongComponentsFinder(
      listOf(third, first, second, fourth, fifth, self)
    )

    assertThat(finder.strongComponents)
      .containsExactly(
        listOf(third, first, second),
        listOf(fourth, fifth),
        listOf(self)
      )
      .inOrder()
    assertThat(finder.diagnostic)
      .isEqualTo(
        expectedDiagnostic(
          "Third-First-Second",
          "Fourth-Fifth",
          "Self-Self"
        )
      )
  }

  @Test
  fun `all three-table directed graphs match reachability components`() {
    val tableNames = listOf("First", "Second", "Third")
    val vertexCount = tableNames.size
    val graphCount = 1 shl (vertexCount * vertexCount)

    for (graph in 0 until graphCount) {
      val tables = tableNames.mapIndexed { source, tableName ->
        val edges = buildList {
          for (target in tableNames.indices.reversed()) {
            when {
              graph.hasEdge(
                vertexCount = vertexCount,
                source = source,
                target = target
              ) -> add(edge(tableNames[target]))
            }
          }
        }
        table(tableName, *edges.toTypedArray())
      }
      val expected = expectedStrongComponents(
        tables = tables,
        graph = graph
      )

      assertWithMessage("strong components for graph mask $graph")
        .that(StrongComponentsFinder(tables).strongComponents)
        .isEqualTo(expected)
    }
  }
}

private data class TestEdge(
  val targetName: String?,
  val isHandledRecursively: Boolean
)

private fun edge(targetName: String) = TestEdge(
  targetName = targetName,
  isHandledRecursively = true
)

private fun ignoredEdge() = TestEdge(
  targetName = "Ignored",
  isHandledRecursively = false
)

private fun nonReference() = TestEdge(
  targetName = null,
  isHandledRecursively = true
)

private fun table(
  name: String,
  vararg edges: TestEdge
): TableElement {
  val idColumn = column(
    targetName = null,
    isId = true,
    isHandledRecursively = false
  )
  val relationshipColumns = edges.map { edge ->
    column(
      targetName = edge.targetName,
      isId = false,
      isHandledRecursively = edge.isHandledRecursively
    )
  }
  return mockTableElement(
    modelName = name,
    allColumns = relationshipColumns + idColumn
  )
}

private fun column(
  targetName: String?,
  isId: Boolean,
  isHandledRecursively: Boolean
): ColumnElement = mockColumnElement(
  referencedTableTypeKey = targetName?.let { "$PACKAGE.$it" },
  isId = isId,
  isHandledRecursively = isHandledRecursively
)

private fun expectedDiagnostic(vararg components: String) = buildString {
  append("VALIDATION ERROR:\n")
  append("\tTable graph validation failed: Tables cannot have reference cycles.\n")
  append("\tFound cycles:\n")
  for (component in components) {
    append("\t\t")
    append(component)
    append('\n')
  }
  append("\tPossible fix: remove some complex columns or annotate them with @Column(handleRecursively = false)")
}

/**
 * Finds SCCs through mutual transitive reachability using the Floyd-Warshall algorithm,
 * independently calculating the expected result.
 */
private fun expectedStrongComponents(
  tables: List<TableElement>,
  graph: Int
): List<List<TableElement>> {
  val vertexCount = tables.size
  val reachable = Array(vertexCount) { source ->
    BooleanArray(vertexCount) { target ->
      source == target || graph.hasEdge(
        vertexCount = vertexCount,
        source = source,
        target = target
      )
    }
  }
  for (intermediate in 0 until vertexCount) {
    for (source in 0 until vertexCount) {
      if (!reachable[source][intermediate]) continue
      for (target in 0 until vertexCount) {
        if (reachable[intermediate][target]) {
          reachable[source][target] = true
        }
      }
    }
  }

  val assigned = BooleanArray(vertexCount)
  return buildList {
    for (vertex in 0 until vertexCount) {
      if (assigned[vertex]) continue
      val componentIndexes = (0 until vertexCount).filter { candidate ->
        reachable[vertex][candidate] && reachable[candidate][vertex]
      }
      for (member in componentIndexes) {
        assigned[member] = true
      }
      when {
        componentIndexes.size > 1 || graph.hasEdge(
          vertexCount = vertexCount,
          source = vertex,
          target = vertex
        ) -> add(componentIndexes.map(tables::get))
      }
    }
  }
}

private fun Int.hasEdge(
  vertexCount: Int,
  source: Int,
  target: Int
) = this and (1 shl (source * vertexCount + target)) != 0
