package com.siimkinks.sqlitemagic.model;

import androidx.annotation.Nullable;

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
public class SimpleMutableWithUniqueAndNullableId {
  @Id
  @Nullable
  Long id;
  @Unique
  long uniqueVal;
  @Nullable
  String string;

  public long getUniqueVal() {
    return uniqueVal;
  }

  public void setUniqueVal(long uniqueVal) {
    this.uniqueVal = uniqueVal;
  }

  public static SimpleMutableWithUniqueAndNullableId newRandom() {
    final SimpleMutableWithUniqueAndNullableId val = new SimpleMutableWithUniqueAndNullableId();
    fillWithRandomValues(val);
    return val;
  }

  public static void fillWithRandomValues(SimpleMutableWithUniqueAndNullableId val) {
    final Random r = new Random();
    val.uniqueVal = r.nextLong();
    val.string = Utils.randomTableName();
  }
}
