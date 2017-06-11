package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table(persistAll = true)
class Magazine {
  @Id
  @JvmField
  var id: Long? = null

  @JvmField
  var name: String? = null

  @Column(onDeleteCascade = true)
  @JvmField
  var author: Author? = null

  @JvmField
  var nrOfReleases: Int? = null
}