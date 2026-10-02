package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.OperatorCountZeroOrNot.Companion.countNotZero
import com.siimkinks.sqlitemagic.OperatorCountZeroOrNot.Companion.countZero
import io.reactivex.Observable

/** An [Observable] of [Query] which offers count query specific convenience operators. */
open class CountQueryObservable internal constructor(
  upstream: Observable<Query<Long>>
) : SingleItemQueryObservable<Long>(upstream) {
  /**
   * Runs each emitted [Query] and propagates `true` to downstream if
   * [Query] result is equal to zero; `false` otherwise.
   */
  @CheckResult
  fun isZero(): Observable<Boolean> = lift(countZero)

  /**
   * Runs each emitted [Query] and propagates `true` to downstream if
   * [Query] result is not equal to zero; `false` otherwise.
   */
  @CheckResult
  fun isNotZero(): Observable<Boolean> = lift(countNotZero)
}
