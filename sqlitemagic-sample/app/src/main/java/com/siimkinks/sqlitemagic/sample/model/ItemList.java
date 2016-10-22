package com.siimkinks.sqlitemagic.sample.model;

import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.CompiledCountSelect;
import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.ArrayList;
import java.util.List;

import static com.siimkinks.sqlitemagic.ItemListTable.ITEM_LIST;

@AutoValue
@Table(persistAll = true)
public abstract class ItemList implements Parcelable {
  public static final CompiledCountSelect COUNT = Select
      .from(ITEM_LIST)
      .count();

  @Id
  @Nullable
  public abstract Long id();

  public abstract String name();

  public abstract boolean archived();

  public static Builder builder() {
    return new AutoValue_ItemList.Builder();
  }

  @AutoValue.Builder
  public static abstract class Builder {
    abstract Builder id(@Nullable Long id);

    public abstract Builder name(@NonNull String name);

    public abstract Builder archived(boolean archived);

    public abstract ItemList build();
  }

  public static List<ItemList> createRandom(int count) {
    final ArrayList<ItemList> output = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      output.add(ItemList.builder()
          .name(Utils.randomTableName())
          .archived(false)
          .build());
    }
    return output;
  }
}