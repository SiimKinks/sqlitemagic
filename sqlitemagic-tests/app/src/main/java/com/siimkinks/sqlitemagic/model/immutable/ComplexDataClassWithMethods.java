package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.Nullable;

import com.siimkinks.sqlitemagic.SqliteMagic_ComplexDataClassWithMethods_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.model.Author;

import java.util.Collection;
import java.util.Random;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@Table(persistAll = true, useAccessMethods = true)
public final class ComplexDataClassWithMethods implements ImmutableEquals {
  @Id
  private final long id;

  @Nullable
  private final String name;

  @Nullable
  private final Author author;

  @Nullable
  private final SimpleValueWithBuilder simpleValueWithBuilder;

  @Nullable
  private final SimpleValueWithCreator simpleValueWithCreator;

  public ComplexDataClassWithMethods(long id,
                                     @Nullable String name,
                                     @Nullable Author author,
                                     @Nullable SimpleValueWithBuilder simpleValueWithBuilder,
                                     @Nullable SimpleValueWithCreator simpleValueWithCreator) {
    this.id = id;
    this.name = name;
    this.author = author;
    this.simpleValueWithBuilder = simpleValueWithBuilder;
    this.simpleValueWithCreator = simpleValueWithCreator;
  }

  public long getId() {
    return id;
  }

  @Nullable
  public String getName() {
    return name;
  }

  @Nullable
  public Author getAuthor() {
    return author;
  }

  @Nullable
  public SimpleValueWithBuilder getSimpleValueWithBuilder() {
    return simpleValueWithBuilder;
  }

  @Nullable
  public SimpleValueWithCreator getSimpleValueWithCreator() {
    return simpleValueWithCreator;
  }

  public boolean equalsWithoutId(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof ComplexDataClassWithMethods) {
      ComplexDataClassWithMethods that = (ComplexDataClassWithMethods) o;
      return ((this.name == null) ? (that.name == null) : this.name.equals(that.name))
          && ((this.author == null) ? (that.author == null) : this.author.equals(that.author))
          && ((this.simpleValueWithBuilder == null) ? (that.simpleValueWithBuilder == null) : this.simpleValueWithBuilder.equalsWithoutId(that.simpleValueWithBuilder))
          && ((this.simpleValueWithCreator == null) ? (that.simpleValueWithCreator == null) : this.simpleValueWithCreator.equalsWithoutId(that.simpleValueWithCreator));
    }
    return false;
  }

  public static ComplexDataClassWithMethods newRandom() {
    return new ComplexDataClassWithMethods(
        new Random().nextLong(),
        Utils.randomTableName(),
        Author.newRandom(),
        SimpleValueWithBuilder.newRandom().build(),
        SimpleValueWithCreator.newRandom()
    );
  }

  @Override
  public Long provideId() {
    return id;
  }

  public SqliteMagic_ComplexDataClassWithMethods_Handler.InsertBuilder insert() {
    return SqliteMagic_ComplexDataClassWithMethods_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_ComplexDataClassWithMethods_Handler.UpdateBuilder update() {
    return SqliteMagic_ComplexDataClassWithMethods_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_ComplexDataClassWithMethods_Handler.PersistBuilder persist() {
    return SqliteMagic_ComplexDataClassWithMethods_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_ComplexDataClassWithMethods_Handler.DeleteBuilder delete() {
    return SqliteMagic_ComplexDataClassWithMethods_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_ComplexDataClassWithMethods_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_ComplexDataClassWithMethods_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_ComplexDataClassWithMethods_Handler.BulkInsertBuilder insert(Iterable<ComplexDataClassWithMethods> o) {
    return SqliteMagic_ComplexDataClassWithMethods_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_ComplexDataClassWithMethods_Handler.BulkUpdateBuilder update(Iterable<ComplexDataClassWithMethods> o) {
    return SqliteMagic_ComplexDataClassWithMethods_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_ComplexDataClassWithMethods_Handler.BulkPersistBuilder persist(Iterable<ComplexDataClassWithMethods> o) {
    return SqliteMagic_ComplexDataClassWithMethods_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_ComplexDataClassWithMethods_Handler.BulkDeleteBuilder delete(Collection<ComplexDataClassWithMethods> o) {
    return SqliteMagic_ComplexDataClassWithMethods_Handler.BulkDeleteBuilder.create(o);
  }
}
