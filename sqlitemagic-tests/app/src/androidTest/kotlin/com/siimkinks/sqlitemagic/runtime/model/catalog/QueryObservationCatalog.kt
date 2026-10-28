package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SelectQuery
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.StringIdEntityTable.Companion.STRING_ID_ENTITY
import com.siimkinks.sqlitemagic.UPDATE
import com.siimkinks.sqlitemagic.asScalar
import com.siimkinks.sqlitemagic.fixture.model.EntityWithRelationship
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.fixture.model.StringIdEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.queryDeep
import com.siimkinks.sqlitemagic.runtime.support.insertedResult

data class QueryObservationMutation<T>(
  val name: String,
  val mutate: () -> Unit,
  val expected: List<T>
)

data class QueryObservationScenario<T>(
  val query: () -> SelectQuery<T, *>,
  val initial: List<T>,
  val mutations: List<QueryObservationMutation<T>>
)

data class QueryObservationCase<T>(
  val name: String,
  val newScenario: () -> QueryObservationScenario<T>
) {
  override fun toString() = name
}

internal object QueryObservationCatalog {
  val cases = listOf<QueryObservationCase<*>>(
    QueryObservationCase(
      name = "explicit joined tables",
      newScenario = ::newExplicitJoinScenario
    ),
    QueryObservationCase(
      name = "simple scalar subquery in simple outer query",
      newScenario = ::newSimpleScalarSubqueryScenario
    ),
    QueryObservationCase(
      name = "complex relationship subquery in simple outer query",
      newScenario = ::newComplexSubqueryInSimpleOuterScenario
    ),
    QueryObservationCase(
      name = "complex subquery in complex outer query",
      newScenario = ::newComplexSubqueryInComplexOuterScenario
    )
  )

  private fun newExplicitJoinScenario(): QueryObservationScenario<EntityWithRelationship> {
    val related = newSimple(value = "explicit-join-match")
    val parent = newRelationship(
      value = "explicit-join-match",
      related = related,
      count = 11
    )
    Topology.insert(value = parent)
    val parentId = checkNotNull(parent.id)
    val relatedId = checkNotNull(related.id)

    return QueryObservationScenario(
      query = {
        SELECT
          .FROM(Topology.relationshipTable)
          .LEFT_JOIN(Topology.simpleTable)
          .ON(Topology.relationshipTable.RELATED_ENTITY IS Topology.simpleTable.ID)
          .WHERE(Topology.relationshipTable.VALUE IS Topology.simpleTable.VALUE)
          .ORDER_BY(Topology.relationshipTable.ID.ASC())
          .queryDeep()
      },
      initial = listOf(
        expectedRelationship(
          parentId = parentId,
          parentValue = "explicit-join-match",
          relatedId = relatedId,
          relatedValue = "explicit-join-match",
          count = 11
        )
      ),
      mutations = listOf(
        QueryObservationMutation(
          name = "EntityWithRelationship",
          mutate = {
            Topology.updateRelationshipValue(
              value = parent,
              newValue = "explicit-join-updated"
            )
            parent.value = "explicit-join-updated"
          },
          expected = emptyList()
        ),
        QueryObservationMutation(
          name = "SimpleMutableEntity",
          mutate = {
            Topology.updateSimpleValue(
              value = related,
              newValue = "explicit-join-updated"
            )
            related.value = "explicit-join-updated"
          },
          expected = listOf(
            expectedRelationship(
              parentId = parentId,
              parentValue = "explicit-join-updated",
              relatedId = relatedId,
              relatedValue = "explicit-join-updated",
              count = 11
            )
          )
        )
      )
    )
  }

  private fun newSimpleScalarSubqueryScenario(): QueryObservationScenario<Long> {
    val target = StringIdEntity(
      id = "simple-subquery-target",
      value = "simple-subquery-match"
    )
    val outer = newSimple(value = "simple-subquery-match")
    Topology.insert(value = target)
    Topology.insert(value = outer)
    val outerId = checkNotNull(outer.id)

    return QueryObservationScenario(
      query = {
        SELECT
          .COLUMN(Topology.simpleTable.ID)
          .FROM(Topology.simpleTable)
          .WHERE(
            Topology.simpleTable.VALUE IS SELECT
              .COLUMN(Topology.stringTable.VALUE)
              .FROM(Topology.stringTable)
              .asScalar()
          )
          .ORDER_BY(Topology.simpleTable.ID.ASC())
      },
      initial = listOf(outerId),
      mutations = listOf(
        QueryObservationMutation(
          name = "StringIdEntity",
          mutate = {
            Topology.updateStringValue(
              value = target,
              newValue = "simple-subquery-updated"
            )
          },
          expected = emptyList()
        ),
        QueryObservationMutation(
          name = "SimpleMutableEntity",
          mutate = {
            Topology.updateSimpleValue(
              value = outer,
              newValue = "simple-subquery-updated"
            )
            outer.value = "simple-subquery-updated"
          },
          expected = listOf(outerId)
        )
      )
    )
  }

  private fun newComplexSubqueryInSimpleOuterScenario(): QueryObservationScenario<String> {
    val related = newSimple(value = "complex-subquery-match")
    val parent = newRelationship(
      value = "complex-subquery-match",
      related = related,
      count = 17
    )
    val outer = StringIdEntity(
      id = "complex-subquery-outer",
      value = "complex-subquery-match"
    )
    Topology.insert(value = parent)
    Topology.insert(value = outer)

    return QueryObservationScenario(
      query = {
        val subquery = SELECT
          .COLUMN(Topology.stringTable.ID)
          .FROM(Topology.relationshipTable)
          .LEFT_JOIN(Topology.simpleTable)
          .ON(Topology.relationshipTable.RELATED_ENTITY IS Topology.simpleTable.ID)
          .LEFT_JOIN(Topology.stringTable)
          .ON(Topology.stringTable.VALUE IS Topology.simpleTable.VALUE)
          .WHERE(Topology.relationshipTable.VALUE IS Topology.simpleTable.VALUE)

        SELECT
          .COLUMN(Topology.stringTable.ID)
          .FROM(Topology.stringTable)
          .WHERE(Topology.stringTable.ID IS subquery.asScalar())
          .ORDER_BY(Topology.stringTable.ID.ASC())
      },
      initial = listOf(outer.id),
      mutations = listOf(
        QueryObservationMutation(
          name = "EntityWithRelationship",
          mutate = {
            Topology.updateRelationshipValue(
              value = parent,
              newValue = "complex-subquery-updated"
            )
            parent.value = "complex-subquery-updated"
          },
          expected = emptyList()
        ),
        QueryObservationMutation(
          name = "SimpleMutableEntity",
          mutate = {
            Topology.updateSimpleValue(
              value = related,
              newValue = "complex-subquery-updated"
            )
            related.value = "complex-subquery-updated"
          },
          expected = emptyList()
        ),
        QueryObservationMutation(
          name = "StringIdEntity",
          mutate = {
            Topology.updateStringValue(
              value = outer,
              newValue = "complex-subquery-updated"
            )
          },
          expected = listOf(outer.id)
        )
      )
    )
  }

  private fun newComplexSubqueryInComplexOuterScenario(): QueryObservationScenario<EntityWithRelationship> {
    val related = newSimple(value = "complex-outer-match")
    val parent = newRelationship(
      value = "complex-outer-match",
      related = related,
      count = 19
    )
    Topology.insert(value = parent)
    val parentId = checkNotNull(parent.id)
    val relatedId = checkNotNull(related.id)

    return QueryObservationScenario(
      query = {
        val subquery = SELECT
          .COLUMN(Topology.relationshipTable.ID)
          .FROM(Topology.relationshipTable)
          .LEFT_JOIN(Topology.simpleTable)
          .ON(Topology.relationshipTable.RELATED_ENTITY IS Topology.simpleTable.ID)
          .WHERE(Topology.relationshipTable.VALUE IS Topology.simpleTable.VALUE)

        SELECT
          .FROM(Topology.relationshipTable)
          .WHERE(Topology.relationshipTable.ID IS subquery.asScalar())
          .ORDER_BY(Topology.relationshipTable.ID.ASC())
          .queryDeep()
      },
      initial = listOf(
        expectedRelationship(
          parentId = parentId,
          parentValue = "complex-outer-match",
          relatedId = relatedId,
          relatedValue = "complex-outer-match",
          count = 19
        )
      ),
      mutations = listOf(
        QueryObservationMutation(
          name = "EntityWithRelationship",
          mutate = {
            Topology.updateRelationshipValue(
              value = parent,
              newValue = "complex-outer-updated"
            )
            parent.value = "complex-outer-updated"
          },
          expected = emptyList()
        ),
        QueryObservationMutation(
          name = "SimpleMutableEntity",
          mutate = {
            Topology.updateSimpleValue(
              value = related,
              newValue = "complex-outer-updated"
            )
            related.value = "complex-outer-updated"
          },
          expected = listOf(
            expectedRelationship(
              parentId = parentId,
              parentValue = "complex-outer-updated",
              relatedId = relatedId,
              relatedValue = "complex-outer-updated",
              count = 19
            )
          )
        )
      )
    )
  }

  private fun newSimple(value: String) = SimpleMutableEntity(
    id = null,
    value = value,
    boxedBoolean = false,
    primitiveBoolean = true
  )

  private fun newRelationship(
    value: String,
    related: SimpleMutableEntity?,
    count: Int
  ) = EntityWithRelationship().apply {
    this.value = value
    relatedEntity = related
    this.count = count
  }

  private fun expectedRelationship(
    parentId: Long,
    parentValue: String,
    relatedId: Long,
    relatedValue: String,
    count: Int
  ) = EntityWithRelationship().apply {
    id = parentId
    value = parentValue
    relatedEntity = SimpleMutableEntity(
      id = relatedId,
      value = relatedValue,
      boxedBoolean = false,
      primitiveBoolean = true
    )
    this.count = count
  }

  private object Topology {
    val simpleTable = SIMPLE_MUTABLE_ENTITY
    val relationshipTable = ENTITY_WITH_RELATIONSHIP
    val stringTable = STRING_ID_ENTITY

    fun insert(value: SimpleMutableEntity) = insertedResult(
      result = value
        .insert()
        .execute(),
      modelName = "SimpleMutableEntity"
    )

    fun insert(value: EntityWithRelationship) = insertedResult(
      result = value
        .insert()
        .execute(),
      modelName = "EntityWithRelationship"
    )

    fun insert(value: StringIdEntity) = insertedResult(
      result = value
        .insert()
        .execute(),
      modelName = "StringIdEntity"
    )

    fun updateSimpleValue(
      value: SimpleMutableEntity,
      newValue: String
    ) = check(
      UPDATE
        .TABLE(simpleTable)
        .SET(simpleTable.VALUE, newValue)
        .WHERE(simpleTable.ID IS checkNotNull(value.id))
        .execute() == 1
    )

    fun updateRelationshipValue(
      value: EntityWithRelationship,
      newValue: String
    ) = check(
      UPDATE
        .TABLE(relationshipTable)
        .SET(relationshipTable.VALUE, newValue)
        .WHERE(relationshipTable.ID IS checkNotNull(value.id))
        .execute() == 1
    )

    fun updateStringValue(
      value: StringIdEntity,
      newValue: String
    ) = check(
      UPDATE
        .TABLE(stringTable)
        .SET(stringTable.VALUE, newValue)
        .WHERE(stringTable.ID IS value.id)
        .execute() == 1
    )
  }
}
