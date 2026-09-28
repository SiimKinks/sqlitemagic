package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.DependencyOuterViewTable.Companion.DEPENDENCY_OUTER_VIEW
import com.siimkinks.sqlitemagic.MainSessionViewTable.Companion.MAIN_SESSION_VIEW
import com.siimkinks.sqlitemagic.PersistentEmailReadbackViewTable.Companion.PERSISTENT_EMAIL_READBACK_VIEW
import com.siimkinks.sqlitemagic.PersistentEmbeddedReadbackViewTable.Companion.PERSISTENT_EMBEDDED_READBACK_VIEW
import com.siimkinks.sqlitemagic.PersistentNestedReadbackViewTable.Companion.PERSISTENT_NESTED_READBACK_VIEW
import com.siimkinks.sqlitemagic.PersistentSourceSessionViewTable.Companion.PERSISTENT_SOURCE_SESSION_VIEW
import com.siimkinks.sqlitemagic.PersistentSubmoduleReadbackViewTable.Companion.PERSISTENT_SUBMODULE_READBACK_VIEW
import com.siimkinks.sqlitemagic.QueryCompositionAuthorViewTable.Companion.QUERY_COMPOSITION_AUTHOR_VIEW
import com.siimkinks.sqlitemagic.SubmoduleSessionViewTable.Companion.SUBMODULE_SESSION_VIEW
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.MainSessionValue
import com.siimkinks.sqlitemagic.fixture.view.DependencyOuterView
import com.siimkinks.sqlitemagic.fixture.view.MainSessionView
import com.siimkinks.sqlitemagic.fixture.view.PersistentContact
import com.siimkinks.sqlitemagic.fixture.view.PersistentEmailReadbackView
import com.siimkinks.sqlitemagic.fixture.view.PersistentEmbeddedReadbackView
import com.siimkinks.sqlitemagic.fixture.view.PersistentInnerReadbackView
import com.siimkinks.sqlitemagic.fixture.view.PersistentNestedReadbackView
import com.siimkinks.sqlitemagic.fixture.view.PersistentSourceSessionView
import com.siimkinks.sqlitemagic.fixture.view.PersistentSubmoduleReadbackView
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthor
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthorView
import com.siimkinks.sqlitemagic.fixture.view.ReaderEmail
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.SubmodulePersistentValue
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.SubmoduleSessionValue
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.SubmoduleSessionView
import com.siimkinks.sqlitemagic.runtime.model.ViewIntegrationCase
import com.siimkinks.sqlitemagic.runtime.model.ViewObservationCase
import com.siimkinks.sqlitemagic.runtime.model.ViewReadCase
import com.siimkinks.sqlitemagic.update

internal object ViewIntegrationCatalog {
  val cases: List<ViewIntegrationCase> = listOf(
    QueryCompositionAuthorViewCase,
    DependencyOuterViewCase,
    PersistentEmailReadbackViewCase,
    PersistentEmbeddedReadbackViewCase,
    PersistentNestedReadbackViewCase,
    PersistentSubmoduleReadbackViewCase,
    MainSessionViewCase,
    SubmoduleSessionViewCase,
    PersistentSourceSessionViewCase
  )

  val readCases = cases.filterIsInstance<ViewReadCase<*>>()
  val observationCases = cases.filterIsInstance<ViewObservationCase<*>>()
}

internal object QueryCompositionAuthorViewCase : ViewReadCase<QueryCompositionAuthorView>,
  ViewObservationCase<QueryCompositionAuthorView> {
  override val name = "QueryCompositionAuthorView"
  override fun toString() = name
  override val sourceModelName = "QueryCompositionAuthor"
  override val table = QUERY_COMPOSITION_AUTHOR_VIEW
  override val expected = QueryCompositionAuthorView(
    name = "Ada",
    id = 7L
  )
  override val insertedExpected = listOf(
    QueryCompositionAuthorView(
      name = "Ada",
      id = 1L
    )
  )
  override val updatedExpected = listOf(
    QueryCompositionAuthorView(
      name = "Grace",
      id = 1L
    )
  )

  override fun insertSource() = QueryCompositionAuthor(
    id = 1L,
    name = "Ada"
  )
    .insert()
    .execute()

  override fun updateSource() = QueryCompositionAuthor(
    id = 1L,
    name = "Grace"
  )
    .update()
    .execute()

  override fun insertAfterDisposal() = QueryCompositionAuthor(
    id = 2L,
    name = "Lin"
  )
    .insert()
    .execute()

  override fun insertSource(connection: DbConnection): EntityInsertResult = QueryCompositionAuthor(
    id = 7L,
    name = "Ada"
  )
    .insert()
    .usingConnection(connection)
    .execute()
}

internal object DependencyOuterViewCase : ViewObservationCase<DependencyOuterView> {
  override val name = "DependencyOuterView"
  override fun toString() = name
  override val table = DEPENDENCY_OUTER_VIEW
  override val insertedExpected = listOf(
    DependencyOuterView(name = "Ada")
  )
  override val updatedExpected = listOf(
    DependencyOuterView(name = "Grace")
  )

  override fun insertSource() = QueryCompositionAuthor(
    id = 91L,
    name = "Ada"
  )
    .insert()
    .execute()

  override fun updateSource() = QueryCompositionAuthor(
    id = 91L,
    name = "Grace"
  )
    .update()
    .execute()

  override fun insertAfterDisposal() = QueryCompositionAuthor(
    id = 92L,
    name = "Lin"
  )
    .insert()
    .execute()
}

internal object PersistentEmailReadbackViewCase : ViewReadCase<PersistentEmailReadbackView> {
  override val name = "PersistentEmailReadbackView"
  override fun toString() = name
  override val sourceModelName = "QueryCompositionAuthor"
  override val table = PERSISTENT_EMAIL_READBACK_VIEW
  override val expected = PersistentEmailReadbackView(email = ReaderEmail(value = "Ada"))

  override fun insertSource(connection: DbConnection): EntityInsertResult = QueryCompositionAuthor(
    id = 7L,
    name = "Ada"
  )
    .insert()
    .usingConnection(connection)
    .execute()
}

internal object PersistentEmbeddedReadbackViewCase : ViewReadCase<PersistentEmbeddedReadbackView>,
  ViewObservationCase<PersistentEmbeddedReadbackView> {
  override val name = "PersistentEmbeddedReadbackView"
  override fun toString() = name
  override val sourceModelName = "QueryCompositionAuthor"
  override val table = PERSISTENT_EMBEDDED_READBACK_VIEW
  override val expected = PersistentEmbeddedReadbackView(
    contact = PersistentContact(
      city = "Ada",
      zip = 7
    )
  )
  override val insertedExpected = listOf(expected)
  override val updatedExpected = listOf(
    PersistentEmbeddedReadbackView(
      contact = PersistentContact(
        city = "Grace",
        zip = 7
      )
    )
  )

  override fun insertSource() = QueryCompositionAuthor(
    id = 7L,
    name = "Ada"
  )
    .insert()
    .execute()

  override fun updateSource() = QueryCompositionAuthor(
    id = 7L,
    name = "Grace"
  )
    .update()
    .execute()

  override fun insertAfterDisposal() = QueryCompositionAuthor(
    id = 8L,
    name = "Lin"
  )
    .insert()
    .execute()

  override fun insertSource(connection: DbConnection): EntityInsertResult = QueryCompositionAuthor(
    id = 7L,
    name = "Ada"
  )
    .insert()
    .usingConnection(connection)
    .execute()
}

internal object PersistentNestedReadbackViewCase : ViewReadCase<PersistentNestedReadbackView>,
  ViewObservationCase<PersistentNestedReadbackView> {
  override val name = "PersistentNestedReadbackView"
  override fun toString() = name
  override val sourceModelName = "QueryCompositionAuthor"
  override val table = PERSISTENT_NESTED_READBACK_VIEW
  override val expected = PersistentNestedReadbackView(
    inner = PersistentInnerReadbackView(name = "Ada"),
    tail = "Ada"
  )
  override val insertedExpected = listOf(expected)
  override val updatedExpected = listOf(
    PersistentNestedReadbackView(
      inner = PersistentInnerReadbackView(name = "Grace"),
      tail = "Grace"
    )
  )

  override fun insertSource() = QueryCompositionAuthor(
    id = 7L,
    name = "Ada"
  )
    .insert()
    .execute()

  override fun updateSource() = QueryCompositionAuthor(
    id = 7L,
    name = "Grace"
  )
    .update()
    .execute()

  override fun insertAfterDisposal() = QueryCompositionAuthor(
    id = 9L,
    name = "Lin"
  )
    .insert()
    .execute()

  override fun insertSource(connection: DbConnection): EntityInsertResult = QueryCompositionAuthor(
    id = 7L,
    name = "Ada"
  )
    .insert()
    .usingConnection(connection)
    .execute()
}

internal object PersistentSubmoduleReadbackViewCase : ViewReadCase<PersistentSubmoduleReadbackView> {
  override val name = "PersistentSubmoduleReadbackView"
  override fun toString() = name
  override val sourceModelName = "SubmodulePersistentValue"
  override val table = PERSISTENT_SUBMODULE_READBACK_VIEW
  override val expected = PersistentSubmoduleReadbackView(
    id = "submodule-id",
    value = "submodule-value"
  )

  override fun insertSource(connection: DbConnection): EntityInsertResult = SubmodulePersistentValue(
    id = "submodule-id",
    value = "submodule-value"
  )
    .insert()
    .usingConnection(connection)
    .execute()
}

internal object MainSessionViewCase : ViewReadCase<MainSessionView> {
  override val name = "MainSessionView"
  override fun toString() = name
  override val sourceModelName = "MainSessionValue"
  override val table = MAIN_SESSION_VIEW
  override val expected = MainSessionView(
    id = "main",
    value = "main value"
  )

  override fun insertSource(connection: DbConnection): EntityInsertResult = MainSessionValue(
    id = "main",
    value = "main value"
  )
    .insert()
    .usingConnection(connection)
    .execute()
}

internal object SubmoduleSessionViewCase : ViewReadCase<SubmoduleSessionView> {
  override val name = "SubmoduleSessionView"
  override fun toString() = name
  override val sourceModelName = "SubmoduleSessionValue"
  override val table = SUBMODULE_SESSION_VIEW
  override val expected = SubmoduleSessionView(
    id = "submodule",
    value = "submodule value"
  )

  override fun insertSource(connection: DbConnection): EntityInsertResult = SubmoduleSessionValue(
    id = "submodule",
    value = "submodule value"
  )
    .insert()
    .usingConnection(connection)
    .execute()
}

internal object PersistentSourceSessionViewCase : ViewReadCase<PersistentSourceSessionView> {
  override val name = "PersistentSourceSessionView"
  override fun toString() = name
  override val sourceModelName = "QueryCompositionAuthor"
  override val table = PERSISTENT_SOURCE_SESSION_VIEW
  override val expected = PersistentSourceSessionView(
    id = 41L,
    name = "persistent value"
  )

  override fun insertSource(connection: DbConnection): EntityInsertResult = QueryCompositionAuthor(
    id = 41L,
    name = "persistent value"
  )
    .insert()
    .usingConnection(connection)
    .execute()
}
