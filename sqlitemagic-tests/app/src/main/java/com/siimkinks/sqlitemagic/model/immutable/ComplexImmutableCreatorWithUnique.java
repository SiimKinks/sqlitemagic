package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.annotation.Unique;
import com.siimkinks.sqlitemagic.model.SimpleMutableWithUnique;

import java.util.Random;

@Table(persistAll = true)
@AutoValue
public abstract class ComplexImmutableCreatorWithUnique {

  ComplexImmutableCreatorWithUnique() {
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
  public static ComplexImmutableCreatorWithUnique newRandom() {
    final Random r = new Random();
    return new AutoValue_ComplexImmutableCreatorWithUnique(
        r.nextLong(),
        r.nextLong(),
        Utils.randomTableName(),
        SimpleMutableWithUnique.newRandom(),
        SimpleMutableWithUnique.newRandom()
    );
  }

  @NonNull
  @CheckResult
  public static ComplexImmutableCreatorWithUnique create(long id,
                                                         long uniqueVal,
                                                         @Nullable String string,
                                                         @Nullable SimpleMutableWithUnique complexVal1,
                                                         @NonNull SimpleMutableWithUnique complexVal2) {
    return new AutoValue_ComplexImmutableCreatorWithUnique(
        id,
        uniqueVal,
        string,
        complexVal1,
        complexVal2
    );
  }
}