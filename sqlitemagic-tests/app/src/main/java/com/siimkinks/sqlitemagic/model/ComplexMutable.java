package com.siimkinks.sqlitemagic.model;

import com.siimkinks.sqlitemagic.SqliteMagic_ComplexMutable_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.Collection;
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

  public SqliteMagic_ComplexMutable_Handler.InsertBuilder insert() {
    return SqliteMagic_ComplexMutable_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_ComplexMutable_Handler.UpdateBuilder update() {
    return SqliteMagic_ComplexMutable_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_ComplexMutable_Handler.PersistBuilder persist() {
    return SqliteMagic_ComplexMutable_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_ComplexMutable_Handler.DeleteBuilder delete() {
    return SqliteMagic_ComplexMutable_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_ComplexMutable_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_ComplexMutable_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_ComplexMutable_Handler.BulkInsertBuilder insert(Iterable<ComplexMutable> o) {
    return SqliteMagic_ComplexMutable_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_ComplexMutable_Handler.BulkUpdateBuilder update(Iterable<ComplexMutable> o) {
    return SqliteMagic_ComplexMutable_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_ComplexMutable_Handler.BulkPersistBuilder persist(Iterable<ComplexMutable> o) {
    return SqliteMagic_ComplexMutable_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_ComplexMutable_Handler.BulkDeleteBuilder delete(Collection<ComplexMutable> o) {
    return SqliteMagic_ComplexMutable_Handler.BulkDeleteBuilder.create(o);
  }
}
