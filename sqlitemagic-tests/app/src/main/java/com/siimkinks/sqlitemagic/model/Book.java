package com.siimkinks.sqlitemagic.model;

import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.Random;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Table
@NoArgsConstructor
@AllArgsConstructor(suppressConstructorProperties = true)
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
    final Random r = new Random();
    final Book book = new Book();
    book.setBaseId(r.nextLong());
    book.author = Author.newRandom();
    book.title = Utils.randomTableName();
    book.nrOfReleases = r.nextInt();
    return book;
  }
}
