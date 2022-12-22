package com.siimkinks.sqlitemagic.model;

import com.siimkinks.sqlitemagic.SqliteMagic_Magazine_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.Collection;
import java.util.Random;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@EqualsAndHashCode
@ToString
@Getter
@Table(persistAll = true)
public class Magazine implements ProvidesId {

  public static final String TABLE = "magazine";
  public static final String C_ID = "magazine._id";
  public static final String C_NAME = "magazine.name";
  public static final String C_AUTHOR = "magazine.author";

  @Id
  @Column("_id")
  long id;
  String name;
  @Column(onDeleteCascade = true)
  Author author;
  int nrOfReleases;

  public static Magazine newRandom() {
    final Magazine magazine = new Magazine();
    fillWithRandomValues(magazine);
    return magazine;
  }

  public static Magazine newRandom(String name) {
    final Magazine magazine = new Magazine();
    fillWithRandomValues(magazine);
    if (name != null) {
      magazine.name = name;
    }
    return magazine;
  }

  public static void fillWithRandomValues(Magazine magazine) {
    magazine.name = Utils.randomTableName();
    magazine.author = Author.newRandom();
    magazine.nrOfReleases = new Random().nextInt();
  }

  @Override
  public Long provideId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Author getAuthor() {
    return author;
  }

  public int getNrOfReleases() {
    return nrOfReleases;
  }

  public SqliteMagic_Magazine_Handler.InsertBuilder insert() {
    return SqliteMagic_Magazine_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_Magazine_Handler.UpdateBuilder update() {
    return SqliteMagic_Magazine_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_Magazine_Handler.PersistBuilder persist() {
    return SqliteMagic_Magazine_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_Magazine_Handler.DeleteBuilder delete() {
    return SqliteMagic_Magazine_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_Magazine_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_Magazine_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_Magazine_Handler.BulkInsertBuilder insert(Iterable<Magazine> o) {
    return SqliteMagic_Magazine_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_Magazine_Handler.BulkUpdateBuilder update(Iterable<Magazine> o) {
    return SqliteMagic_Magazine_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_Magazine_Handler.BulkPersistBuilder persist(Iterable<Magazine> o) {
    return SqliteMagic_Magazine_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_Magazine_Handler.BulkDeleteBuilder delete(Collection<Magazine> o) {
    return SqliteMagic_Magazine_Handler.BulkDeleteBuilder.create(o);
  }
}
