package com.siimkinks.sqlitemagic.model;

import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.Random;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Table(persistAll = true)
public class ComplexMutable {
  @Id(autoIncrement = false)
  long id;
  String name;
  SimpleMutable complexVal;

  public static ComplexMutable newRandom() {
    final ComplexMutable val = new ComplexMutable();
    fillWithRandomValues(val);
    return val;
  }

  public static void fillWithRandomValues(ComplexMutable val) {
    final Random r = new Random();
    val.id = Math.abs(r.nextLong());
    val.name = Utils.randomTableName();
    val.complexVal = SimpleMutable.newRandom();
  }
}
