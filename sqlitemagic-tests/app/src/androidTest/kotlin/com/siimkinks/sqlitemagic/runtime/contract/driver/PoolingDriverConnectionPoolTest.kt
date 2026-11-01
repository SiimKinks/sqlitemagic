@file:OptIn(InternalSqliteMagicApi::class)

package com.siimkinks.sqlitemagic.runtime.contract.driver

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper.Callback
import androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.driver.SupportSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.connection.ConnectionOptions
import com.siimkinks.sqlitemagic.connection.ConnectionPool
import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
import com.siimkinks.sqlitemagic.runtime.support.freshDatabasePath
import org.junit.Test

internal class PoolingDriverConnectionPoolTest {
  @Test
  fun mutationResultsComeFromTheConnectionThatRanTheMutation() {
    val name = "pooling-driver.db"
    val fileName = freshDatabasePath(name)
    val helper = FrameworkSQLiteOpenHelperFactory().create(
      Configuration
        .builder(InstrumentationRegistry.getInstrumentation().targetContext)
        .name(name)
        .callback(
          object : Callback(AccountSchema.version) {
            override fun onCreate(db: SupportSQLiteDatabase) =
              db.execSQL("CREATE TABLE tag(id INTEGER PRIMARY KEY, name TEXT NOT NULL)")

            override fun onUpgrade(
              db: SupportSQLiteDatabase,
              oldVersion: Int,
              newVersion: Int
            ) = Unit
          }
        )
        .build()
    )
    helper.setWriteAheadLoggingEnabled(true)

    val results = ConnectionPool(
      driver = SupportSQLiteDriver(helper),
      fileName = fileName,
      options = ConnectionOptions(),
      schema = AccountSchema
    ).use { pool ->
      pool.write { pooled ->
        listOf(
          pooled.withStatement(
            sql = "INSERT INTO tag(name) VALUES ('first')",
            block = pooled.mutations::executeInsert
          ),
          pooled.withStatement(
            sql = "INSERT INTO tag(name) VALUES ('second')",
            block = pooled.mutations::executeInsert
          ),
          pooled.withStatement("UPDATE tag SET name = name || '!'") { statement ->
            pooled.mutations
              .executeUpdateDelete(statement)
              .toLong()
          }
        )
      }
    }

    assertThat(results)
      .containsExactly(1L, 2L, 2L)
      .inOrder()
  }
}
