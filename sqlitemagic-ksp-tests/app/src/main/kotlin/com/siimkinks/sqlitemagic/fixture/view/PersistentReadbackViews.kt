package com.siimkinks.sqlitemagic.fixture.view

import com.siimkinks.sqlitemagic.AS
import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SubmodulePersistentValueTable.Companion.SUBMODULE_PERSISTENT_VALUE
import com.siimkinks.sqlitemagic.annotation.Embedded
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewQuery

@View("persistent_email_readback")
data class PersistentEmailReadbackView(
  @ViewColumn("email") val email: ReaderEmail
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(QUERY_COMPOSITION_AUTHOR.NAME AS "email")
      .from(QUERY_COMPOSITION_AUTHOR)
      .compile()
  }
}

data class PersistentContact(
  val city: String,
  val zip: Int
)

@View("persistent_embedded_readback")
data class PersistentEmbeddedReadbackView(
  @Embedded(prefix = "contact_") val contact: PersistentContact
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(
        QUERY_COMPOSITION_AUTHOR.NAME AS "contact_city",
        QUERY_COMPOSITION_AUTHOR.ID AS "contact_zip"
      )
      .from(QUERY_COMPOSITION_AUTHOR)
      .compile()
  }
}

@View("persistent_inner_readback")
data class PersistentInnerReadbackView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(QUERY_COMPOSITION_AUTHOR.NAME)
      .from(QUERY_COMPOSITION_AUTHOR)
      .compile()
  }
}

@View("persistent_nested_readback")
data class PersistentNestedReadbackView(
  @ViewColumn("inner") val inner: PersistentInnerReadbackView,
  @ViewColumn("tail") val tail: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(
        QUERY_COMPOSITION_AUTHOR.NAME AS "inner.name",
        QUERY_COMPOSITION_AUTHOR.NAME AS "tail"
      )
      .from(QUERY_COMPOSITION_AUTHOR)
      .compile()
  }
}

@View("persistent_submodule_readback")
data class PersistentSubmoduleReadbackView(
  @ViewColumn("submodule_persistent_value.id") val id: String,
  @ViewColumn("submodule_persistent_value.value") val value: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(
        SUBMODULE_PERSISTENT_VALUE.ID,
        SUBMODULE_PERSISTENT_VALUE.VALUE
      )
      .from(SUBMODULE_PERSISTENT_VALUE)
      .compile()
  }
}
