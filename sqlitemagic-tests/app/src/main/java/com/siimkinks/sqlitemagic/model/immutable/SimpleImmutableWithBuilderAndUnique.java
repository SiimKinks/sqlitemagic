package com.siimkinks.sqlitemagic.model.immutable;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.IgnoreColumn;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.annotation.Unique;

import java.util.Random;

@Table(persistAll = true)
@AutoValue
public abstract class SimpleImmutableWithBuilderAndUnique {

  SimpleImmutableWithBuilderAndUnique() {
  }

  @Id(autoIncrement = false)
  public abstract long id();

  @Unique
  public abstract long uniqueVal();

  @Nullable
  public abstract String string();

  @NonNull
  @CheckResult
  public static Builder builder() {
    return new AutoValue_SimpleImmutableWithBuilderAndUnique.Builder();
  }

  @NonNull
  @CheckResult
  @IgnoreColumn
  public abstract Builder copy();

  @AutoValue.Builder
  public static abstract class Builder {
    public abstract Builder id(long id);

    public abstract Builder uniqueVal(long uniqueVal);

    public abstract Builder string(@Nullable String string);

    @CheckResult
    public abstract SimpleImmutableWithBuilderAndUnique build();
  }

  public static SimpleImmutableWithBuilderAndUnique newRandom() {
    final Random r = new Random();
    return builder()
        .id(Math.abs(r.nextLong()))
        .uniqueVal(r.nextLong())
        .string(Utils.randomTableName())
        .build();
  }

  public static SimpleImmutableWithBuilderAndUnique newRandomWithId(long id) {
    final Random r = new Random();
    return builder()
        .id(id)
        .uniqueVal(r.nextLong())
        .string(Utils.randomTableName())
        .build();
  }
}