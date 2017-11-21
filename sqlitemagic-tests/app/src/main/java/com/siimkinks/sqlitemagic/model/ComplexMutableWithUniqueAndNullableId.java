package com.siimkinks.sqlitemagic.model;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.annotation.Unique;

import java.util.Random;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Table(persistAll = true)
public class ComplexMutableWithUniqueAndNullableId {
  @Id
  @Nullable
  Long id;
  @Unique
  long uniqueVal;
  String string;
  SimpleMutableWithUniqueAndNullableId complexVal;
  @Unique
  @NonNull
  SimpleMutableWithUniqueAndNullableId complexVal2;

  public static ComplexMutableWithUniqueAndNullableId newRandom() {
    final ComplexMutableWithUniqueAndNullableId val = new ComplexMutableWithUniqueAndNullableId();
    fillWithRandomValues(val);
    return val;
  }

  public static void fillWithRandomValues(ComplexMutableWithUniqueAndNullableId val) {
    final Random r = new Random();
    val.id = Math.abs(r.nextLong());
    val.uniqueVal = r.nextLong();
    val.string = Utils.randomTableName();
    val.complexVal = SimpleMutableWithUniqueAndNullableId.newRandom();
    val.complexVal2 = SimpleMutableWithUniqueAndNullableId.newRandom();
  }
}
