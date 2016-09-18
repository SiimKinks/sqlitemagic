package com.siimkinks.sqlitemagic;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.BookTable.BOOK;
import static com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE;
import static com.siimkinks.sqlitemagic.SimpleAllValuesImmutableWithBuilderTable.SIMPLE_ALL_VALUES_IMMUTABLE_WITH_BUILDER;
import static com.siimkinks.sqlitemagic.SimpleAllValuesMutableTable.SIMPLE_ALL_VALUES_MUTABLE;

public final class ColumnTest {
	@Test
	public void columnEquals() {
		assertThat(BOOK.AUTHOR).isEqualTo(BOOK.AUTHOR);
		assertThat(BOOK.NR_OF_RELEASES).isEqualTo(BOOK.NR_OF_RELEASES);
		assertThat(AUTHOR.BOXED_BOOLEAN).isEqualTo(AUTHOR.BOXED_BOOLEAN);
		assertThat(AUTHOR.NAME).isEqualTo(AUTHOR.NAME);
	}

	@Test
	public void renamedTableColumnEquals() {
		final BookTable b = BOOK.as("b");
		final AuthorTable a = AUTHOR.as("a");

		assertThat(b.AUTHOR).isEqualTo(BOOK.AUTHOR);
		assertThat(b.AUTHOR).isEqualTo(b.AUTHOR);
		assertThat(b.NR_OF_RELEASES).isEqualTo(BOOK.NR_OF_RELEASES);
		assertThat(b.NR_OF_RELEASES).isEqualTo(b.NR_OF_RELEASES);
		assertThat(a.BOXED_BOOLEAN).isEqualTo(AUTHOR.BOXED_BOOLEAN);
		assertThat(a.BOXED_BOOLEAN).isEqualTo(a.BOXED_BOOLEAN);
		assertThat(a.NAME).isEqualTo(AUTHOR.NAME);
		assertThat(a.NAME).isEqualTo(a.NAME);
	}

	@Test
	public void sameTypeDifferentTableColumnsAreNotEqual() {
		assertThat(BOOK.AUTHOR).isNotEqualTo(MAGAZINE.AUTHOR);
		assertThat(SIMPLE_ALL_VALUES_MUTABLE.BOXED_BOOLEAN).isNotEqualTo(AUTHOR.BOXED_BOOLEAN);
		assertThat(SIMPLE_ALL_VALUES_MUTABLE.STRING).isNotEqualTo(SIMPLE_ALL_VALUES_IMMUTABLE_WITH_BUILDER.STRING);
		assertThat(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT).isNotEqualTo(SIMPLE_ALL_VALUES_IMMUTABLE_WITH_BUILDER.PRIMITIVE_INT);
	}

	@Test
	public void renamedSameTypeDifferentTableColumnsAreNotEqual() {
		final SimpleAllValuesMutableTable s = SIMPLE_ALL_VALUES_MUTABLE.as("s");
		final SimpleAllValuesImmutableWithBuilderTable sb = SIMPLE_ALL_VALUES_IMMUTABLE_WITH_BUILDER.as("sb");

		assertThat(BOOK.as("b").AUTHOR).isNotEqualTo(MAGAZINE.AUTHOR);
		assertThat(BOOK.as("b").AUTHOR).isNotEqualTo(MAGAZINE.as("m").AUTHOR);
		assertThat(s.BOXED_BOOLEAN).isNotEqualTo(AUTHOR.BOXED_BOOLEAN);
		assertThat(s.BOXED_BOOLEAN).isNotEqualTo(AUTHOR.as("a").BOXED_BOOLEAN);
		assertThat(s.STRING).isNotEqualTo(SIMPLE_ALL_VALUES_IMMUTABLE_WITH_BUILDER.STRING);
		assertThat(s.STRING).isNotEqualTo(sb.STRING);
		assertThat(s.PRIMITIVE_INT).isNotEqualTo(SIMPLE_ALL_VALUES_IMMUTABLE_WITH_BUILDER.PRIMITIVE_INT);
		assertThat(s.PRIMITIVE_INT).isNotEqualTo(sb.PRIMITIVE_INT);
	}
}
