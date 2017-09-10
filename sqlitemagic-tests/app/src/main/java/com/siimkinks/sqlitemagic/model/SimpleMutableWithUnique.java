package com.siimkinks.sqlitemagic.model;

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
public class SimpleMutableWithUnique {
  @Id(autoIncrement = false)
  long id;
  @Unique
  long uniqueVal;
  @Nullable
  String string;

  @Nullable
  public String getString() {
    return string;
  }

  public static SimpleMutableWithUnique newRandom() {
    final SimpleMutableWithUnique val = new SimpleMutableWithUnique();
    fillWithRandomValues(val);
    return val;
  }

  public static void fillWithRandomValues(SimpleMutableWithUnique val) {
    final Random r = new Random();
    val.id = Math.abs(r.nextLong());
    val.uniqueVal = r.nextLong();
    val.string = Utils.randomTableName();
  }
}
