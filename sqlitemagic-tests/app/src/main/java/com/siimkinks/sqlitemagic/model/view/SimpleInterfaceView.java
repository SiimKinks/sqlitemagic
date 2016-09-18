package com.siimkinks.sqlitemagic.model.view;

import com.siimkinks.sqlitemagic.CompiledSelect;
import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.annotation.View;
import com.siimkinks.sqlitemagic.annotation.ViewColumn;
import com.siimkinks.sqlitemagic.annotation.ViewQuery;

import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE;

@View
public interface SimpleInterfaceView {
	@ViewQuery
	CompiledSelect QUERY = Select
			.columns(
					MAGAZINE.NAME.as("mn"),
					AUTHOR.NAME.as("an"))
			.from(MAGAZINE)
			.queryDeep()
			.compile();

	@ViewColumn("an")
	String authorName();

	@ViewColumn("mn")
	String magazineName();
}
