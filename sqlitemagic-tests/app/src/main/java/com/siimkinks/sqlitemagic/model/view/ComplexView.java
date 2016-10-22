package com.siimkinks.sqlitemagic.model.view;

import com.siimkinks.sqlitemagic.CompiledSelect;
import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.annotation.View;
import com.siimkinks.sqlitemagic.annotation.ViewColumn;
import com.siimkinks.sqlitemagic.annotation.ViewQuery;
import com.siimkinks.sqlitemagic.model.Author;

import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.BookTable.BOOK;
import static com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE;

@View
public interface ComplexView {
  @ViewQuery
  CompiledSelect QUERY = Select
      .columns(
          BOOK.NR_OF_RELEASES,
          BOOK.TITLE.as("booktitle"),
          MAGAZINE.NR_OF_RELEASES.as("mnr"),
          MAGAZINE.NAME.as("mn"),
          AUTHOR.all())
      .from(BOOK)
      .leftJoin(MAGAZINE.on(BOOK.AUTHOR.is(MAGAZINE.AUTHOR)))
      .queryDeep()
      .compile();

  @ViewColumn("booktitle")
  String bookTitle();

  @ViewColumn("book.nr_of_releases")
  int bookNrOfReleases();

  @ViewColumn("mn")
  String magazineName();

  @ViewColumn("mnr")
  int magazineNrOfReleases();

  @ViewColumn("author")
  Author author();
}
