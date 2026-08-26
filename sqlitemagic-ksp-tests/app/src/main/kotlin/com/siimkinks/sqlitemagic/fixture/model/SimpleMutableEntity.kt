package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.Utils
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import java.util.Random

@Table
data class SimpleMutableEntity(
  @Id var id: Long? = null,
  var value: String? = null,
  var boxedBoolean: Boolean? = null,
  var primitiveBoolean: Boolean = false
) {
  fun fillWithRandomValues() {
    val r = Random()
    id = r.nextLong()
    value = Utils.randomTableName()
    boxedBoolean = r.nextBoolean()
    primitiveBoolean = r.nextBoolean()
  }

  companion object {
    fun newRandom() = SimpleMutableEntity()
      .also(SimpleMutableEntity::fillWithRandomValues)
  }
}
