package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.SqliteMagic_BuilderMagazine_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.IgnoreColumn;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.model.Author;

import java.util.Collection;
import java.util.Random;

@AutoValue
@Table(persistAll = true)
public abstract class BuilderMagazine implements ImmutableEquals {
  public static final String TABLE = "builder_magazine";
  public static final String C_ID = "builder_magazine.id";
  public static final String C_NAME = "builder_magazine.name";
  public static final String C_AUTHOR = "builder_magazine.author";
  public static final String C_SIMPLE_VALUE_WITH_BUILDER = "builder_magazine.simple_value_with_builder";
  public static final String C_SIMPLE_VALUE_WITH_CREATOR = "builder_magazine.simple_value_with_creator";

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

  @IgnoreColumn
  public abstract Builder copy();

  public static Builder builder() {
    return new AutoValue_BuilderMagazine.Builder();
  }

  public boolean equalsWithoutId(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof BuilderMagazine) {
      BuilderMagazine that = (BuilderMagazine) o;
      return ((this.name() == null) ? (that.name() == null) : this.name().equals(that.name()))
          && ((this.author() == null) ? (that.author() == null) : this.author().equals(that.author()))
          && ((this.simpleValueWithBuilder() == null) ? (that.simpleValueWithBuilder() == null) : this.simpleValueWithBuilder().equalsWithoutId(that.simpleValueWithBuilder()))
          && ((this.simpleValueWithCreator() == null) ? (that.simpleValueWithCreator() == null) : this.simpleValueWithCreator().equalsWithoutId(that.simpleValueWithCreator()));
    }
    return false;
  }

  @AutoValue.Builder
  public static abstract class Builder {
    public abstract Builder id(long id);

    public abstract Builder name(String name);

    public abstract Builder author(Author author);

    public abstract Builder simpleValueWithBuilder(SimpleValueWithBuilder simpleValueWithBuilder);

    public abstract Builder simpleValueWithCreator(SimpleValueWithCreator simpleValueWithCreator);

    public abstract BuilderMagazine build();
  }

  public static Builder newRandom() {
    final Random r = new Random();
    return builder()
        .id(r.nextLong())
        .name(Utils.randomTableName())
        .author(Author.newRandom())
        .simpleValueWithBuilder(SimpleValueWithBuilder.newRandom().build())
        .simpleValueWithCreator(SimpleValueWithCreator.newRandom());
  }

  @Override
  public Long provideId() {
    return id();
  }

  public SqliteMagic_BuilderMagazine_Handler.InsertBuilder insert() {
    return SqliteMagic_BuilderMagazine_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_BuilderMagazine_Handler.UpdateBuilder update() {
    return SqliteMagic_BuilderMagazine_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_BuilderMagazine_Handler.PersistBuilder persist() {
    return SqliteMagic_BuilderMagazine_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_BuilderMagazine_Handler.DeleteBuilder delete() {
    return SqliteMagic_BuilderMagazine_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_BuilderMagazine_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_BuilderMagazine_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_BuilderMagazine_Handler.BulkInsertBuilder insert(Iterable<BuilderMagazine> o) {
    return SqliteMagic_BuilderMagazine_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_BuilderMagazine_Handler.BulkUpdateBuilder update(Iterable<BuilderMagazine> o) {
    return SqliteMagic_BuilderMagazine_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_BuilderMagazine_Handler.BulkPersistBuilder persist(Iterable<BuilderMagazine> o) {
    return SqliteMagic_BuilderMagazine_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_BuilderMagazine_Handler.BulkDeleteBuilder delete(Collection<BuilderMagazine> o) {
    return SqliteMagic_BuilderMagazine_Handler.BulkDeleteBuilder.create(o);
  }
}