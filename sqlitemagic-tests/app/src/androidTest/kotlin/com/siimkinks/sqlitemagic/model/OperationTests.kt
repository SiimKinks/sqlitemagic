@file:Suppress("DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE")

package com.siimkinks.sqlitemagic.model

import android.os.Build
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Column
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.*
import io.reactivex.observers.TestObserver
import java.util.concurrent.TimeUnit

class SingleTestCaseConfiguration {
  var testCaseCreator: ((TestModel<Any>) -> SingleOperationTestCase<*, *, *>)? = null
  var testModels: Array<out TestModel<*>>? = null

  fun testCase(testCase: (TestModel<Any>) -> SingleOperationTestCase<*, *, *>) {
    this.testCaseCreator = testCase
  }

  fun isSuccessfulFor(vararg models: TestModel<*>) {
    this.testModels = models
  }
}

class SingleTestResult(val model: TestModel<*>) {
  var error: Throwable? = null
  val failed get() = error != null
}

@Suppress("UNCHECKED_CAST")
fun assertThatSingle(testConfig: SingleTestCaseConfiguration.() -> Unit) {
  val test = SingleTestCaseConfiguration()
  testConfig(test)
  val testCaseCreator = checkNotNull(test.testCaseCreator) { "Test case missing" }
  val testModels = checkNotNull(test.testModels) { "Test models missing" }
  val fails = ArrayList<SingleTestResult>()
  testModels.forEach { model ->
    val testResult = SingleTestResult(model)
    val testCase = testCaseCreator(model as TestModel<Any>)
    try {
      testCase.test()
    } catch (e: Throwable) {
      testResult.error = e
    }
    if (testResult.failed) {
      fails.add(testResult)
    }
  }
  if (fails.isNotEmpty()) {
    val testCase = testCaseCreator(testModels[0] as TestModel<Any>)
    val errorMessage = StringBuilder("Test case \"")
        .append(testCase.testName)
        .append("\" failed for models:\n")
    fails.forEach { fail ->
      errorMessage
          .append(fail.model.table)
          .append("\n")
      fail.error?.printStackTrace()
    }
    val error = AssertionError(errorMessage.toString())
    fails.forEach { fail ->
      val modelError = fail.error!!
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        error.addSuppressed(AssertionError("Failure for model ${fail.model.table}", modelError))
      }
    }
    throw error
  }
}

typealias SingleOperation<PrepType, ModelType, TestReturnType> = (model: TestModel<ModelType>, testVal: PrepType) -> TestReturnType

open class SingleOperationTestCase<PrepType, ModelType, TestReturnType>(
    val testName: String = "",
    private val model: TestModel<ModelType>,
    private val setUp: (TestModel<ModelType>) -> PrepType,
    private val test: SingleOperation<PrepType, ModelType, TestReturnType>,
    private val assertResults: (TestModel<ModelType>, PrepType, TestReturnType) -> Unit) {
  fun test() {
    val model = this.model
    model.deleteTable()
    val beforeVal = setUp(model)
    val testVal = test(model, beforeVal)
    assertResults(model, beforeVal, testVal)
  }
}

class DualTestCaseConfiguration {
  var testCaseCreator: ((TestModel<Any>) -> DualOperationTestCase<*, *, *>)? = null
  var testModels: Array<out TestModel<*>>? = null

  fun testCase(testCase: (TestModel<Any>) -> DualOperationTestCase<*, *, *>) {
    this.testCaseCreator = testCase
  }

  fun isSuccessfulFor(vararg models: TestModel<*>) {
    this.testModels = models
  }
}

class DualTestResult(val model: TestModel<*>) {
  var executeError: Throwable? = null
  var observeError: Throwable? = null
  val executeFailed get() = executeError != null
  val observeFailed get() = observeError != null
  val successful get() = executeError == null && observeError == null
}

@Suppress("UNCHECKED_CAST")
fun assertThatDual(testConfig: DualTestCaseConfiguration.() -> Unit) {
  val test = DualTestCaseConfiguration()
  testConfig(test)
  val testCaseCreator = checkNotNull(test.testCaseCreator) { "Test case missing" }
  val testModels = checkNotNull(test.testModels) { "Test models missing" }
  val fails = ArrayList<DualTestResult>()
  testModels.forEach { model ->
    val testResult = DualTestResult(model)
    val testCase = testCaseCreator(model as TestModel<Any>)
    try {
      testCase.testExecute()
    } catch (e: Throwable) {
      testResult.executeError = e
    }
    try {
      testCase.testObserve()
    } catch (e: Throwable) {
      testResult.observeError = e
    }
    if (!testResult.successful) {
      fails.add(testResult)
    }
  }
  if (fails.isNotEmpty()) {
    val testCase = testCaseCreator(testModels[0] as TestModel<Any>)
    val errorMessage = StringBuilder("Test case \"")
        .append(testCase.testName)
        .append("\" failed for models:\n")
    fails.forEach { fail ->
      errorMessage
          .append(fail.model.table)
          .append(" [")
      if (fail.executeFailed) {
        errorMessage.append("execute")
        fail.executeError?.printStackTrace()
      }
      if (fail.observeFailed) {
        if (fail.executeFailed) {
          errorMessage.append("|")
        }
        errorMessage.append("observe")
        fail.observeError?.printStackTrace()
      }
      errorMessage.append("]\n")
    }
    val error = AssertionError(errorMessage.toString())
    fails.forEach { fail ->
      if (fail.executeFailed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          error.addSuppressed(AssertionError("Execute failure for model ${fail.model.table}", fail.executeError!!))
        }
      }
      if (fail.observeFailed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          error.addSuppressed(AssertionError("Observe failure for model ${fail.model.table}", fail.observeError!!))
        }
      }
    }
    throw error
  }
}

open class DualOperationTestCase<PrepType, ModelType, TestReturnType>(
    val testName: String = "",
    private val model: TestModel<ModelType>,
    private val setUp: (TestModel<ModelType>) -> PrepType,
    operation: DualOperation<PrepType, ModelType, TestReturnType>,
    private val assertResults: (TestModel<ModelType>, PrepType, TestReturnType) -> Unit
) : DualOperation<PrepType, ModelType, TestReturnType> by operation {
  fun testExecute() {
    val model = model
    model.deleteTable()
    val beforeVal = setUp(model)
    val testVal = executeTest(model, beforeVal)
    assertResults(model, beforeVal, testVal)
  }

  fun testObserve() {
    val model = model
    model.deleteTable()
    val beforeVal = setUp(model)
    val testVal = observeTest(model, beforeVal)
    assertResults(model, beforeVal, testVal)
  }
}

interface DualOperation<PrepType, ModelType, TestReturnType> {
  fun executeTest(model: TestModel<ModelType>, testVal: PrepType): TestReturnType
  fun observeTest(model: TestModel<ModelType>, testVal: PrepType): TestReturnType
}

interface TestModel<T> {
  val table: Table<T>
  val idColumn: Column<Long, Long, Number, *>

  fun deleteTable()
  fun newRandom(): T
  fun setId(v: T, id: Long): T
  fun getId(v: T): Long?
  fun valsAreEqual(v1: T, v2: T): Boolean
  fun updateAllVals(v: T, id: Long): T
  fun insertBuilder(v: T): EntityInsertBuilder
  fun bulkInsertBuilder(v: Iterable<T>): EntityBulkInsertBuilder
  fun updateBuilder(v: T): EntityUpdateBuilder
  fun bulkUpdateBuilder(v: Iterable<T>): EntityBulkUpdateBuilder
  fun persistBuilder(v: T): EntityPersistBuilder
  fun bulkPersistBuilder(v: Iterable<T>): EntityBulkPersistBuilder
  fun deleteBuilder(v: T): EntityDeleteBuilder
  fun bulkDeleteBuilder(v: Collection<T>): EntityBulkDeleteBuilder
  fun deleteTableBuilder(): EntityDeleteTableBuilder
  fun assertNoValsInTables()
}

interface UniqueValued<T> {
  fun transferUniqueVal(src: T, target: T): T
}
interface ComplexUniqueValued<T> : UniqueValued<T> {
  fun getChildren(src: T): Map<ChildMetadata, List<Long>>
  fun transferComplexUniqueVal(src: T, target: T): T
  fun assertNoChildrenInDb(testVal: T) {
    getChildren(testVal)
        .forEach { (child, childIds) ->
          assertThat(Select
              .from(child.table)
              .where(child.idColumn.`in`(childIds))
              .count()
              .execute())
              .isEqualTo(0)
        }
  }

  fun assertChildrenCountPerParentCount(parents: List<T>) {
    getChildren(parents.first())
        .forEach { (child, childIds) ->
          assertThat(Select
              .from(child.table)
              .count()
              .execute())
              .isEqualTo(parents.size * childIds.size)
        }
  }
}

interface NullableColumns<T> {
  fun nullSomeColumns(target: T): T
  fun assertAllExceptNulledColumnsAreUpdated(target: T, nulledVal: T)
}

interface ComplexNullableColumns<T> : NullableColumns<T> {
  fun nullSomeComplexColumns(target: T): T
  fun assertAllExceptNulledComplexColumnsAreUpdated(target: T, nulledVal: T)
}

open class TestModelWithNullableColumns<T>(
    testModel: TestModel<T>,
    nullableColumns: NullableColumns<T>
) : TestModel<T> by testModel, NullableColumns<T> by nullableColumns

open class TestModelWithUniqueColumn<T>(
    testModel: TestModel<T>,
    uniqueValue: UniqueValued<T>
) : TestModel<T> by testModel, UniqueValued<T> by uniqueValue

open class TestModelWithUniqueNullableColumns<T>(
    testModel: TestModel<T>,
    uniqueValue: UniqueValued<T>,
    nullableColumns: NullableColumns<T>
) : TestModelWithUniqueColumn<T>(testModel, uniqueValue), NullableColumns<T> by nullableColumns

class ComplexTestModelWithNullableColumns<T>(
    testModel: TestModel<T>,
    nullableColumns: ComplexNullableColumns<T>
) : TestModelWithNullableColumns<T>(testModel, nullableColumns),
    ComplexNullableColumns<T> by nullableColumns

open class ComplexTestModelWithUniqueColumn<T>(
    testModel: TestModel<T>,
    uniqueValue: ComplexUniqueValued<T>
) : TestModelWithUniqueColumn<T>(testModel, uniqueValue),
    ComplexUniqueValued<T> by uniqueValue

class ComplexTestModelWithUniqueNullableColumns<T>(
    testModel: TestModel<T>,
    uniqueValue: ComplexUniqueValued<T>,
    nullableColumns: ComplexNullableColumns<T>
) : ComplexTestModelWithUniqueColumn<T>(testModel, uniqueValue),
    ComplexNullableColumns<T> by nullableColumns

data class ChildMetadata(
    val table: Table<*>,
    val idColumn: Column<Long, Long, Number, *>
)

fun <T> insertNewRandom(model: TestModel<T>): Pair<T, Long> {
  val newRandom = model.newRandom()
  val id = model.insertBuilder(newRandom).execute()
  assertThat(id).isNotEqualTo(-1)
  return newRandom to id
}

fun <T> assertTableDoesNotHaveValues(l: List<T>, model: TestModel<T>) {
  val tableValues = Select
      .from(model.table)
      .queryDeep()
      .execute()
  assertThat(tableValues).containsNoneIn(l)
}

fun <T> assertTableDoesNotHaveValue(v: T, model: TestModel<T>) {
  val tableValues = Select
      .from(model.table)
      .queryDeep()
      .execute()
  assertThat(tableValues).doesNotContain(v)
}

fun assertSingleError(ts: TestObserver<Long>) {
  ts.awaitTerminalEvent(1, TimeUnit.SECONDS)
  ts.assertNoValues()
  val errors = ts.errors()
  assertThat(errors.size).isEqualTo(1)
}

val ALL_FIXED_ID_MODELS = arrayOf<TestModel<*>>(
    *SIMPLE_FIXED_ID_MODELS,
    *COMPLEX_FIXED_ID_MODELS)

val ALL_NULLABLE_FIXED_ID_MODELS = arrayOf<TestModel<*>>(
    *SIMPLE_NULLABLE_FIXED_ID_MODELS,
    *COMPLEX_NULLABLE_FIXED_ID_MODELS)

val ALL_AUTO_ID_MODELS = arrayOf<TestModel<*>>(
    *SIMPLE_AUTO_ID_MODELS,
    *COMPLEX_AUTO_ID_MODELS)

val ALL_NULLABLE_AUTO_ID_MODELS = arrayOf<TestModel<*>>(
    *SIMPLE_NULLABLE_AUTO_ID_MODELS,
    *COMPLEX_NULLABLE_AUTO_ID_MODELS)
