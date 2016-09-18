package com.siimkinks.sqlitemagic.model.view;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.CompiledSelect;
import com.siimkinks.sqlitemagic.annotation.View;
import com.siimkinks.sqlitemagic.annotation.ViewColumn;
import com.siimkinks.sqlitemagic.annotation.ViewQuery;

@AutoValue
@View
public abstract class SimpleBuilderView {
	@ViewQuery
	static final CompiledSelect QUERY = SimpleInterfaceView.QUERY;

	@ViewColumn("an")
	public abstract String authorName();

	@ViewColumn("mn")
	public abstract String magazineName();

	public static Builder builder() {
		return new AutoValue_SimpleBuilderView.Builder();
	}

	@AutoValue.Builder
	public static abstract class Builder {

		public abstract Builder authorName(String authorName);

		public abstract Builder magazineName(String magazineName);

		public abstract SimpleBuilderView build();
	}
}