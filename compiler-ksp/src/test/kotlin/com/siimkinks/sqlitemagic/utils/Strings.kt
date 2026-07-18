package com.siimkinks.sqlitemagic.utils

import kotlin.uuid.Uuid

val String.Companion.random
  get() = Uuid.random().toString()