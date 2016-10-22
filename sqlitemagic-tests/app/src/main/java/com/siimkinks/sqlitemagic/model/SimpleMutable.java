package com.siimkinks.sqlitemagic.model;

import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.Random;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Table
public class SimpleMutable {

  public static final String TABLE = "simplemutable";
  public static final String C_ID = "simplemutable.id";

  @Id(autoIncrement = false)
  @Column
  long id;
  @Column
  String name;
  @Column
  long aLong;
  @Column
  String name2;

  public static SimpleMutable newRandom() {
    final SimpleMutable simpleMutable = new SimpleMutable();
    fillWithRandomValues(simpleMutable);
    return simpleMutable;
  }

  public static void fillWithRandomValues(SimpleMutable simpleMutable) {
    final Random r = new Random();
    simpleMutable.id = Math.abs(r.nextLong());
    simpleMutable.name = Utils.randomTableName();
    simpleMutable.aLong = r.nextLong();
    simpleMutable.name2 = Utils.randomTableName();
  }
}
