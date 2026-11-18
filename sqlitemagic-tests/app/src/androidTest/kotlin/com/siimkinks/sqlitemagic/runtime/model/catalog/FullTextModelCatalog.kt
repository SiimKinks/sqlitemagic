package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.LetterAuthorTable.Companion.LETTER_AUTHOR
import com.siimkinks.sqlitemagic.LetterAuthors
import com.siimkinks.sqlitemagic.LetterTable.Companion.LETTER
import com.siimkinks.sqlitemagic.Letters
import com.siimkinks.sqlitemagic.MemoTable.Companion.MEMO
import com.siimkinks.sqlitemagic.Memos
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.fulltext.Letter
import com.siimkinks.sqlitemagic.fixture.model.fulltext.LetterAuthor
import com.siimkinks.sqlitemagic.fixture.model.fulltext.Memo
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.RecursiveBulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.ReferencedDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardBulkDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardBulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardDeleteBuilders
import com.siimkinks.sqlitemagic.runtime.model.StandardOperationBuilders
import com.siimkinks.sqlitemagic.runtime.support.ReactiveAdapter
import com.siimkinks.sqlitemagic.update

internal object FullTextModelCatalog {
  val cases: List<RuntimeModelCase<*>> = listOf(
    LetterCase,
    MemoCase,
  )

  private object LetterCase :
    RecursiveBulkPersistModelCase<Letter>,
    StandardBulkDeleteModelCase<Letter>,
    ReferencedDeleteModelCase<Letter, LetterAuthor> {
    override val name = "Letter"
    override val table = LETTER
    override val relatedTable = LETTER_AUTHOR
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = Letter(
      id = sequence.toLong(),
      subject = "letter-subject-$sequence",
      body = "letter-body-$sequence",
      tag = "letter-tag-$sequence",
      language = sequence % 2,
      author = LetterAuthor(
        id = sequence.toLong(),
        name = "letter-author-$sequence"
      )
    )

    override val operationBuilders = StandardOperationBuilders(
      insert = Letter::insert,
      bulkInsert = Letters::insert,
      update = Letter::update,
      bulkUpdate = Letters::update,
      persist = Letter::persist,
      bulkPersist = Letters::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = Letter::delete,
      bulkDelete = Letters::delete,
      deleteTable = Letters::deleteTable
    )

    override fun relatedDeleteValues(value: Letter) = listOf(value.author)

    override fun executeRelatedDelete(value: LetterAuthor) = value
      .delete()
      .execute()

    override fun observeRelatedDelete(
      adapter: ReactiveAdapter,
      value: LetterAuthor
    ) = adapter.observe(value.delete())

    override fun executeRelatedBulkDelete(values: Collection<LetterAuthor>) = LetterAuthors
      .delete(o = values)
      .execute()

    override fun observeRelatedBulkDelete(
      adapter: ReactiveAdapter,
      values: Collection<LetterAuthor>
    ) = adapter.observe(LetterAuthors.delete(o = values))

    override fun executeRelatedTableDelete() = LetterAuthors
      .deleteTable()
      .execute()

    override fun observeRelatedTableDelete(adapter: ReactiveAdapter) = adapter.observe(LetterAuthors.deleteTable())

    override fun expectedAfterInsert(value: Letter, result: EntityInsertResult.Inserted) = value

    override fun updatedValue(value: Letter, sequence: Int) = value.copy(
      subject = "letter-subject-updated-$sequence",
      body = null,
      tag = "letter-tag-updated-$sequence",
      language = value.language + 1,
      author = value.author.copy(name = "letter-author-updated-$sequence")
    )

    override fun toString() = name
  }

  private object MemoCase :
    StandardBulkPersistModelCase<Memo>,
    StandardBulkDeleteModelCase<Memo> {
    override val name = "Memo"
    override val table = MEMO
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = Memo(
      code = "memo-$sequence",
      title = "memo-title-$sequence",
      text = "memo-text-$sequence"
    )

    override val operationBuilders = StandardOperationBuilders(
      insert = Memo::insert,
      bulkInsert = Memos::insert,
      update = Memo::update,
      bulkUpdate = Memos::update,
      persist = Memo::persist,
      bulkPersist = Memos::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = Memo::delete,
      bulkDelete = Memos::delete,
      deleteTable = Memos::deleteTable
    )

    override fun expectedAfterInsert(value: Memo, result: EntityInsertResult.Inserted) = value.copy(
      rowId = checkNotNull(result.rowId)
    )

    override fun expectedAfterBulkInsert(
      values: List<Memo>,
      actual: List<Memo>
    ) = actual.map { persisted ->
      values
        .single { it.code == persisted.code }
        .copy(rowId = persisted.rowId)
    }

    override fun updatedValue(value: Memo, sequence: Int) = value.copy(
      title = "memo-title-updated-$sequence",
      text = "memo-text-updated-$sequence"
    )

    override fun toString() = name
  }
}
