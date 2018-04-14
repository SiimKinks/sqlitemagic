package com.siimkinks.sqlitemagic.another

import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase
import com.siimkinks.sqlitemagic.submodule.DbTransformers

@SubmoduleDatabase("another", externalTransformers = [DbTransformers::class])
interface AnotherSubmoduleDatabaseConfig