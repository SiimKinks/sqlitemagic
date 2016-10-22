package com.siimkinks.sqlitemagic.model.view;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.CompiledSelect;
import com.siimkinks.sqlitemagic.annotation.View;
import com.siimkinks.sqlitemagic.annotation.ViewColumn;
import com.siimkinks.sqlitemagic.annotation.ViewQuery;

@AutoValue
@View
public abstract class SimpleCreatorView {
  @ViewQuery
  static final CompiledSelect QUERY = SimpleInterfaceView.QUERY;

  @ViewColumn("an")
  public abstract String authorName();

  @ViewColumn("mn")
  public abstract String magazineName();

  public static SimpleCreatorView create(String authorName, String magazineName) {
    return new AutoValue_SimpleCreatorView(authorName, magazineName);
  }
}