package com.siimkinks.sqlitemagic.multimodule

import com.siimkinks.sqlitemagic.annotation.Database
import com.siimkinks.sqlitemagic.another.AnotherSubmoduleDatabaseConfig
import com.siimkinks.sqlitemagic.submodule.SubmoduleDatabaseConfig

@Database(
    name = "multimodule.db",
    version = 2,
    submodules = [
      SubmoduleDatabaseConfig::class,
      AnotherSubmoduleDatabaseConfig::class
    ]
)
interface DatabaseConfig