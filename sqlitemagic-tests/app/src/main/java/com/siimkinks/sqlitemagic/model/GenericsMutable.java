package com.siimkinks.sqlitemagic.model;

import android.support.annotation.Nullable;

import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.annotation.Unique;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Table(persistAll = true)
public class GenericsMutable {
  @Id(autoIncrement = false)
  long id;
  @Unique
  long uniqueVal;
  @Nullable
  List<String> listOfStrings;
  @Nullable
  List<Integer> listOfInts;
  @Nullable
  Map<String, String> map;

  public static GenericsMutable newRandom() {
    final Random r = new Random();
    final GenericsMutable simpleMutable = new GenericsMutable();
    simpleMutable.id = Math.abs(r.nextLong());
    simpleMutable.uniqueVal = r.nextLong();
    simpleMutable.listOfStrings = randomListOfStrings();
    simpleMutable.listOfInts = randomListOfInts(r);
    simpleMutable.map = randomMap();
    return simpleMutable;
  }

  private static List<String> randomListOfStrings() {
    final ArrayList<String> output = new ArrayList<>(5);
    for (int i = 0; i < 5; i++) {
      output.add(Utils.randomTableName());
    }
    return output;
  }

  private static List<Integer> randomListOfInts(Random r) {
    final ArrayList<Integer> output = new ArrayList<>(5);
    for (int i = 0; i < 5; i++) {
      output.add(r.nextInt());
    }
    return output;
  }

  private static Map<String, String> randomMap() {
    final HashMap<String, String> output = new HashMap<>(5);
    for (int i = 0; i < 5; i++) {
      output.put(Utils.randomTableName(), Utils.randomTableName());
    }
    return output;
  }
}
