package com.siimkinks.sqlitemagic.model.view;

import com.siimkinks.sqlitemagic.CompiledSelect;
import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.annotation.View;
import com.siimkinks.sqlitemagic.annotation.ViewColumn;
import com.siimkinks.sqlitemagic.annotation.ViewQuery;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithBuilder;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithCreator;

import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE;
import static com.siimkinks.sqlitemagic.Select.length;
import static com.siimkinks.sqlitemagic.SimpleValueWithBuilderTable.SIMPLE_VALUE_WITH_BUILDER;
import static com.siimkinks.sqlitemagic.SimpleValueWithCreatorTable.SIMPLE_VALUE_WITH_CREATOR;

@View
public interface InterfaceView {
  @ViewQuery
  CompiledSelect QUERY = Select
      .columns(
          length(MAGAZINE.NAME).as("magLen"),
          SIMPLE_VALUE_WITH_BUILDER.all().as("svwb"),
          AUTHOR.NAME.as("an"),
          SIMPLE_VALUE_WITH_CREATOR.all())
      .from(MAGAZINE)
      .join(SIMPLE_VALUE_WITH_BUILDER)
      .join(SIMPLE_VALUE_WITH_CREATOR)
      .queryDeep()
      .compile();

  @ViewColumn("magLen")
  long magazineNameLen();

  @ViewColumn("an")
  String authorName();

  @ViewColumn("svwb")
  SimpleValueWithBuilder simpleBuilder();

  @ViewColumn("simple_value_with_creator")
  SimpleValueWithCreator simpleCreator();
}
