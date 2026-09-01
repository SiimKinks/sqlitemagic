package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.TransformerRuntimeEntityTable.Companion.TRANSFORMER_RUNTIME_ENTITY
import com.siimkinks.sqlitemagic.fixture.model.BlobToken
import com.siimkinks.sqlitemagic.fixture.model.OwnedToken
import com.siimkinks.sqlitemagic.fixture.model.ParameterizedToken
import com.siimkinks.sqlitemagic.fixture.model.NullableToken
import com.siimkinks.sqlitemagic.fixture.model.TransformerRuntimeEmbedded
import com.siimkinks.sqlitemagic.fixture.model.TransformerRuntimeEntity
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.ExternalToken
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase

internal object TransformerModelCatalog {
  val runtimeCase: RuntimeModelCase<TransformerRuntimeEntity> = TransformerRuntimeEntityCase
  val cases: List<RuntimeModelCase<*>> = listOf(runtimeCase)

  private object TransformerRuntimeEntityCase : RuntimeModelCase<TransformerRuntimeEntity> {
    override val name = "TransformerRuntimeEntity"
    override val table = TRANSFORMER_RUNTIME_ENTITY

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

    override fun toString() = name
  }
}
