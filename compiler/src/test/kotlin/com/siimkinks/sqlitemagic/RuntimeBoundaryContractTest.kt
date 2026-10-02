package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class RuntimeBoundaryContractTest {
  @Test
  fun `downstream Kotlin compiles against the typed internal adapter boundary`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "RuntimeBoundaryConsumer.kt",
          contents = """
          package downstream

          import com.siimkinks.sqlitemagic.internal.EntityAdapter
          import com.siimkinks.sqlitemagic.internal.EntityDefaultIdentityAdapter
          import com.siimkinks.sqlitemagic.internal.EntityGeneratedIdAdapter
          import com.siimkinks.sqlitemagic.internal.EntityIdentityAdapter
          import com.siimkinks.sqlitemagic.internal.EntityIdentityStatementBinder
          import com.siimkinks.sqlitemagic.internal.EntityRecursiveAdapter
          import com.siimkinks.sqlitemagic.internal.EntityRelationshipOperations
          import com.siimkinks.sqlitemagic.internal.EntityStatementBinder
          import com.siimkinks.sqlitemagic.internal.GeneratedEntityIdentity
          import com.siimkinks.sqlitemagic.internal.InsertBuilder
          import com.siimkinks.sqlitemagic.internal.PersistBuilder
          import com.siimkinks.sqlitemagic.internal.UpdateBuilder
          import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder
          import com.siimkinks.sqlitemagic.entity.EntityPersistBuilder
          import com.siimkinks.sqlitemagic.entity.EntityUpdateBuilder

          fun useAdapter(adapter: EntityAdapter<String>) = adapter
          fun useIdentityAdapter(adapter: EntityIdentityAdapter<String>) = adapter
          fun useStatementBinder(binder: EntityStatementBinder<String>) = binder
          fun useIdentityStatementBinder(binder: EntityIdentityStatementBinder<String>) = binder
          fun useAdapterAsStatementBinder(
            adapter: EntityAdapter<String>
          ): EntityStatementBinder<String> = adapter
          fun useIdentityAdapterAsStatementBinder(
            adapter: EntityIdentityAdapter<String>
          ): EntityIdentityStatementBinder<String> = adapter
          fun useDefaultIdentityAdapter(
            adapter: EntityDefaultIdentityAdapter<String>
          ) = adapter
          fun useRecursiveAdapter(adapter: EntityRecursiveAdapter<String>) = adapter
          fun useRelationshipOperations(operations: EntityRelationshipOperations) = operations
          fun useGeneratedIdAdapter(adapter: EntityGeneratedIdAdapter<String>) = adapter
          fun useIdentity(identity: GeneratedEntityIdentity) = identity.columnName
          fun useInsertBuilder(adapter: EntityAdapter<String>, entity: String): EntityInsertBuilder =
            InsertBuilder(
              adapter = adapter,
              entity = entity
            )
          fun useUpdateBuilder(
            adapter: EntityDefaultIdentityAdapter<String>,
            entity: String
          ): EntityUpdateBuilder = UpdateBuilder(
            adapter = adapter,
            entity = entity
          )
          fun usePersistBuilder(
            adapter: EntityDefaultIdentityAdapter<String>,
            entity: String
          ): EntityPersistBuilder = PersistBuilder(
            adapter = adapter,
            entity = entity
          )
        """
        ),
        processingStepsFactory = null
      )
      .isOk()
  }

  @Test
  fun `removed EntityOperationCoordination is unresolved downstream`() {
    assertRemovedRuntimeContract(
      fileName = "RemovedEntityOperationCoordinationConsumer.kt",
      typeName = "EntityOperationCoordination",
      source = """
        package downstream

        import com.siimkinks.sqlitemagic.internal.EntityOperationCoordination

        fun accessRemoved(value: EntityOperationCoordination<*>) = value
      """
    )
  }

  @Test
  fun `removed EntityInsertOperation is unresolved downstream`() {
    assertRemovedRuntimeContract(
      fileName = "RemovedEntityInsertOperationConsumer.kt",
      typeName = "EntityInsertOperation",
      source = """
        package downstream

        import com.siimkinks.sqlitemagic.internal.EntityInsertOperation

        fun accessRemoved(value: EntityInsertOperation<String>) = value
      """
    )
  }

  @Test
  fun `removed EntityUpdateOperation is unresolved downstream`() {
    assertRemovedRuntimeContract(
      fileName = "RemovedEntityUpdateOperationConsumer.kt",
      typeName = "EntityUpdateOperation",
      source = """
        package downstream

        import com.siimkinks.sqlitemagic.internal.EntityUpdateOperation

        fun accessRemoved(value: EntityUpdateOperation<String>) = value
      """
    )
  }

  @Test
  fun `removed EntityPersistOperation is unresolved downstream`() {
    assertRemovedRuntimeContract(
      fileName = "RemovedEntityPersistOperationConsumer.kt",
      typeName = "EntityPersistOperation",
      source = """
        package downstream

        import com.siimkinks.sqlitemagic.internal.EntityPersistOperation

        fun accessRemoved(value: EntityPersistOperation<String>) = value
      """
    )
  }

  @Test
  fun `removed EntityOperations is unresolved downstream`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "EntityOperationsConsumer.kt",
          contents = """
          package downstream

          import com.siimkinks.sqlitemagic.EntityOperations

          fun accessEngine() = EntityOperations
        """
        ),
        processingStepsFactory = null
      )
      .assertCompilationError("EntityOperations")
  }

  @Test
  fun `root-package GeneratedOperationContext is inaccessible downstream`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "GeneratedOperationContextConsumer.kt",
          contents = """
          package downstream

          import com.siimkinks.sqlitemagic.GeneratedOperationContext

          fun accessContext(context: GeneratedOperationContext) = context
        """
        ),
        processingStepsFactory = null
      )
      .assertCompilationError("GeneratedOperationContext")
  }

  @Test
  fun `removed NonRecursiveEntityHandler is not available downstream`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "RemovedHandlerConsumer.kt",
          contents = """
          package downstream

          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.NotNullable
          import com.siimkinks.sqlitemagic.NonRecursiveEntityHandler

          typealias AdapterColumn = Column<*, *, *, String, NotNullable>

          fun accessLegacy(handler: NonRecursiveEntityHandler<String, AdapterColumn>) = handler
        """
        ),
        processingStepsFactory = null
      )
      .assertCompilationError("NonRecursiveEntityHandler")
  }

  @Test
  fun `concrete operation builders are available from the generated-code-only package`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ConcreteBuilderConsumer.kt",
          contents = """
          package downstream

          import com.siimkinks.sqlitemagic.internal.EntityAdapter
          import com.siimkinks.sqlitemagic.internal.InsertBuilder
          import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder

          fun constructConcreteBuilder(
            adapter: EntityAdapter<String>,
            entity: String
          ): EntityInsertBuilder = InsertBuilder(
            adapter = adapter,
            entity = entity
          )
        """
        ),
        processingStepsFactory = null
      )
      .isOk()
  }

  @Test
  fun `runtime helper construction and trigger delivery remain inaccessible downstream`() {
    assertInaccessibleRuntimeContract(
      fileName = "OperationHelperConsumer.kt",
      source = """
        package downstream

        import com.siimkinks.sqlitemagic.OperationHelper

        fun constructHelper() = OperationHelper(0, 0, null)
      """
    )
    assertInaccessibleRuntimeContract(
      fileName = "VariableArgsOperationHelperConsumer.kt",
      source = """
        package downstream

        import com.siimkinks.sqlitemagic.VariableArgsOperationHelper

        fun constructHelper() = VariableArgsOperationHelper(0)
      """
    )
    assertInaccessibleRuntimeContract(
      fileName = "EntityDbManagerConsumer.kt",
      source = """
        package downstream

        import com.siimkinks.sqlitemagic.EntityDbManager

        fun accessManager(manager: EntityDbManager) = manager
      """
    )
  }

  private fun assertRemovedRuntimeContract(
    fileName: String,
    typeName: String,
    source: String
  ) {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = fileName,
          contents = source
        ),
        processingStepsFactory = null
      )
      .assertCompilationError(typeName)
  }

  private fun assertInaccessibleRuntimeContract(
    fileName: String,
    source: String
  ) {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = fileName,
          contents = source
        ),
        processingStepsFactory = null
      )
      .assertCompilationError()
  }
}
