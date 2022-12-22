package com.siimkinks.sqlitemagic.model;

import androidx.annotation.Nullable;

import com.siimkinks.sqlitemagic.SqliteMagic_GenericsMutable_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.annotation.Unique;

import java.util.ArrayList;
import java.util.Collection;
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

  public SqliteMagic_GenericsMutable_Handler.InsertBuilder insert() {
    return SqliteMagic_GenericsMutable_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_GenericsMutable_Handler.UpdateBuilder update() {
    return SqliteMagic_GenericsMutable_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_GenericsMutable_Handler.PersistBuilder persist() {
    return SqliteMagic_GenericsMutable_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_GenericsMutable_Handler.DeleteBuilder delete() {
    return SqliteMagic_GenericsMutable_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_GenericsMutable_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_GenericsMutable_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_GenericsMutable_Handler.BulkInsertBuilder insert(Iterable<GenericsMutable> o) {
    return SqliteMagic_GenericsMutable_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_GenericsMutable_Handler.BulkUpdateBuilder update(Iterable<GenericsMutable> o) {
    return SqliteMagic_GenericsMutable_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_GenericsMutable_Handler.BulkPersistBuilder persist(Iterable<GenericsMutable> o) {
    return SqliteMagic_GenericsMutable_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_GenericsMutable_Handler.BulkDeleteBuilder delete(Collection<GenericsMutable> o) {
    return SqliteMagic_GenericsMutable_Handler.BulkDeleteBuilder.create(o);
  }
}
