package com.siimkinks.sqlitemagic.multimodule

import android.support.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AuthorTable.AUTHOR
import com.siimkinks.sqlitemagic.FROM
import com.siimkinks.sqlitemagic.ImmutableValueTable.IMMUTABLE_VALUE
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.submodule.ImmutableValue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultimoduleDatabaseTest {
  @Before
  fun setUp() {
    SqliteMagic.getDefaultConnection().clearData()
  }

  @Test
  fun insertIntoNativeModuleTable() {
    val value = Author.newRandom()
    val id = value
        .insert()
        .execute()
    assertThat(id).isNotEqualTo(-1)
    assertThat(id).isNotEqualTo(value.id)

    assertThat((SELECT FROM AUTHOR).count().execute())
        .isEqualTo(1)
    assertThat((SELECT FROM AUTHOR).takeFirst().execute())
        .isEqualTo(value.copy(id = id))
  }

  @Test
  fun insertIntoSubmoduleTable() {
    val value = ImmutableValue.newRandom()
    val id = value
        .insert()
        .execute()
    assertThat(id).isNotEqualTo(-1)
    assertThat(id).isNotEqualTo(value.id)

    assertThat((SELECT FROM IMMUTABLE_VALUE).count().execute())
        .isEqualTo(1)
    assertThat((SELECT FROM IMMUTABLE_VALUE).takeFirst().execute())
        .isEqualTo(value.copy(id = id))
  }

  @Test
  fun clearAllTables() {
    Author.newRandom().insert().execute()
    ImmutableValue.newRandom().insert().execute()

    SqliteMagic.getDefaultConnection().clearData()

    assertThat((SELECT FROM AUTHOR).count().execute())
        .isEqualTo(0)
    assertThat((SELECT FROM IMMUTABLE_VALUE).count().execute())
        .isEqualTo(0)
  }
}