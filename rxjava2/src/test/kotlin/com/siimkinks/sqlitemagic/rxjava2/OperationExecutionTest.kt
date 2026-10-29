package com.siimkinks.sqlitemagic.rxjava2

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.deferredBulk
import io.reactivex.observers.TestObserver
import io.reactivex.plugins.RxJavaPlugins
import org.junit.Test
import java.util.concurrent.CancellationException

internal class OperationExecutionTest {
  @Test
  fun `cancellation exception without disposal is emitted`() {
    val cancellation = CancellationException("not caused by disposal")

    deferredBulk { throw cancellation }
      .observeBulk()
      .test()
      .assertFailure(cancellation::equals)
  }

  @Test
  fun `cancellation exception after disposal is suppressed`() {
    val pluginErrors = mutableListOf<Throwable>()
    RxJavaPlugins.setErrorHandler(pluginErrors::add)
    try {
      val observer = TestObserver<Void>()
      var cancelled = false

      deferredBulk { isCancelled ->
        observer.dispose()
        cancelled = isCancelled()
        throw CancellationException()
      }
        .observeBulk()
        .subscribe(observer)

      observer.assertEmpty()
      assertThat(cancelled).isTrue()
      assertThat(pluginErrors).isEmpty()
    } finally {
      RxJavaPlugins.reset()
    }
  }
}
