package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.SqliteObjectKind.VIEW
import com.siimkinks.sqlitemagic.internal.SqliteSchemaKey

/** Resolves a batch before schema DDL starts. */
internal class GeneratedViewResolver(views: Collection<GeneratedView>) {
  private val viewsByKey = views.associateBy { it.identity.normalizedKey }

  fun resolve(temporary: Boolean): List<ResolvedView> {
    val nodes = viewsByKey.values
      .filter { it.temporary == temporary }
      .associateBy(
        keySelector = { it.identity.normalizedKey },
        valueTransform = { view ->
          ResolvedView(
            view = view,
            definition = view.definition
          )
        }
      )
    nodes.values.forEach { node ->
      SqlUtil.validateViewDefinition(
        definition = node.definition,
        viewName = node.view.viewName,
        temporary = node.view.temporary,
        requireCompleteSources = true
      )
    }
    val ordered = ArrayList<ResolvedView>(nodes.size)
    val completed = HashSet<SqliteSchemaKey>()
    val path = ArrayList<SqliteSchemaKey>()
    val activeIndexByKey = HashMap<SqliteSchemaKey, Int>()

    fun visit(node: ResolvedView) {
      val key = node.view.identity.normalizedKey
      if (key in completed) return
      activeIndexByKey[key]?.let { cycleStart ->
        val cycle = (path.subList(cycleStart, path.size) + key)
          .joinToString(separator = " -> ") { cycleKey ->
            val view = nodes.getValue(cycleKey).view
            "${view.identity.schema.qualifier}.${view.viewName}"
          }
        error("Generated view dependency cycle: $cycle")
      }
      activeIndexByKey[key] = path.size
      path += key
      for (source in node.definition.queryDependencies.directSources) {
        if (source.kind != VIEW) continue
        val sourceKey = source.identity.normalizedKey
        requireNotNull(viewsByKey[sourceKey]) {
          "Generated view '${node.view.viewName}' references unregistered generated view " +
              "'${source.schema.qualifier}.${source.name}'"
        }
        nodes[sourceKey]?.let(::visit)
      }
      path.removeAt(path.lastIndex)
      activeIndexByKey.remove(key)
      completed += key
      ordered += node
    }

    nodes.values.forEach(::visit)
    return ordered
  }

  internal class ResolvedView(
      val view: GeneratedView,
      val definition: ViewDefinition
  )
}
