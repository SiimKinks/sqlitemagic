package com.siimkinks.sqlitemagic.runtime.support

import io.reactivex.observers.TestObserver

internal class SuccessiveTraversalIterable<T>(
  private val traversalBatches: List<List<T>>
) : Iterable<T> {
  var traversalCount = 0
    private set

  override fun iterator(): Iterator<T> {
    traversalCount += 1
    return traversalBatches
      .getOrNull(traversalCount - 1)
      ?.iterator()
      ?: error("Unexpected traversal: $traversalCount")
  }
}

internal fun <T> disposeAfterFirst(
  values: List<T>,
  observer: TestObserver<Void>
) = object : AbstractList<T>() {
  override val size get() = values.size

  override fun get(index: Int) = values[index]

  override fun iterator() = object : Iterator<T> {
    private var index = 0

    override fun hasNext(): Boolean {
      if (index == 1) {
        observer.dispose()
      }
      return index < values.size
    }

    override fun next() = values[index++]
  }
}
