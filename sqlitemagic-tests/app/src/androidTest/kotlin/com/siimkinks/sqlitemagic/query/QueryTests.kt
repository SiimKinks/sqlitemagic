@file:Suppress("UNCHECKED_CAST")

package com.siimkinks.sqlitemagic.query

import android.os.Build
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SelectSqlNode
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.model.TestModel

open class QueryTestCase<PrepType, TestReturnType>(
    private val testName: String = "",
    private val setUp: () -> PrepType,
    operation: QueryOperation<PrepType, TestReturnType>,
    private val assertResults: (PrepType, TestReturnType) -> Unit
) : QueryOperation<PrepType, TestReturnType> by operation {
  private fun testExecute() {
    SqliteMagic.getDefaultConnection().clearData()
    val beforeVal = setUp()
    val testVal = executeTest(beforeVal)
    assertResults(beforeVal, testVal)
  }

  private fun testObserve() {
    SqliteMagic.getDefaultConnection().clearData()
    val beforeVal = setUp()
    val testVal = observeTest(beforeVal)
    assertResults(beforeVal, testVal)
  }

  fun test() {
    val testResult = QueryTestResult()
    try {
      testExecute()
    } catch (e: Throwable) {
      testResult.executeError = e
    }
    try {
      testObserve()
    } catch (e: Throwable) {
      testResult.observeError = e
    }
    if (!testResult.successful) {
      val errorMessage = StringBuilder("Test case \"")
          .append(testName)
          .append("\" failed for [")
      if (testResult.executeFailed) {
        errorMessage.append("execute")
        testResult.executeError?.printStackTrace()
      }
      if (testResult.observeFailed) {
        if (testResult.executeFailed) {
          errorMessage.append("|")
        }
        errorMessage.append("observe")
        testResult.observeError?.printStackTrace()
      }
      errorMessage.append("]\n")
      val error = AssertionError(errorMessage)
      if (testResult.executeFailed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          error.addSuppressed(AssertionError("Execute failure", testResult.executeError!!))
        }
      }
      if (testResult.observeFailed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          error.addSuppressed(AssertionError("Observe failure", testResult.observeError!!))
        }
      }
      throw error
    }
  }
}

open class QueryModelTestCase<PrepType, ModelType, TestReturnType>(
    private val testName: String = "",
    private val testModel: TestModel<ModelType>,
    private val setUp: (TestModel<ModelType>) -> PrepType,
    operation: QueryOperation<PrepType, TestReturnType>,
    private val assertResults: (PrepType, TestReturnType) -> Unit
) : QueryOperation<PrepType, TestReturnType> by operation {
  private fun testExecute() {
    val model = testModel
    model.deleteTable()
    val beforeVal = setUp(model)
    val testVal = executeTest(beforeVal)
    assertResults(beforeVal, testVal)
  }

  private fun testObserve() {
    val model = testModel
    model.deleteTable()
    val beforeVal = setUp(model)
    val testVal = observeTest(beforeVal)
    assertResults(beforeVal, testVal)
  }

  fun test() {
    val testResult = QueryTestResult()
    try {
      testExecute()
    } catch (e: Throwable) {
      testResult.executeError = e
    }
    try {
      testObserve()
    } catch (e: Throwable) {
      testResult.observeError = e
    }
    if (!testResult.successful) {
      val errorMessage = StringBuilder("Test case \"")
          .append(testName)
          .append("\" failed for [")
      if (testResult.executeFailed) {
        errorMessage.append("execute")
        testResult.executeError?.printStackTrace()
      }
      if (testResult.observeFailed) {
        if (testResult.executeFailed) {
          errorMessage.append("|")
        }
        errorMessage.append("observe")
        testResult.observeError?.printStackTrace()
      }
      errorMessage.append("]\n")
      val error = AssertionError(errorMessage)
      if (testResult.executeFailed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          error.addSuppressed(AssertionError("Execute failure", testResult.executeError!!))
        }
      }
      if (testResult.observeFailed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          error.addSuppressed(AssertionError("Observe failure", testResult.observeError!!))
        }
      }
      throw error
    }
  }
}

class QueryTestResult {
  var executeError: Throwable? = null
  var observeError: Throwable? = null
  val executeFailed get() = executeError != null
  val observeFailed get() = observeError != null
  val successful get() = executeError == null && observeError == null
}

interface QueryOperation<PrepType, TestReturnType> {
  fun executeTest(testVal: PrepType): TestReturnType
  fun observeTest(testVal: PrepType): TestReturnType
}

class SelectListQueryOperation<PrepType, SelectReturnType, SelectType>(
    private val selectBuilder: () -> SelectSqlNode.SelectNode<SelectReturnType, SelectType, *>
) : QueryOperation<PrepType, List<SelectReturnType>> {
  override fun executeTest(testVal: PrepType): List<SelectReturnType> = selectBuilder()
      .execute()

  override fun observeTest(testVal: PrepType): List<SelectReturnType> = selectBuilder()
      .observe()
      .runQueryOnce()
      .blockingGet()
}

class SelectFirstQueryOperation<PrepType, SelectReturnType, SelectType>(
    private val selectBuilder: () -> SelectSqlNode.SelectNode<SelectReturnType, SelectType, *>
) : QueryOperation<PrepType, SelectReturnType?> {
  override fun executeTest(testVal: PrepType): SelectReturnType? = selectBuilder()
      .takeFirst()
      .execute()

  override fun observeTest(testVal: PrepType): SelectReturnType? = selectBuilder()
      .takeFirst()
      .observe()
      .runQueryOnce()
      .blockingGet()
}

fun <T> resultIsEqualToExpected(): (T, T) -> Unit = { expected, result ->
  assertThat(result).isEqualTo(expected)
}

fun <T> resultIsNull(): (T, T) -> Unit = { _, result -> assertThat(result).isNull() }