package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.transformer.BooleanTransformer
import org.junit.Test
import kotlin.Int.Companion.MAX_VALUE
import kotlin.Int.Companion.MIN_VALUE

class BooleanTransformerTest {
  @Test
  fun `nullable booleans serialize to canonical database values`() {
    mapOf(null to null, true to 1, false to 0)
      .forEach { (value, expected) ->
        assertWithMessage("Boolean value: $value")
          .that(BooleanTransformer.objectToDbValue(value))
          .isEqualTo(expected)
      }
  }

  @Test
  fun `only database value one deserializes as true and null stays null`() {
    mapOf(null to null, 1 to true, 0 to false, -1 to false, 2 to false, MIN_VALUE to false, MAX_VALUE to false)
      .forEach { (value, expected) ->
        assertWithMessage("Database value: $value")
          .that(BooleanTransformer.dbValueToObject(value))
          .isEqualTo(expected)
      }
  }
}
