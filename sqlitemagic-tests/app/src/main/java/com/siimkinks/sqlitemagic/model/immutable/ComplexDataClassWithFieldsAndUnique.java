package com.siimkinks.sqlitemagic.model.immutable;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.annotation.Unique;
import com.siimkinks.sqlitemagic.model.SimpleMutableWithUnique;

import java.util.Random;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@Table(persistAll = true)
public final class ComplexDataClassWithFieldsAndUnique {
  @Id(autoIncrement = false)
  public final long id;

  @Unique
  public final long uniqueVal;

  public final String string;

  public final SimpleMutableWithUnique complexVal;

  @Unique
  @NonNull
  public final SimpleMutableWithUnique complexVal2;

  public ComplexDataClassWithFieldsAndUnique(long id,
                                             long uniqueVal,
                                             String string,
                                             SimpleMutableWithUnique complexVal,
                                             SimpleMutableWithUnique complexVal2) {
    this.id = id;
    this.uniqueVal = uniqueVal;
    this.string = string;
    this.complexVal = complexVal;
    this.complexVal2 = complexVal2;
  }

  @NonNull
  @CheckResult
  public static ComplexDataClassWithFieldsAndUnique newRandom() {
    final Random r = new Random();
    return new ComplexDataClassWithFieldsAndUnique(
        r.nextLong(),
        r.nextLong(),
        Utils.randomTableName(),
        SimpleMutableWithUnique.newRandom(),
        SimpleMutableWithUnique.newRandom()
    );
  }

  @NonNull
  @CheckResult
  public static ComplexDataClassWithFieldsAndUnique create(long id,
                                                           long uniqueVal,
                                                           @Nullable String string,
                                                           @Nullable SimpleMutableWithUnique complexVal1,
                                                           @NonNull SimpleMutableWithUnique complexVal2) {
    return new ComplexDataClassWithFieldsAndUnique(
        id,
        uniqueVal,
        string,
        complexVal1,
        complexVal2
    );
  }
}
