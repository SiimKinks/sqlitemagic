package com.siimkinks.sqlitemagic.model.view;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.CompiledSelect;
import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.annotation.View;
import com.siimkinks.sqlitemagic.annotation.ViewColumn;
import com.siimkinks.sqlitemagic.annotation.ViewQuery;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithBuilder;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithCreator;

import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE;
import static com.siimkinks.sqlitemagic.SimpleValueWithBuilderTable.SIMPLE_VALUE_WITH_BUILDER;
import static com.siimkinks.sqlitemagic.SimpleValueWithCreatorTable.SIMPLE_VALUE_WITH_CREATOR;

@View
@AutoValue
public abstract class ValueViewWithCreator {
	@ViewQuery
	static final CompiledSelect QUERY = Select
			.columns(
					AUTHOR.NAME.as("an"),
					MAGAZINE.NAME,
					SIMPLE_VALUE_WITH_CREATOR.all(),
					SIMPLE_VALUE_WITH_BUILDER.all()
			)
			.from(MAGAZINE)
			.join(AUTHOR)
			.join(SIMPLE_VALUE_WITH_BUILDER)
			.join(SIMPLE_VALUE_WITH_CREATOR)
			.where(MAGAZINE.AUTHOR.is(AUTHOR.ID))
			.queryDeep()
			.compile();

	@ViewColumn("magazine.name")
	public abstract String magazineName();

	@ViewColumn("an")
	public abstract String authorName();

	@ViewColumn("simplevaluewithbuilder")
	public abstract SimpleValueWithBuilder simpleBuilder();

	@ViewColumn("simplevaluewithcreator")
	public abstract SimpleValueWithCreator simpleCreator();
}