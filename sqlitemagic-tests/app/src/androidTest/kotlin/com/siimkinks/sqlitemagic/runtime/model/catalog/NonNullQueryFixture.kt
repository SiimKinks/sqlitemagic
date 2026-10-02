package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.ImmutableValueWithFieldss
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithFields
import com.siimkinks.sqlitemagic.fixture.model.TransformableObject

internal object NonNullQueryFixture {
  val nonNullRows = listOf(
    ImmutableValueWithFields(
      id = null,
      stringValue = "non-null-string-1",
      aBoolean = true,
      integer = 101,
      aDouble = 1.25,
      aShort = 11,
      transformableObject = TransformableObject(value = 1001)
    ),
    ImmutableValueWithFields(
      id = null,
      stringValue = "non-null-string-2",
      aBoolean = false,
      integer = 202,
      aDouble = 2.5,
      aShort = 22,
      transformableObject = TransformableObject(value = 1002)
    ),
    ImmutableValueWithFields(
      id = null,
      stringValue = "non-null-string-3",
      aBoolean = true,
      integer = 303,
      aDouble = 3.75,
      aShort = 33,
      transformableObject = TransformableObject(value = 1003)
    )
  )

  fun seed() {
    check(
      ImmutableValueWithFieldss
        .insert(nonNullRows)
        .execute()
    )
  }
}
