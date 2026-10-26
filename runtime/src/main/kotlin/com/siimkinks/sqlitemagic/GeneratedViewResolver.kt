package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.SqliteObjectKind.VIEW
import com.siimkinks.sqlitemagic.internal.GeneratedView
import com.siimkinks.sqlitemagic.internal.MutableObjectIntMap
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import com.siimkinks.sqlitemagic.internal.SqliteSchemaKey

internal class GeneratedViewResolver(views: Collection<GeneratedView>) {
  private val viewsByKey = views.associateBy { it.identity.normalizedKey }

  fun resolve(temporary: Boolean): List<GeneratedView> {
    val nodes = viewsByKey.filterValues { it.temporary == temporary }
    if (!temporary) {
      nodes.values.forEach(::requirePersistentSources)
    }
    val ordered = ArrayList<GeneratedView>(nodes.size)
    val completed = MutableScatterSet<SqliteSchemaKey>(initialCapacity = nodes.size)
    val path = ArrayList<SqliteSchemaKey>()
    val activeIndexByKey = MutableObjectIntMap<SqliteSchemaKey>()

    fun visit(node: GeneratedView) {
      val key = node.identity.normalizedKey
      if (key in completed) return
      activeIndexByKey
        .getOrNull(key)
        ?.let { cycleStart ->
          val cycle = (path.subList(fromIndex = cycleStart, toIndex = path.size) + key)
            .joinToString(separator = " -> ") { cycleKey ->
              val view = nodes.getValue(cycleKey)
              "${view.identity.schema.qualifier}.${view.viewName}"
            }
          error("Generated view dependency cycle: $cycle")
        }
      activeIndexByKey[key] = path.size
      path += key
      for (source in node.definition.queryDependencies.directSources) {
        if (source.kind != VIEW) continue
        val sourceKey = source.identity.normalizedKey
        require(sourceKey in viewsByKey) {
          "Generated view '${node.viewName}' references unregistered generated view " +
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

  private fun requirePersistentSources(node: GeneratedView) {
    node.definition.queryDependencies.directSources
      .firstOrNull { it.schema == TEMPORARY }
      ?.let { source ->
        throw IllegalArgumentException(
          "Cannot create persistent view '${node.viewName}': defining query references temporary " +
              "${source.kind.name.lowercase()} '${source.name}'"
        )
      }
  }
}
