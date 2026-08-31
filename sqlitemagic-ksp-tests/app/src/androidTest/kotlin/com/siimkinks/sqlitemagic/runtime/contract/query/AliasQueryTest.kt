package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AS
import com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.Companion.COMPLEX_OBJECT_WITH_SAME_LEAFS
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.fixture.model.ComplexObjectWithSameLeafs
import com.siimkinks.sqlitemagic.fixture.model.EntityWithRelationship
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithFields
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.fixture.model.TransformableObject
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.model.catalog.QueryPredicateCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test

class AliasQueryTest : RuntimeDatabaseTest() {
  @Test
  fun selectedScalarColumnSupportsNonIdentifierAlias() {
    QueryPredicateCatalog.seed()

    assertThat(
      Select
        .column(IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE AS "string value")
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
        .execute()
    ).isEqualTo(
      QueryPredicateCatalog.rows
        .map(ImmutableValueWithFields::stringValue)
    )
  }

  @Test
  fun aliasedRootTableUsesAliasedPredicate() {
    val expected = SimpleMutableEntity(
      id = null,
      value = "aliased-root-value",
      boxedBoolean = null,
      primitiveBoolean = true
    )
    check(
      expected
        .insert()
        .execute() is EntityInsertResult.Inserted
    )
    val aliasedTable = SIMPLE_MUTABLE_ENTITY AS "simple_alias"

    assertThat(
      Select
        .from(aliasedTable)
        .where(aliasedTable.VALUE IS "aliased-root-value")
        .execute()
    ).containsExactly(expected)
  }

  @Test
  fun duplicateRelationshipDeepQueryMapsAutomaticAliases() {
    val expected = seedComplexValue()

    assertThat(
      Select
        .columns(
          COMPLEX_OBJECT_WITH_SAME_LEAFS.all(),
          IMMUTABLE_VALUE_WITH_FIELDS.all(),
          ENTITY_WITH_RELATIONSHIP.all(),
          SIMPLE_MUTABLE_ENTITY.all()
        )
        .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        .queryDeep()
        .execute()
    ).containsExactly(expected)
  }

  @Test
  fun compatibleUserJoinIsReusedForRelationshipDeepQuery() {
    val expected = seedComplexValue()
    val leaf = IMMUTABLE_VALUE_WITH_FIELDS AS "leaf"

    assertThat(
      Select
        .columns(
          COMPLEX_OBJECT_WITH_SAME_LEAFS.all(),
          leaf.all(),
          IMMUTABLE_VALUE_WITH_FIELDS.all(),
          ENTITY_WITH_RELATIONSHIP.all(),
          SIMPLE_MUTABLE_ENTITY.all()
        )
        .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        .leftJoin(leaf.on(COMPLEX_OBJECT_WITH_SAME_LEAFS.SIMPLE_VALUE IS leaf.ID))
        .queryDeep()
        .execute()
    ).containsExactly(expected)
  }

  @Test
  fun userSmZeroAliasAdvancesAutomaticAlias() {
    val expected = seedComplexValue()
    val entityWithRelationship = ENTITY_WITH_RELATIONSHIP AS "sm_0"

    assertThat(
      Select
        .columns(
          COMPLEX_OBJECT_WITH_SAME_LEAFS.all(),
          entityWithRelationship.all(),
          IMMUTABLE_VALUE_WITH_FIELDS.all(),
          SIMPLE_MUTABLE_ENTITY.all()
        )
        .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        .leftJoin(
          entityWithRelationship.on(
            COMPLEX_OBJECT_WITH_SAME_LEAFS.ENTITY_WITH_RELATIONSHIP IS entityWithRelationship.ID
          )
        )
        .queryDeep()
        .execute()
    ).containsExactly(expected)
  }

  private fun seedComplexValue(): ComplexObjectWithSameLeafs {
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
    val simpleValueId = when (val result = simpleValue.insert().execute()) {
      is EntityInsertResult.Inserted -> checkNotNull(result.rowId)
      EntityInsertResult.Ignored -> error("Simple value seed insert was ignored")
    }
    val simpleValueDuplicateId = when (val result = simpleValueDuplicate.insert().execute()) {
      is EntityInsertResult.Inserted -> checkNotNull(result.rowId)
      EntityInsertResult.Ignored -> error("Duplicate simple value seed insert was ignored")
    }
    val relatedEntity = SimpleMutableEntity(
      id = null,
      value = "alias-related-value",
      boxedBoolean = false,
      primitiveBoolean = true
    )
    val entityWithRelationship = EntityWithRelationship().apply {
      value = "alias-relationship-value"
      this.relatedEntity = relatedEntity
      count = 77
    }
    val entityWithRelationshipId = when (val result = entityWithRelationship.insert().execute()) {
      is EntityInsertResult.Inserted -> checkNotNull(result.rowId)
      EntityInsertResult.Ignored -> error("Relationship seed insert was ignored")
    }
    val complex = ComplexObjectWithSameLeafs(
      id = Long.MIN_VALUE,
      name = "alias-complex-value",
      simpleValue = simpleValue.copy(id = simpleValueId),
      entityWithRelationship = entityWithRelationship,
      simpleValueDuplicate = simpleValueDuplicate.copy(id = simpleValueDuplicateId)
    )
    val complexId = when (val result = complex.persist().execute()) {
      is EntityPersistResult.Inserted -> checkNotNull(result.rowId)
      EntityPersistResult.Updated -> error("Complex value seed unexpectedly updated")
      EntityPersistResult.Ignored -> error("Complex value seed persist was ignored")
    }
    return complex.copy(id = complexId).also {
      check(entityWithRelationship.id == entityWithRelationshipId)
    }
  }
}
