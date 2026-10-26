package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.Companion.COMPLEX_OBJECT_WITH_SAME_LEAFS
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SelectQuery
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.fixture.model.ComplexObjectWithSameLeafs
import com.siimkinks.sqlitemagic.fixture.model.EntityWithRelationship
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithFields
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.fixture.model.TransformableObject
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.mapTo
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.queryDeep
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.insertedPersistResult
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.runtime.support.insertedRowId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class RepeatedRelationshipAliasQueryTest(
  private val queryCase: AliasQueryCase
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeReadsTheSeededValue() {
    val simpleValue = ImmutableValueWithFields(
      id = null,
      stringValue = "alias-simple-value",
      aBoolean = true,
      integer = 101,
      aDouble = 1.25,
      aShort = 11,
      transformableObject = TransformableObject(value = 1001)
    )
    val simpleValueDuplicate = ImmutableValueWithFields(
      id = null,
      stringValue = "alias-simple-value-duplicate",
      aBoolean = false,
      integer = 202,
      aDouble = 2.5,
      aShort = 22,
      transformableObject = TransformableObject(value = 1002)
    )
    val simpleValueId = insertedRowId(
      result = simpleValue
        .insert()
        .execute(),
      modelName = "simple value"
    )
    val simpleValueDuplicateId = insertedRowId(
      result = simpleValueDuplicate
        .insert()
        .execute(),
      modelName = "duplicate simple value"
    )
    val entityWithRelationship = EntityWithRelationship().apply {
      value = "alias-relationship-value"
      relatedEntity = SimpleMutableEntity(
        id = null,
        value = "alias-related-value",
        boxedBoolean = false,
        primitiveBoolean = true
      )
      count = 77
    }
    insertedResult(
      result = entityWithRelationship
        .insert()
        .execute(),
      modelName = "EntityWithRelationship"
    )
    val complex = ComplexObjectWithSameLeafs(
      id = Long.MIN_VALUE,
      name = "alias-complex-value",
      simpleValue = simpleValue.copy(id = simpleValueId),
      entityWithRelationship = entityWithRelationship,
      simpleValueDuplicate = simpleValueDuplicate.copy(id = simpleValueDuplicateId)
    )
    val expected = complex.copy(
      id = checkNotNull(
        insertedPersistResult(
          result = complex
            .persist()
            .execute(),
          modelName = "ComplexObjectWithSameLeafs"
        ).rowId
      )
    )

    assertThat(
      queryCase
        .query()
        .execute()
    ).containsExactly(expected)
  }

  class AliasQueryCase(
    private val label: String,
    val query: () -> SelectQuery<ComplexObjectWithSameLeafs, *>
  ) {
    override fun toString() = label
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun queryCases() = listOf(
      AliasQueryCase(
        label = "deep query maps automatic aliases",
        query = {
          SELECT
            .FROM(COMPLEX_OBJECT_WITH_SAME_LEAFS)
            .queryDeep()
        }
      ),
      AliasQueryCase(
        label = "deep query reuses a compatible user join",
        query = {
          val leaf = IMMUTABLE_VALUE_WITH_FIELDS AS "leaf"
          SELECT
            .FROM(COMPLEX_OBJECT_WITH_SAME_LEAFS)
            .LEFT_JOIN(leaf)
            .ON(COMPLEX_OBJECT_WITH_SAME_LEAFS.SIMPLE_VALUE IS leaf.ID)
            .queryDeep()
        }
      ),
      AliasQueryCase(
        label = "user alias of a relationship path name advances the automatic name",
        query = {
          val entityWithRelationship = ENTITY_WITH_RELATIONSHIP AS "simple_value_duplicate"
          SELECT
            .FROM(COMPLEX_OBJECT_WITH_SAME_LEAFS)
            .LEFT_JOIN(entityWithRelationship)
            .ON(COMPLEX_OBJECT_WITH_SAME_LEAFS.ENTITY_WITH_RELATIONSHIP IS entityWithRelationship.ID)
            .queryDeep()
        }
      ),
      AliasQueryCase(
        label = "aliased root deep query maps repeated relationships",
        query = {
          val root = COMPLEX_OBJECT_WITH_SAME_LEAFS AS "complex_root"
          SELECT
            .FROM(root)
            .WHERE(root.NAME IS "alias-complex-value")
            .queryDeep()
        }
      ),
      AliasQueryCase(
        label = "unrelated same-table user join keeps automatic relationship joins",
        query = {
          val unrelatedLeaf = IMMUTABLE_VALUE_WITH_FIELDS AS "unrelated_leaf"
          SELECT
            .FROM(COMPLEX_OBJECT_WITH_SAME_LEAFS)
            .LEFT_JOIN(unrelatedLeaf)
            .ON(unrelatedLeaf.INTEGER IS -1)
            .queryDeep()
        }
      ),
      AliasQueryCase(
        label = "field lists map repeated relationships through exact joins and automatic paths",
        query = {
          val leaf = IMMUTABLE_VALUE_WITH_FIELDS AS "leaf"
          val duplicate = IMMUTABLE_VALUE_WITH_FIELDS AS "duplicate"
          SELECT
            .COLUMNS(
              COMPLEX_OBJECT_WITH_SAME_LEAFS.all(),
              duplicate.all(),
              SIMPLE_MUTABLE_ENTITY.all(),
              leaf.all(),
              ENTITY_WITH_RELATIONSHIP.all()
            )
            .FROM(COMPLEX_OBJECT_WITH_SAME_LEAFS)
            .LEFT_JOIN(leaf)
            .ON(COMPLEX_OBJECT_WITH_SAME_LEAFS.SIMPLE_VALUE IS leaf.ID)
            .LEFT_JOIN(duplicate)
            .ON(COMPLEX_OBJECT_WITH_SAME_LEAFS.SIMPLE_VALUE_DUPLICATE IS duplicate.ID)
            .mapTo(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        }
      )
    )
  }
}
