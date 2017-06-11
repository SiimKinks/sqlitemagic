package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table(persistAll = true, useAccessMethods = true)
class Magazine {
  @Id
  var id: Long? = null

  var name: String? = null

  @Column(onDeleteCascade = true)
  var author: Author? = null

  var nrOfReleases: Int? = null
}