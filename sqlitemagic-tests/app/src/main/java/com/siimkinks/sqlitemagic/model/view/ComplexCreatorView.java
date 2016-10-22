package com.siimkinks.sqlitemagic.model.view;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.CompiledSelect;
import com.siimkinks.sqlitemagic.annotation.View;
import com.siimkinks.sqlitemagic.annotation.ViewColumn;
import com.siimkinks.sqlitemagic.annotation.ViewQuery;
import com.siimkinks.sqlitemagic.model.immutable.BuilderMagazine;

@AutoValue
@View
public abstract class ComplexCreatorView {
  @ViewQuery
  static final CompiledSelect QUERY = ComplexInterfaceView.QUERY;

  @ViewColumn(ComplexInterfaceView.VALUE_W_BUILDER_ALIAS)
  public abstract String builderString();

  @ViewColumn(ComplexInterfaceView.MAGAZINE_ALIAS)
  public abstract BuilderMagazine builderMagazine();

  @ViewColumn(ComplexInterfaceView.AUTHOR_NAME_ALIAS)
  public abstract String authorName();
}
