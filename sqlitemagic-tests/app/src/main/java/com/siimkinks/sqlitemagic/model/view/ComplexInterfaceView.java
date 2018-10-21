package com.siimkinks.sqlitemagic.model.view;

import androidx.annotation.Nullable;

import com.siimkinks.sqlitemagic.CompiledSelect;
import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.annotation.View;
import com.siimkinks.sqlitemagic.annotation.ViewColumn;
import com.siimkinks.sqlitemagic.annotation.ViewQuery;
import com.siimkinks.sqlitemagic.model.immutable.BuilderMagazine;

import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.BuilderMagazineTable.BUILDER_MAGAZINE;
import static com.siimkinks.sqlitemagic.SimpleValueWithBuilderTable.SIMPLE_VALUE_WITH_BUILDER;
import static com.siimkinks.sqlitemagic.SimpleValueWithCreatorTable.SIMPLE_VALUE_WITH_CREATOR;

@View
public interface ComplexInterfaceView {
  String MAGAZINE_ALIAS = "bm";
  String AUTHOR_NAME_ALIAS = "am";
  String VALUE_W_BUILDER_ALIAS = "bs";

  @ViewQuery
  CompiledSelect QUERY = Select
      .columns(
          BUILDER_MAGAZINE.all().as(MAGAZINE_ALIAS),
          AUTHOR.all(),
          SIMPLE_VALUE_WITH_BUILDER.all(),
          SIMPLE_VALUE_WITH_CREATOR.all(),
          AUTHOR.NAME.as(AUTHOR_NAME_ALIAS),
          SIMPLE_VALUE_WITH_BUILDER.STRING_VALUE.as(VALUE_W_BUILDER_ALIAS))
      .from(BUILDER_MAGAZINE)
      .queryDeep()
      .compile();

  @ViewColumn(VALUE_W_BUILDER_ALIAS)
  String builderString();

  @ViewColumn(MAGAZINE_ALIAS)
  @Nullable
  BuilderMagazine builderMagazine();

  @ViewColumn(AUTHOR_NAME_ALIAS)
  @Nullable
  String authorName();
}
