package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.SqlKeywordOrderTable.Companion.SQL_KEYWORD_ORDER
import com.siimkinks.sqlitemagic.SqlKeywordOrders
import com.siimkinks.sqlitemagic.SqlKeywordWhereTable.Companion.SQL_KEYWORD_WHERE
import com.siimkinks.sqlitemagic.SqlKeywordWheres
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.SqlKeywordOrder
import com.siimkinks.sqlitemagic.fixture.model.SqlKeywordWhere
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation.PRESENT
import com.siimkinks.sqlitemagic.runtime.model.ProjectionColumns
import com.siimkinks.sqlitemagic.runtime.model.RecursiveBulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.RecursiveNullOmittingPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.RecursiveTriggerModelCase
import com.siimkinks.sqlitemagic.runtime.model.ReferencedDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.model.RelationshipQueryModelCase
import com.siimkinks.sqlitemagic.runtime.model.RepresentativeEmptyBulkModelCase
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardDeleteBuilders
import com.siimkinks.sqlitemagic.runtime.model.StandardNullOmittingPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardOperationBuilders
import com.siimkinks.sqlitemagic.runtime.model.SuccessfulModelProjectionCase
import com.siimkinks.sqlitemagic.runtime.model.TriggerModelCase
import com.siimkinks.sqlitemagic.runtime.support.ReactiveAdapter
import com.siimkinks.sqlitemagic.update

internal object SqlKeywordModelCatalog {
  val cases: List<RuntimeModelCase<*>> = listOf(
    SqlKeywordOrderCase,
    SqlKeywordWhereCase
  )

  private object SqlKeywordOrderCase :
    RepresentativeEmptyBulkModelCase<SqlKeywordOrder>,
    TriggerModelCase<SqlKeywordOrder> {
    override val name = "SqlKeywordOrder"
    override val table = SQL_KEYWORD_ORDER
    override val rowIdExpectation = PRESENT

    override fun newValue(sequence: Int) = SqlKeywordOrder(
      id = null,
      group = "keyword-order-$sequence",
      index = null,
      default = true
    )

    override val operationBuilders = StandardOperationBuilders(
      insert = SqlKeywordOrder::insert,
      bulkInsert = SqlKeywordOrders::insert,
      update = SqlKeywordOrder::update,
      bulkUpdate = SqlKeywordOrders::update,
      persist = SqlKeywordOrder::persist,
      bulkPersist = SqlKeywordOrders::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = SqlKeywordOrder::delete,
      bulkDelete = SqlKeywordOrders::delete,
      deleteTable = SqlKeywordOrders::deleteTable
    )

    override fun updatedValue(value: SqlKeywordOrder, sequence: Int) = value.copy(
      group = "keyword-order-updated-$sequence",
      index = sequence % 2 == 0,
      default = sequence % 2 != 0
    )

    override fun expectedAfterInsert(value: SqlKeywordOrder, result: EntityInsertResult.Inserted) = value

    override fun toString() = name
  }

  private object SqlKeywordWhereCase :
    RepresentativeEmptyBulkModelCase<SqlKeywordWhere>,
    RelationshipQueryModelCase<SqlKeywordWhere>,
    SuccessfulModelProjectionCase<SqlKeywordWhere>,
    RecursiveTriggerModelCase<SqlKeywordWhere>,
    RecursiveBulkPersistModelCase<SqlKeywordWhere>,
    RecursiveNullOmittingPersistModelCase<SqlKeywordWhere>,
    StandardNullOmittingPersistModelCase<SqlKeywordWhere>,
    ReferencedDeleteModelCase<SqlKeywordWhere, SqlKeywordOrder> {
    override val name = "SqlKeywordWhere"
    override val table = SQL_KEYWORD_WHERE
    override val relatedTable = SQL_KEYWORD_ORDER
    override val rowIdExpectation = PRESENT

    override val projectionColumns: ProjectionColumns = listOf(
      SQL_KEYWORD_WHERE.ID,
      SQL_KEYWORD_WHERE.VALUES
    )

    override fun newValue(sequence: Int) = SqlKeywordWhere().apply {
      values = "keyword-where-$sequence"
      order = SqlKeywordOrder(
        id = null,
        group = "keyword-where-order-$sequence",
        index = false,
        default = true
      )
      limit = 7 + sequence
    }

    override val operationBuilders = StandardOperationBuilders(
      insert = SqlKeywordWhere::insert,
      bulkInsert = SqlKeywordWheres::insert,
      update = SqlKeywordWhere::update,
      bulkUpdate = SqlKeywordWheres::update,
      persist = SqlKeywordWhere::persist,
      bulkPersist = SqlKeywordWheres::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = SqlKeywordWhere::delete,
      bulkDelete = SqlKeywordWheres::delete,
      deleteTable = SqlKeywordWheres::deleteTable
    )

    override fun relatedDeleteValues(value: SqlKeywordWhere) = listOfNotNull(value.order)

    override fun executeRelatedDelete(value: SqlKeywordOrder) = value
      .delete()
      .execute()

    override fun observeRelatedDelete(
      adapter: ReactiveAdapter,
      value: SqlKeywordOrder
    ) = adapter.observe(value.delete())

    override fun executeRelatedBulkDelete(values: Collection<SqlKeywordOrder>) = SqlKeywordOrders
      .delete(o = values)
      .execute()

    override fun observeRelatedBulkDelete(
      adapter: ReactiveAdapter,
      values: Collection<SqlKeywordOrder>
    ) = adapter.observe(SqlKeywordOrders.delete(o = values))

    override fun executeRelatedTableDelete() = SqlKeywordOrders
      .deleteTable()
      .execute()

    override fun observeRelatedTableDelete(adapter: ReactiveAdapter) = adapter.observe(
      SqlKeywordOrders.deleteTable()
    )

    override fun expectedAfterInsert(
      value: SqlKeywordWhere,
      result: EntityInsertResult.Inserted
    ) = value.copyWithGeneratedId(result = result)

    override fun expectedAfterProjection(value: SqlKeywordWhere) = SqlKeywordWhere().apply {
      id = value.id
      values = value.values
    }

    override fun expectedAfterShallowQuery(deepExpected: SqlKeywordWhere) = SqlKeywordWhere().apply {
      id = deepExpected.id
      values = deepExpected.values
      order = deepExpected.order?.let { related ->
        SqlKeywordOrder(id = related.id)
      }
      limit = deepExpected.limit
    }

    override fun updatedValue(value: SqlKeywordWhere, sequence: Int) = value.apply {
      values = "keyword-where-updated-$sequence"
      limit = 17 + sequence
      order?.let { related ->
        related.group = "keyword-where-order-updated-$sequence"
        related.index = sequence % 2 == 0
        related.default = sequence % 2 != 0
      }
    }

    override fun partialNullValue(sequence: Int) = newValue(sequence = sequence)
      .copyForNullOmittingPersist(
        sequence = sequence,
        id = null
      )

    override fun partialNullUpdatedValue(value: SqlKeywordWhere, sequence: Int) = value
      .copyForNullOmittingPersist(
        sequence = sequence,
        id = value.id
      )

    override fun expectedAfterNullOmittingUpdate(
      existing: SqlKeywordWhere,
      value: SqlKeywordWhere
    ) = SqlKeywordWhere().apply {
      id = existing.id
      values = existing.values
      limit = value.limit
      order = checkNotNull(existing.order).copy(
        id = checkNotNull(value.order).id,
        default = checkNotNull(value.order).default
      )
    }

    override fun toString() = name
  }

  private fun SqlKeywordWhere.copyWithGeneratedId(result: EntityInsertResult.Inserted) =
    SqlKeywordWhere().also { entity ->
      entity.id = checkNotNull(result.rowId)
      entity.values = values
      entity.order = order
      entity.limit = limit
    }

  private fun SqlKeywordWhere.copyForNullOmittingPersist(
    sequence: Int,
    id: Long?
  ) = SqlKeywordWhere().also { entity ->
    entity.id = id
    entity.values = null
    entity.limit = 100 + sequence
    entity.order = checkNotNull(order).copy(
      group = null,
      index = null,
      default = sequence % 2 == 0
    )
  }
}
