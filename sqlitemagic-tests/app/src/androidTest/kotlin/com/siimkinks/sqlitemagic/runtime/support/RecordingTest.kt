package com.siimkinks.sqlitemagic.runtime.support

import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class RecordingTest(
  private val violation: ProtocolViolation
) {
  @Test
  fun assertionRejectsProtocolViolation() {
    val recording = Recording<Int>()
    violation.signal(recording)
    assertThrows(AssertionError::class.java) { violation.assertion(recording) }
  }

  class ProtocolViolation(
    private val name: String,
    val signal: (Recording<Int>) -> Unit,
    val assertion: (Recording<Int>) -> Unit
  ) {
    override fun toString() = name
  }

  companion object {
    private val subscription = object : RecordedSubscription {
      override fun request(count: Long) = Unit

      override fun cancel() = Unit
    }

    @JvmStatic
    @Parameters(name = "{0}")
    fun violations() = listOf(
      ProtocolViolation(
        name = "duplicate completion",
        signal = { recording ->
          recording.onSubscribe(subscription)
          recording.onComplete()
          recording.onComplete()
        },
        assertion = Recording<Int>::assertResult
      ),
      ProtocolViolation(
        name = "duplicate failure",
        signal = { recording ->
          recording.onSubscribe(subscription)
          recording.onError(IllegalStateException("first"))
          recording.onError(IllegalStateException("second"))
        },
        assertion = { recording -> recording.assertFailure { true } }
      ),
      ProtocolViolation(
        name = "missing subscription",
        signal = { it.onNext(1) },
        assertion = { it.assertValuesOnly(1) }
      ),
      ProtocolViolation(
        name = "duplicate subscription",
        signal = { recording ->
          recording.onSubscribe(subscription)
          recording.onSubscribe(subscription)
          recording.onNext(1)
        },
        assertion = { it.assertValuesOnly(1) }
      )
    )
  }
}
