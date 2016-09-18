package com.siimkinks.sqlitemagic.model;

import android.support.test.runner.AndroidJUnit4;

import com.siimkinks.sqlitemagic.CompiledCountSelect;
import com.siimkinks.sqlitemagic.CompiledDelete;
import com.siimkinks.sqlitemagic.Delete;
import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.model.immutable.BuilderMagazine;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Random;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.BookTable.BOOK;
import static com.siimkinks.sqlitemagic.BuilderMagazineTable.BUILDER_MAGAZINE;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertAuthors;

@RunWith(AndroidJUnit4.class)
public final class DeleteTest {
	final CompiledCountSelect COUNT_BOOKS = Select.from(BOOK).count();
	final CompiledCountSelect COUNT_AUTHORS = Select.from(AUTHOR).count();

	@Before
	public void setUp() {
		Book.deleteTable().execute();
		Author.deleteTable().execute();
	}

	@Test
	public void deleteSingle() {
		assertThat(COUNT_AUTHORS.execute()).isEqualTo(0);
		final int count = 10;
		final List<Author> authors = insertAuthors(count);
		final Author randomAuthor = authors.get(new Random().nextInt(count));
		assertThat(randomAuthor.delete().execute()).isEqualTo(1);
		assertThat(COUNT_AUTHORS.execute()).isEqualTo(count - 1);
	}

	@Test
	public void observeDeleteSingle() {
		assertThat(COUNT_AUTHORS.execute()).isEqualTo(0);
		final int count = 10;
		final List<Author> authors = insertAuthors(count);
		final Author randomAuthor = authors.get(new Random().nextInt(count));
		assertThat(randomAuthor.delete()
				.observe()
				.toBlocking()
				.value())
				.isEqualTo(1);
		assertThat(COUNT_AUTHORS.execute()).isEqualTo(count - 1);
	}

	@Test
	public void deleteTable() {
		final int count = 10;
		insertAuthors(count);
		assertThat(COUNT_AUTHORS.execute()).isEqualTo(count);
		assertThat(Author.deleteTable().execute()).isEqualTo(count);
		assertThat(COUNT_AUTHORS.execute()).isEqualTo(0);
	}

	@Test
	public void deleteTableObserve() {
		final int count = 10;
		insertAuthors(count);
		assertThat(COUNT_AUTHORS.execute()).isEqualTo(count);
		assertThat(Author.deleteTable()
				.observe()
				.toBlocking()
				.value())
				.isEqualTo(count);
		assertThat(COUNT_AUTHORS.execute()).isEqualTo(0);
	}

	@Test
	public void bulkDelete() {
		final int count = 20;
		final List<Author> authors = insertAuthors(count);
		final List<Author> deletedAuthors = authors.subList(5, 15);
		assertThat(COUNT_AUTHORS.execute()).isEqualTo(count);
		assertThat(Author.delete(deletedAuthors).execute()).isEqualTo(deletedAuthors.size());
		assertThat(COUNT_AUTHORS.execute()).isEqualTo(count - deletedAuthors.size());
	}

	@Test
	public void observeBulkDelete() {
		final int count = 20;
		final List<Author> authors = insertAuthors(count);
		final List<Author> deletedAuthors = authors.subList(5, 15);
		assertThat(COUNT_AUTHORS.execute()).isEqualTo(count);
		assertThat(Author.delete(deletedAuthors)
				.observe()
				.toBlocking()
				.value())
				.isEqualTo(deletedAuthors.size());
		assertThat(COUNT_AUTHORS.execute()).isEqualTo(count - deletedAuthors.size());
	}

	@Test
	public void deleteFrom() {
		final CompiledDelete compiledDelete = Delete.from(BOOK).compile();

		compiledDelete.execute();
		long count = COUNT_BOOKS.execute();
		assertThat(count).isEqualTo(0);

		final int testCount = 10;
		for (int i = 0; i < testCount; i++) {
			final long id = Book.newRandom().persist().execute();
			assertThat(id).isNotEqualTo(-1);
		}
		assertThat(COUNT_BOOKS.execute()).isEqualTo(testCount);

		assertThat(compiledDelete.execute()).isEqualTo(testCount);
		assertThat(COUNT_BOOKS.execute()).isEqualTo(0);
	}

	@Test
	public void deleteWhere() {
		final CompiledCountSelect compiledCountSelect = Select.from(BUILDER_MAGAZINE).count();
		Delete.from(BUILDER_MAGAZINE).execute();
		assertThat(compiledCountSelect.execute()).isEqualTo(0);

		final int testCount = 10;
		for (int i = 0; i < testCount; i++) {
			final BuilderMagazine magazine = BuilderMagazine.newRandom()
					.name("asd")
					.build();
			final long id = magazine.persist().execute();
			assertThat(id).isNotEqualTo(-1);
		}
		assertThat(BuilderMagazine.newRandom().build().persist().execute()).isNotEqualTo(-1);
		assertThat(BuilderMagazine.newRandom().build().persist().execute()).isNotEqualTo(-1);
		assertThat(BuilderMagazine.newRandom().build().persist().execute()).isNotEqualTo(-1);
		assertThat(compiledCountSelect.execute()).isEqualTo(testCount + 3);

		assertThat(Delete
				.from(BUILDER_MAGAZINE)
				.where(BUILDER_MAGAZINE.NAME.is("asd"))
				.execute())
				.isEqualTo(testCount);
		assertThat(compiledCountSelect.execute()).isEqualTo(3);
	}
}
