package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.SqliteMagic_SimpleValueWithBuilder_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.IgnoreColumn;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.model.ProvidesId;
import com.siimkinks.sqlitemagic.model.TransformableObject;

import java.util.Collection;
import java.util.Random;

@Table(persistAll = true)
@AutoValue
public abstract class SimpleValueWithBuilder implements ImmutableEquals, ProvidesId {
  public static final String TABLE = "simple_value_with_builder";
  public static final String C_ID = "simple_value_with_builder.id";
  public static final String C_STRING_VALUE = "simple_value_with_builder.string_value";

  @Id
  @Nullable
  public abstract Long id();

  public abstract String stringValue();

  abstract Boolean boxedBoolean();

  public abstract boolean aBoolean();

  public abstract int integer();

  abstract TransformableObject transformableObject();

  public static Builder builder() {
    return new AutoValue_SimpleValueWithBuilder.Builder();
  }

  @AutoValue.Builder
  public static abstract class Builder {
    public abstract Builder id(@Nullable Long id);

    public abstract Builder stringValue(String stringValue);

    public abstract Builder boxedBoolean(Boolean boxedBoolean);

    public abstract Builder aBoolean(boolean aBoolean);

    public abstract Builder integer(int integer);

    public abstract Builder transformableObject(TransformableObject transformableObject);

    public abstract SimpleValueWithBuilder build();
  }

  @IgnoreColumn
  public abstract Builder copy();

  public static Builder newRandom() {
    final Random random = new Random();
    return SimpleValueWithBuilder.builder()
        .stringValue(Utils.randomTableName())
        .boxedBoolean(random.nextBoolean())
        .aBoolean(random.nextBoolean())
        .integer(random.nextInt())
        .transformableObject(new TransformableObject(random.nextInt()));
  }

  public boolean equalsWithoutId(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof SimpleValueWithBuilder) {
      SimpleValueWithBuilder that = (SimpleValueWithBuilder) o;
      return (this.stringValue().equals(that.stringValue()))
          && (this.boxedBoolean().equals(that.boxedBoolean()))
          && (this.aBoolean() == that.aBoolean())
          && (this.integer() == that.integer())
          && (this.transformableObject().equals(that.transformableObject()));
    }
    return false;
  }

  @Override
  public Long provideId() {
    return id();
  }

  public SqliteMagic_SimpleValueWithBuilder_Handler.InsertBuilder insert() {
    return SqliteMagic_SimpleValueWithBuilder_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_SimpleValueWithBuilder_Handler.UpdateBuilder update() {
    return SqliteMagic_SimpleValueWithBuilder_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_SimpleValueWithBuilder_Handler.PersistBuilder persist() {
    return SqliteMagic_SimpleValueWithBuilder_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_SimpleValueWithBuilder_Handler.DeleteBuilder delete() {
    return SqliteMagic_SimpleValueWithBuilder_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_SimpleValueWithBuilder_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_SimpleValueWithBuilder_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_SimpleValueWithBuilder_Handler.BulkInsertBuilder insert(Iterable<SimpleValueWithBuilder> o) {
    return SqliteMagic_SimpleValueWithBuilder_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_SimpleValueWithBuilder_Handler.BulkUpdateBuilder update(Iterable<SimpleValueWithBuilder> o) {
    return SqliteMagic_SimpleValueWithBuilder_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_SimpleValueWithBuilder_Handler.BulkPersistBuilder persist(Iterable<SimpleValueWithBuilder> o) {
    return SqliteMagic_SimpleValueWithBuilder_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_SimpleValueWithBuilder_Handler.BulkDeleteBuilder delete(Collection<SimpleValueWithBuilder> o) {
    return SqliteMagic_SimpleValueWithBuilder_Handler.BulkDeleteBuilder.create(o);
  }
}
