package com.siimkinks.sqlitemagic.runtime.contract.query

import android.database.SQLException
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.runtime.model.catalog.NonNullQueryFixture
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.scalarFunction
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

internal class DeclaredFunctionRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun requiredDeclaredFunctionResultRejectsSqlNull() {
    NonNullQueryFixture.seed()
    val nullIf = scalarFunction("nullif").readAs(LONG)
    val required = SELECT
      .COLUMN(nullIf(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER, 202))
      .FROM(IMMUTABLE_VALUE_WITH_FIELDS)

    assertThat(assertThrows(SQLException::class.java) { required.execute() })
      .hasMessageThat()
      .isEqualTo("Required SQL result returned SQL NULL")
  }
}
