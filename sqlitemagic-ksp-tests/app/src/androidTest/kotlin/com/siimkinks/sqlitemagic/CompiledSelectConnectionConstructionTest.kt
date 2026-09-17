package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import org.junit.Test

class CompiledSelectConnectionConstructionTest {
  @Test
  fun multiColumnSelectConstructionDoesNotResolveDefaultConnection() {
    withoutDefaultConnection {
      Select
        .from(SIMPLE_MUTABLE_ENTITY)
        .compile()
      Select
        .from(SIMPLE_MUTABLE_ENTITY)
        .takeFirst()
      Select
        .from(SIMPLE_MUTABLE_ENTITY)
        .count()
      Select
        .from(SIMPLE_MUTABLE_ENTITY)
        .toCursor()
    }
  }

  @Test
  fun singleColumnSelectConstructionDoesNotResolveDefaultConnection() {
    withoutDefaultConnection {
      Select
        .column(SIMPLE_MUTABLE_ENTITY.VALUE)
        .from(SIMPLE_MUTABLE_ENTITY)
        .compile()
      Select
        .column(SIMPLE_MUTABLE_ENTITY.VALUE)
        .from(SIMPLE_MUTABLE_ENTITY)
        .takeFirst()
      Select
        .column(SIMPLE_MUTABLE_ENTITY.VALUE)
        .from(SIMPLE_MUTABLE_ENTITY)
        .count()
      Select
        .column(SIMPLE_MUTABLE_ENTITY.VALUE)
        .from(SIMPLE_MUTABLE_ENTITY)
        .toCursor()
    }
  }

  @Test
  fun rawSelectCanBeConstructedAndCompiledWithoutDefaultConnection() {
    withoutDefaultConnection {
      Select
        .raw("SELECT * FROM simple_mutable_entity")
        .compile()
      Select
        .raw("SELECT * FROM simple_mutable_entity")
        .from(SIMPLE_MUTABLE_ENTITY)
        .compile()
    }
  }

  private fun withoutDefaultConnection(block: () -> Unit) {
    val instance = SqliteMagic.SingletonHolder.instance
    val previousConnection = instance.defaultConnection
    instance.defaultConnection = null
    try {
      block()
    } finally {
      instance.defaultConnection = previousConnection
    }
  }
}
