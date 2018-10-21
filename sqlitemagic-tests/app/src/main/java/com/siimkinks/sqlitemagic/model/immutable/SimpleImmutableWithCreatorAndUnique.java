package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.annotation.Unique;

import java.util.Random;

@Table(persistAll = true)
@AutoValue
public abstract class SimpleImmutableWithCreatorAndUnique {
  SimpleImmutableWithCreatorAndUnique() {
  }

  @Id(autoIncrement = false)
  public abstract long id();

  @Unique
  public abstract long uniqueVal();

  @Nullable
  public abstract String string();

  @NonNull
  @CheckResult
  public static SimpleImmutableWithCreatorAndUnique create(long id, long uniqueVal, @Nullable String string) {
    return new AutoValue_SimpleImmutableWithCreatorAndUnique(id, uniqueVal, string);
  }

  @NonNull
  @CheckResult
  public SimpleImmutableWithCreatorAndUnique setId(long id) {
    return create(id, uniqueVal(), string());
  }

  @NonNull
  @CheckResult
  public SimpleImmutableWithCreatorAndUnique setUniqueVal(long uniqueVal) {
    return create(id(), uniqueVal, string());
  }

  public static SimpleImmutableWithCreatorAndUnique newRandom() {
    final Random r = new Random();
    return create(Math.abs(r.nextLong()), r.nextLong(), Utils.randomTableName());
  }

  public static SimpleImmutableWithCreatorAndUnique newRandomWithId(long id) {
    final Random r = new Random();
    return create(id, r.nextLong(), Utils.randomTableName());
  }
}