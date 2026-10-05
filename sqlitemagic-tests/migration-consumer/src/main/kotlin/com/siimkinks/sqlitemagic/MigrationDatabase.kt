package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.BuildConfig.*
import com.siimkinks.sqlitemagic.annotation.Database
import com.siimkinks.sqlitemagic.migrationfeature.MigrationConsumerFeatureDatabase

@Database(
  name = DB_NAME,
  version = DB_VERSION,
  submodules = [MigrationConsumerFeatureDatabase::class]
)
object MigrationDatabase
