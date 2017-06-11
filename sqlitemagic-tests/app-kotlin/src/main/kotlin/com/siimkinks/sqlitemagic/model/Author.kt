package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table
class Author {
  @Id
  @JvmField
  var id: Long? = null
  @Column
  @JvmField
  var name: String? = null
  @Column
  @JvmField
  var boxedBoolean: Boolean? = null
  @Column
  @JvmField
  var primitiveBoolean: Boolean = false
}