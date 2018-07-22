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
import com.siimkinks.sqlitemagic.model.SimpleMutableWithUnique;

import java.util.Random;

@Table(persistAll = true)
@AutoValue
public abstract class ComplexImmutableBuilderWithUnique {

  ComplexImmutableBuilderWithUnique() {
  }

  @Id(autoIncrement = false)
  public abstract long id();
  @Unique
  public abstract long uniqueVal();
  @Nullable
  public abstract String string();
  @Nullable
  public abstract SimpleMutableWithUnique complexVal();
  @Unique
  @NonNull
  public abstract SimpleMutableWithUnique complexVal2();

  @NonNull
  @CheckResult
  public static Builder builder() {
    return new AutoValue_ComplexImmutableBuilderWithUnique.Builder();
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
    public abstract Builder complexVal(@Nullable SimpleMutableWithUnique complexVal);
    public abstract Builder complexVal2(@NonNull SimpleMutableWithUnique complexVal2);
    @CheckResult
    public abstract ComplexImmutableBuilderWithUnique build();
  }

  public static ComplexImmutableBuilderWithUnique newRandom() {
    final Random r = new Random();
    return builder()
        .id(r.nextLong())
        .uniqueVal(r.nextLong())
        .string(Utils.randomTableName())
        .complexVal(SimpleMutableWithUnique.newRandom())
        .complexVal2(SimpleMutableWithUnique.newRandom())
        .build();
  }
}