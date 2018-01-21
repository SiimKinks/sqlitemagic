package com.siimkinks.sqlitemagic.multimodule

import com.siimkinks.sqlitemagic.annotation.Database
import com.siimkinks.sqlitemagic.submodule.SubmoduleDatabaseConfig

@Database(
    name = "multimodule.db",
    version = 2,
    submodules = [SubmoduleDatabaseConfig::class])
interface DatabaseConfig