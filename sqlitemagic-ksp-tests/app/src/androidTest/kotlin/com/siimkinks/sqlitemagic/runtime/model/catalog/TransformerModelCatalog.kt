package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.TransformerRuntimeEntityTable.Companion.TRANSFORMER_RUNTIME_ENTITY
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.BlobToken
import com.siimkinks.sqlitemagic.fixture.model.OwnedToken
import com.siimkinks.sqlitemagic.fixture.model.ParameterizedToken
import com.siimkinks.sqlitemagic.fixture.model.NullableToken
import com.siimkinks.sqlitemagic.fixture.model.TransformerRuntimeEmbedded
import com.siimkinks.sqlitemagic.fixture.model.TransformerRuntimeEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.ExternalToken
import com.siimkinks.sqlitemagic.runtime.model.InsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardPersistModelCase
import com.siimkinks.sqlitemagic.update

internal object TransformerModelCatalog {
  val runtimeCase: InsertModelCase<TransformerRuntimeEntity> = TransformerRuntimeEntityCase
  val cases: List<RuntimeModelCase<*>> = listOf(runtimeCase)

  private object TransformerRuntimeEntityCase :
    StandardPersistModelCase<TransformerRuntimeEntity>,
    StandardDeleteModelCase<TransformerRuntimeEntity> {
    override val name = "TransformerRuntimeEntity"
    override val table = TRANSFORMER_RUNTIME_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = TransformerRuntimeEntity(
      id = sequence.toLong(),
      nullableToken = NullableToken(value = "nullable-$sequence"),
      blobToken = BlobToken(value = byteArrayOf(1, 2, sequence.toByte())),
      parameterizedTokens = listOf(
        ParameterizedToken(value = "parameterized-first-$sequence"),
        ParameterizedToken(value = "parameterized-second-$sequence")
      ),
      ownedToken = OwnedToken(value = "owned-$sequence"),
      externalToken = ExternalToken(value = "external-$sequence"),
      embedded = TransformerRuntimeEmbedded(
        ownedToken = OwnedToken(value = "embedded-owned-$sequence")
      )
    )

    override fun insert(value: TransformerRuntimeEntity) = value.insert()

    override fun update(value: TransformerRuntimeEntity) = value.update()

    override fun persist(value: TransformerRuntimeEntity) = value.persist()

    override fun delete(value: TransformerRuntimeEntity) = value.delete()

    override fun expectedAfterInsert(
      value: TransformerRuntimeEntity,
      result: EntityInsertResult.Inserted
    ) = value.copy(id = checkNotNull(result.rowId))

    override fun updatedValue(value: TransformerRuntimeEntity, sequence: Int) = value.copy(
      id = value.id,
      nullableToken = NullableToken(value = "nullable-updated-$sequence"),
      blobToken = BlobToken(value = byteArrayOf(3, 4, sequence.toByte())),
      parameterizedTokens = listOf(
        ParameterizedToken(value = "parameterized-updated-first-$sequence"),
        ParameterizedToken(value = "parameterized-updated-second-$sequence")
      ),
      ownedToken = OwnedToken(value = "owned-updated-$sequence"),
      externalToken = ExternalToken(value = "external-updated-$sequence"),
      embedded = TransformerRuntimeEmbedded(
        ownedToken = OwnedToken(value = "embedded-owned-updated-$sequence")
      )
    )

    override fun toString() = name
  }
}
