package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.annotation.Column

/**
 * Modified Tarjan's algorithm for finding strongly-connected components of a graph from
 * <a href="http://algs4.cs.princeton.edu/42digraph/TarjanSCC.java.html">The textbook Algorithms,
 * 4th Edition by Robert Sedgewick and Kevin Wayne</a>
 */
internal class StrongComponentsFinder(
  private val tables: List<TableElement>
) {
  private val graph = createGraph()

  val strongComponents = findStrongComponents()
  val hasStrongComponents get() = strongComponents.isNotEmpty()
  val diagnostic
    get() = buildString {
      append("VALIDATION ERROR:\n")
      append("\tTable graph validation failed: Tables cannot have reference cycles.\n")
      append("\tFound cycles:\n")
      for (component in strongComponents) {
        append("\t\t")
        append(
          when (component.size) {
            1 -> "${component.first().modelName}-${component.first().modelName}"
            else -> component.joinToString(
              separator = "-",
              transform = TableElement::modelName
            )
          }
        )
        append('\n')
      }
      append("\tPossible fix: remove some complex columns or annotate them with @")
      append(Column::class.simpleName)
      append("(handleRecursively = false)")
    }

  private fun createGraph(): Graph {
    val tableIndexes = buildMap {
      for (index in tables.indices) {
        this[tables[index].typeKey] = index
      }
    }
    val hasSelfLoop = BooleanArray(tables.size)
    val seenAtSource = IntArray(tables.size)
    val adjacentVertices = IntArray(tables.size)
    val adjacency = Array(
      size = tables.size,
      init = { source ->
        val sourceMarker = source + 1
        var adjacentCount = 0
        for (column in tables[source].relationshipColumns) {
          if (!column.isHandledRecursively) continue

          val referencedTableTypeKey = checkNotNull(column.referencedTableTypeKey)
          val target = tableIndexes[referencedTableTypeKey] ?: continue

          if (seenAtSource[target] == sourceMarker) continue

          seenAtSource[target] = sourceMarker
          adjacentVertices[adjacentCount] = target
          adjacentCount++
          if (target == source) {
            hasSelfLoop[source] = true
          }
        }
        adjacentVertices.copyOf(adjacentCount)
      }
    )
    return Graph(
      adjacency = adjacency,
      hasSelfLoop = hasSelfLoop
    )
  }

  private fun findStrongComponents(): List<List<TableElement>> {
    val searchResult = TarjanSearch(graph)
      .findStrongComponents()
    val componentPositionsPlusOne = IntArray(searchResult.componentCount)
    val components = mutableListOf<MutableList<TableElement>>()
    for (vertex in tables.indices) {
      val componentId = searchResult.componentIds[vertex]
      if (!searchResult.relevantComponents[componentId]) continue
      when (val componentPositionPlusOne = componentPositionsPlusOne[componentId]) {
        0 -> {
          components.add(mutableListOf(tables[vertex]))
          componentPositionsPlusOne[componentId] = components.size
        }
        else -> components[componentPositionPlusOne - 1].add(tables[vertex])
      }
    }
    return components
  }

  private class TarjanSearch(
    private val graph: Graph
  ) {
    private val adjacencySize = graph.adjacency.size
    private var nextIndex = 1
    private val indexes = IntArray(adjacencySize)
    private val lowLinks = IntArray(adjacencySize)
    private val stack = IntArray(adjacencySize)
    private var stackSize = 0
    private val isOnStack = BooleanArray(adjacencySize)
    private val componentIds = IntArray(adjacencySize)
    private val relevantComponents = BooleanArray(adjacencySize)
    private var componentCount = 0

    fun findStrongComponents(): SearchResult {
      for (vertex in graph.adjacency.indices) {
        if (indexes[vertex] == UNVISITED) {
          visit(vertex)
        }
      }
      return SearchResult(
        componentIds = componentIds,
        relevantComponents = relevantComponents,
        componentCount = componentCount
      )
    }

    private fun visit(vertex: Int) {
      indexes[vertex] = nextIndex
      lowLinks[vertex] = nextIndex
      nextIndex++
      stack[stackSize] = vertex
      stackSize++
      isOnStack[vertex] = true

      for (adjacent in graph.adjacency[vertex]) {
        when {
          indexes[adjacent] == UNVISITED -> {
            visit(adjacent)
            if (lowLinks[adjacent] < lowLinks[vertex]) {
              lowLinks[vertex] = lowLinks[adjacent]
            }
          }
          isOnStack[adjacent] && indexes[adjacent] < lowLinks[vertex] -> {
            lowLinks[vertex] = indexes[adjacent]
          }
        }
      }

      if (lowLinks[vertex] != indexes[vertex]) return

      var componentSize = 0
      var member: Int
      do {
        stackSize--
        member = stack[stackSize]
        isOnStack[member] = false
        componentIds[member] = componentCount
        componentSize++
      } while (member != vertex)
      relevantComponents[componentCount] = componentSize > 1 || graph.hasSelfLoop[vertex]
      componentCount++
    }
  }

  private class SearchResult(
    val componentIds: IntArray,
    val relevantComponents: BooleanArray,
    val componentCount: Int
  )

  private class Graph(
    val adjacency: Array<IntArray>,
    val hasSelfLoop: BooleanArray
  )

  private companion object {
    const val UNVISITED = 0
  }
}
