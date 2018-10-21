@file:Suppress("NOTHING_TO_INLINE")

package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.entity.ConnectionProvidedOperation

/** @see ConnectionProvidedOperation.usingConnection */
@CheckResult
inline infix fun <R> ConnectionProvidedOperation<R>.usingConnection(connection: DbConnection) =
    this.usingConnection(connection)