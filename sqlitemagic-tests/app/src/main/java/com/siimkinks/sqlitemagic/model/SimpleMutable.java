package com.siimkinks.sqlitemagic.model;

import com.siimkinks.sqlitemagic.SqliteMagic_SimpleMutable_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Column;
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
@Table
public class SimpleMutable {

  public static final String TABLE = "simple_mutable";
  public static final String C_ID = "simple_mutable.id";

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

  public SqliteMagic_SimpleMutable_Handler.InsertBuilder insert() {
    return SqliteMagic_SimpleMutable_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_SimpleMutable_Handler.UpdateBuilder update() {
    return SqliteMagic_SimpleMutable_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_SimpleMutable_Handler.PersistBuilder persist() {
    return SqliteMagic_SimpleMutable_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_SimpleMutable_Handler.DeleteBuilder delete() {
    return SqliteMagic_SimpleMutable_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_SimpleMutable_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_SimpleMutable_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_SimpleMutable_Handler.BulkInsertBuilder insert(Iterable<SimpleMutable> o) {
    return SqliteMagic_SimpleMutable_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_SimpleMutable_Handler.BulkUpdateBuilder update(Iterable<SimpleMutable> o) {
    return SqliteMagic_SimpleMutable_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_SimpleMutable_Handler.BulkPersistBuilder persist(Iterable<SimpleMutable> o) {
    return SqliteMagic_SimpleMutable_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_SimpleMutable_Handler.BulkDeleteBuilder delete(Collection<SimpleMutable> o) {
    return SqliteMagic_SimpleMutable_Handler.BulkDeleteBuilder.create(o);
  }
}
