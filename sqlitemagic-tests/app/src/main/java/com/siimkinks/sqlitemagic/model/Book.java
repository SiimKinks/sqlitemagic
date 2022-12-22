package com.siimkinks.sqlitemagic.model;

import com.siimkinks.sqlitemagic.SqliteMagic_Book_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.Collection;
import java.util.Random;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Table
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Book extends BaseModel {
  public static final String TABLE = "book";
  public static final String C_BASE_ID = "book.base_id";
  public static final String C_AUTHOR = "book.author";
  public static final String C_TITLE = "book.title";
  public static final String C_NR_OF_RELEASES = "book.nr_of_releases";

  @Column
  protected Author author;
  @Column
  protected String title;
  @Column
  protected int nrOfReleases;

  public static Book newRandom() {
    return newRandom(null);
  }

  public static Book newRandom(String title) {
    final Random r = new Random();
    final Book book = new Book();
    book.setBaseId(r.nextLong());
    book.author = Author.newRandom();
    book.title = title != null ? title : Utils.randomTableName();
    book.nrOfReleases = r.nextInt();
    return book;
  }

  public SqliteMagic_Book_Handler.InsertBuilder insert() {
    return SqliteMagic_Book_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_Book_Handler.UpdateBuilder update() {
    return SqliteMagic_Book_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_Book_Handler.PersistBuilder persist() {
    return SqliteMagic_Book_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_Book_Handler.DeleteBuilder delete() {
    return SqliteMagic_Book_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_Book_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_Book_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_Book_Handler.BulkInsertBuilder insert(Iterable<Book> o) {
    return SqliteMagic_Book_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_Book_Handler.BulkUpdateBuilder update(Iterable<Book> o) {
    return SqliteMagic_Book_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_Book_Handler.BulkPersistBuilder persist(Iterable<Book> o) {
    return SqliteMagic_Book_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_Book_Handler.BulkDeleteBuilder delete(Collection<Book> o) {
    return SqliteMagic_Book_Handler.BulkDeleteBuilder.create(o);
  }
}
