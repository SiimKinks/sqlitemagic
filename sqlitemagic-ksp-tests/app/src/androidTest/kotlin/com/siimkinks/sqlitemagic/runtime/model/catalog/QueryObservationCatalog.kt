package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SelectSqlNode
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.StringIdEntityTable.Companion.STRING_ID_ENTITY
import com.siimkinks.sqlitemagic.Update
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.EntityWithRelationship
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.fixture.model.StringIdEntity
import com.siimkinks.sqlitemagic.insert

data class QueryObservationMutation<T>(
  val name: String,
  val apply: () -> Unit,
  val expected: List<T>
)

data class QueryObservationScenario<T>(
  val query: () -> SelectSqlNode.SelectNode<T, *, *>,
  val initial: List<T>,
  val mutations: List<QueryObservationMutation<T>>,
  val unrelatedWrite: () -> Unit
)

data class QueryObservationCase<T>(
  val name: String,
  val newScenario: () -> QueryObservationScenario<T>
) {
  override fun toString() = name
}

internal object QueryObservationCatalog {
  val cases: List<QueryObservationCase<*>> = listOf(
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
        Select
          .from(Topology.relationshipTable)
          .leftJoin(
            Topology.simpleTable.on(
              Topology.relationshipTable.RELATED_ENTITY IS Topology.simpleTable.ID
            )
          )
          .where(Topology.relationshipTable.VALUE IS Topology.simpleTable.VALUE)
          .orderBy(Topology.relationshipTable.ID.asc())
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
          apply = {
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
          apply = {
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
      ),
      unrelatedWrite = QueryPredicateCatalog::seed
    )
  }

  private fun newSimpleScalarSubqueryScenario(): QueryObservationScenario<Long?> {
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
        Select
          .column(Topology.simpleTable.ID)
          .from(Topology.simpleTable)
          .where(
            Topology.simpleTable.VALUE IS Select
              .column(Topology.stringTable.VALUE)
              .from(Topology.stringTable)
          )
          .orderBy(Topology.simpleTable.ID.asc())
      },
      initial = listOf(outerId),
      mutations = listOf(
        QueryObservationMutation(
          name = "StringIdEntity",
          apply = {
            Topology.updateStringValue(
              value = target,
              newValue = "simple-subquery-updated"
            )
          },
          expected = emptyList()
        ),
        QueryObservationMutation(
          name = "SimpleMutableEntity",
          apply = {
            Topology.updateSimpleValue(
              value = outer,
              newValue = "simple-subquery-updated"
            )
            outer.value = "simple-subquery-updated"
          },
          expected = listOf(outerId)
        )
      ),
      unrelatedWrite = QueryPredicateCatalog::seed
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
        val subquery = Select
          .column(Topology.stringTable.ID)
          .from(Topology.relationshipTable)
          .leftJoin(
            Topology.simpleTable.on(
              Topology.relationshipTable.RELATED_ENTITY IS Topology.simpleTable.ID
            )
          )
          .leftJoin(
            Topology.stringTable.on(
              Topology.stringTable.VALUE IS Topology.simpleTable.VALUE
            )
          )
          .where(Topology.relationshipTable.VALUE IS Topology.simpleTable.VALUE)
          .queryDeep()

        Select
          .column(Topology.stringTable.ID)
          .from(Topology.stringTable)
          .where(Topology.stringTable.ID IS subquery)
          .orderBy(Topology.stringTable.ID.asc())
      },
      initial = listOf(outer.id),
      mutations = listOf(
        QueryObservationMutation(
          name = "EntityWithRelationship",
          apply = {
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
          apply = {
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
          apply = {
            Topology.updateStringValue(
              value = outer,
              newValue = "complex-subquery-updated"
            )
          },
          expected = listOf(outer.id)
        )
      ),
      unrelatedWrite = QueryPredicateCatalog::seed
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
        val subquery = Select
          .column(Topology.relationshipTable.ID)
          .from(Topology.relationshipTable)
          .leftJoin(
            Topology.simpleTable.on(
              Topology.relationshipTable.RELATED_ENTITY IS Topology.simpleTable.ID
            )
          )
          .where(Topology.relationshipTable.VALUE IS Topology.simpleTable.VALUE)
          .queryDeep()

        Select
          .from(Topology.relationshipTable)
          .where(Topology.relationshipTable.ID IS subquery)
          .orderBy(Topology.relationshipTable.ID.asc())
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
          apply = {
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
          apply = {
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
      ),
      unrelatedWrite = QueryPredicateCatalog::seed
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

    fun insert(value: SimpleMutableEntity) =
      check(
        value
          .insert()
          .execute() is EntityInsertResult.Inserted
      )

    fun insert(value: EntityWithRelationship) =
      check(
        value
          .insert()
          .execute() is EntityInsertResult.Inserted
      )

    fun insert(value: StringIdEntity) =
      check(
        value
          .insert()
          .execute() is EntityInsertResult.Inserted
      )

    fun updateSimpleValue(
      value: SimpleMutableEntity,
      newValue: String
    ) {
      check(
        Update
          .table(simpleTable)
          .setNullable(simpleTable.VALUE, newValue)
          .where(simpleTable.ID IS checkNotNull(value.id))
          .execute() == 1
      )
    }

    fun updateRelationshipValue(
      value: EntityWithRelationship,
      newValue: String
    ) {
      check(
        Update
          .table(relationshipTable)
          .setNullable(relationshipTable.VALUE, newValue)
          .where(relationshipTable.ID IS checkNotNull(value.id))
          .execute() == 1
      )
    }

    fun updateStringValue(
      value: StringIdEntity,
      newValue: String
    ) {
      check(
        Update
          .table(stringTable)
          .set(stringTable.VALUE, newValue)
          .where(stringTable.ID IS value.id)
          .execute() == 1
      )
    }
  }
}
