package com.siimkinks.sqlitemagic.sample.model;

import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.CountQueryObservable;
import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;

import static com.siimkinks.sqlitemagic.ItemTable.ITEM;

@AutoValue
@Table(persistAll = true)
public abstract class Item implements Parcelable {
  @Id
  @Nullable
  public abstract Long id();

  @Column(onDeleteCascade = true) // if item list is deleted, then all of its items are also deleted
  public abstract ItemList list();

  public abstract String description();

  public abstract boolean complete();

  public static Builder builder() {
    return new AutoValue_Item.Builder();
  }

  @AutoValue.Builder
  public static abstract class Builder {
    public abstract Builder id(@Nullable Long id);

    public abstract Builder list(@Nullable ItemList itemList);

    public abstract Builder description(@NonNull String description);

    public abstract Builder complete(boolean complete);

    public abstract Item build();
  }

  public static CountQueryObservable countItemsFor(@NonNull ItemList itemList) {
    return Select
        .from(ITEM)
        .where(ITEM.COMPLETE.is(false)
            .and(ITEM.LIST.is(itemList)))
        .count()
        .observe();
  }
}