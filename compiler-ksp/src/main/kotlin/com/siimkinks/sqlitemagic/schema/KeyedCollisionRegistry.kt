package com.siimkinks.sqlitemagic.schema

internal class KeyedCollisionRegistry<K, V> {
  private val values = linkedMapOf<K, V>()

  fun lookup(key: K) = values[key]

  fun register(key: K, value: V) = values.putIfAbsent(key, value)

  fun unregister(key: K, value: V): Boolean {
    if (values[key] != value) return false
    values.remove(key)
    return true
  }
}
