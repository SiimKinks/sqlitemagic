package com.siimkinks.sqlitemagic.model;

import android.support.annotation.NonNull;

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
public class ComplexMutableWithUnique {
  @Id(autoIncrement = false)
  long id;
  @Unique
  long uniqueVal;
  String string;
  SimpleMutableWithUnique complexVal;
  @Unique
  @NonNull
  SimpleMutableWithUnique complexVal2;

  public static ComplexMutableWithUnique newRandom() {
    final ComplexMutableWithUnique val = new ComplexMutableWithUnique();
    fillWithRandomValues(val);
    return val;
  }

  public static void fillWithRandomValues(ComplexMutableWithUnique val) {
    final Random r = new Random();
    val.id = Math.abs(r.nextLong());
    val.uniqueVal = r.nextLong();
    val.string = Utils.randomTableName();
    val.complexVal = SimpleMutableWithUnique.newRandom();
    val.complexVal2 = SimpleMutableWithUnique.newRandom();
  }
}
