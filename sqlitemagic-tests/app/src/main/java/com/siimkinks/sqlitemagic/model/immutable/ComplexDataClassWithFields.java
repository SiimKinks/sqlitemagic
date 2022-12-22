package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.Nullable;

import com.siimkinks.sqlitemagic.SqliteMagic_ComplexDataClassWithFields_Handler;
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
@Table(persistAll = true)
public final class ComplexDataClassWithFields implements ImmutableEquals {
  @Id
  public final long id;

  @Nullable
  public final String name;

  @Nullable
  public final Author author;

  @Nullable
  public final SimpleValueWithBuilder simpleValueWithBuilder;

  @Nullable
  public final SimpleValueWithCreator simpleValueWithCreator;

  public ComplexDataClassWithFields(long id,
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

  public boolean equalsWithoutId(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof ComplexDataClassWithFields) {
      ComplexDataClassWithFields that = (ComplexDataClassWithFields) o;
      return ((this.name == null) ? (that.name == null) : this.name.equals(that.name))
          && ((this.author == null) ? (that.author == null) : this.author.equals(that.author))
          && ((this.simpleValueWithBuilder == null) ? (that.simpleValueWithBuilder == null) : this.simpleValueWithBuilder.equalsWithoutId(that.simpleValueWithBuilder))
          && ((this.simpleValueWithCreator == null) ? (that.simpleValueWithCreator == null) : this.simpleValueWithCreator.equalsWithoutId(that.simpleValueWithCreator));
    }
    return false;
  }

  public static ComplexDataClassWithFields newRandom() {
    return new ComplexDataClassWithFields(
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

  public SqliteMagic_ComplexDataClassWithFields_Handler.InsertBuilder insert() {
    return SqliteMagic_ComplexDataClassWithFields_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_ComplexDataClassWithFields_Handler.UpdateBuilder update() {
    return SqliteMagic_ComplexDataClassWithFields_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_ComplexDataClassWithFields_Handler.PersistBuilder persist() {
    return SqliteMagic_ComplexDataClassWithFields_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_ComplexDataClassWithFields_Handler.DeleteBuilder delete() {
    return SqliteMagic_ComplexDataClassWithFields_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_ComplexDataClassWithFields_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_ComplexDataClassWithFields_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_ComplexDataClassWithFields_Handler.BulkInsertBuilder insert(Iterable<ComplexDataClassWithFields> o) {
    return SqliteMagic_ComplexDataClassWithFields_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_ComplexDataClassWithFields_Handler.BulkUpdateBuilder update(Iterable<ComplexDataClassWithFields> o) {
    return SqliteMagic_ComplexDataClassWithFields_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_ComplexDataClassWithFields_Handler.BulkPersistBuilder persist(Iterable<ComplexDataClassWithFields> o) {
    return SqliteMagic_ComplexDataClassWithFields_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_ComplexDataClassWithFields_Handler.BulkDeleteBuilder delete(Collection<ComplexDataClassWithFields> o) {
    return SqliteMagic_ComplexDataClassWithFields_Handler.BulkDeleteBuilder.create(o);
  }
}
