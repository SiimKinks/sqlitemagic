package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.SqliteMagic_CreatorMagazine_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.model.Author;

import java.util.Collection;
import java.util.Random;

@AutoValue
@Table(persistAll = true)
public abstract class CreatorMagazine implements ImmutableEquals {
  public static final String TABLE = "creator_magazine";
  public static final String C_ID = "creator_magazine.id";
  public static final String C_NAME = "creator_magazine.name";
  public static final String C_AUTHOR = "creator_magazine.author";

  @Id
  public abstract long id();

  @Nullable
  public abstract String name();

  @Nullable
  public abstract Author author();

  @Nullable
  public abstract SimpleValueWithBuilder simpleValueWithBuilder();

  @Nullable
  public abstract SimpleValueWithCreator simpleValueWithCreator();

  public static CreatorMagazine create(long id,
                                       String name,
                                       Author author,
                                       SimpleValueWithBuilder simpleValueWithBuilder,
                                       SimpleValueWithCreator simpleValueWithCreator) {
    return new AutoValue_CreatorMagazine(id, name, author, simpleValueWithBuilder, simpleValueWithCreator);
  }

  public boolean equalsWithoutId(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof CreatorMagazine) {
      CreatorMagazine that = (CreatorMagazine) o;
      return ((this.name() == null) ? (that.name() == null) : this.name().equals(that.name()))
          && ((this.author() == null) ? (that.author() == null) : this.author().equals(that.author()))
          && ((this.simpleValueWithBuilder() == null) ? (that.simpleValueWithBuilder() == null) : this.simpleValueWithBuilder().equalsWithoutId(that.simpleValueWithBuilder()))
          && ((this.simpleValueWithCreator() == null) ? (that.simpleValueWithCreator() == null) : this.simpleValueWithCreator().equalsWithoutId(that.simpleValueWithCreator()));
    }
    return false;
  }

  public static CreatorMagazine newRandom() {
    return new AutoValue_CreatorMagazine(
        new Random().nextLong(),
        Utils.randomTableName(),
        Author.newRandom(),
        SimpleValueWithBuilder.newRandom().build(),
        SimpleValueWithCreator.newRandom()
    );
  }

  @Override
  public Long provideId() {
    return id();
  }

  public SqliteMagic_CreatorMagazine_Handler.InsertBuilder insert() {
    return SqliteMagic_CreatorMagazine_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_CreatorMagazine_Handler.UpdateBuilder update() {
    return SqliteMagic_CreatorMagazine_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_CreatorMagazine_Handler.PersistBuilder persist() {
    return SqliteMagic_CreatorMagazine_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_CreatorMagazine_Handler.DeleteBuilder delete() {
    return SqliteMagic_CreatorMagazine_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_CreatorMagazine_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_CreatorMagazine_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_CreatorMagazine_Handler.BulkInsertBuilder insert(Iterable<CreatorMagazine> o) {
    return SqliteMagic_CreatorMagazine_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_CreatorMagazine_Handler.BulkUpdateBuilder update(Iterable<CreatorMagazine> o) {
    return SqliteMagic_CreatorMagazine_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_CreatorMagazine_Handler.BulkPersistBuilder persist(Iterable<CreatorMagazine> o) {
    return SqliteMagic_CreatorMagazine_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_CreatorMagazine_Handler.BulkDeleteBuilder delete(Collection<CreatorMagazine> o) {
    return SqliteMagic_CreatorMagazine_Handler.BulkDeleteBuilder.create(o);
  }
}