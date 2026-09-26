package com.siimkinks.sqlitemagic.fixture.view

import com.siimkinks.sqlitemagic.AS
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.ViewObservationDeepestTable.Companion.VIEW_OBSERVATION_DEEPEST
import com.siimkinks.sqlitemagic.ViewObservationFilterTable.Companion.VIEW_OBSERVATION_FILTER
import com.siimkinks.sqlitemagic.ViewObservationJoinTable.Companion.VIEW_OBSERVATION_JOIN
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewQuery

@Table
data class ViewObservationJoin(
  @Id(autoIncrement = false) val id: Long,
  val authorId: Long
)

@Table
data class ViewObservationFilter(
  @Id(autoIncrement = false) val id: Long,
  val name: String
)

@Table
data class ViewObservationDeepest(
  @Id(autoIncrement = false) val id: Long,
  val name: String
)

@View("view_observation_deep")
data class ViewObservationDeep(
  @ViewColumn("query_composition_author.id") val id: Long,
  @ViewColumn("query_composition_author.name") val name: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(
        QUERY_COMPOSITION_AUTHOR.ID,
        QUERY_COMPOSITION_AUTHOR.NAME
      )
      .from(QUERY_COMPOSITION_AUTHOR)
      .where(
        QUERY_COMPOSITION_AUTHOR.NAME IS Select
          .column(VIEW_OBSERVATION_FILTER.NAME)
          .from(VIEW_OBSERVATION_FILTER)
          .where(
            VIEW_OBSERVATION_FILTER.NAME IS Select
              .column(VIEW_OBSERVATION_DEEPEST.NAME)
              .from(VIEW_OBSERVATION_DEEPEST)
          )
      )
      .compile()
  }
}

@View("view_observation_dependencies")
data class ViewObservationDependencies(
  @ViewColumn("query_composition_author.id") val id: Long,
  @ViewColumn("query_composition_author.name") val name: String
) {
  companion object {
    private val joined = VIEW_OBSERVATION_JOIN AS "joined_dependency"

    @ViewQuery
    val query = Select
      .columns(
        QUERY_COMPOSITION_AUTHOR.ID,
        QUERY_COMPOSITION_AUTHOR.NAME
      )
      .from(QUERY_COMPOSITION_AUTHOR)
      .innerJoin(joined.on(QUERY_COMPOSITION_AUTHOR.ID IS joined.AUTHOR_ID))
      .where(
        QUERY_COMPOSITION_AUTHOR.NAME IS Select
          .column(VIEW_OBSERVATION_FILTER.NAME)
          .from(VIEW_OBSERVATION_FILTER)
      )
      .compile()
  }
}

@View("view_observation_inner")
data class ViewObservationInner(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(VIEW_OBSERVATION_FILTER.NAME)
      .from(VIEW_OBSERVATION_FILTER)
      .compile()
  }
}

@View("view_observation_outer")
data class ViewObservationOuter(
  @ViewColumn("inner") val inner: ViewObservationInner
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(QUERY_COMPOSITION_AUTHOR.NAME AS "inner.name")
      .from(QUERY_COMPOSITION_AUTHOR)
      .compile()
  }
}
