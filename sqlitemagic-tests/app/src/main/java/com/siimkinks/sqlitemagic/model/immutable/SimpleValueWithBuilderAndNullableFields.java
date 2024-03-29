package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.SqliteMagic_SimpleValueWithBuilderAndNullableFields_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.IgnoreColumn;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.Collection;
import java.util.Random;

@Table(persistAll = true)
@AutoValue
public abstract class SimpleValueWithBuilderAndNullableFields implements ImmutableEquals {
  public static final String TABLE = "simple_value_with_builder_and_nullable_fields";
  public static final String C_ID = "simple_value_with_builder_and_nullable_fields.id";
  public static final String C_STRING = "simple_value_with_builder_and_nullable_fields.string";
  public static final String C_BOXED_BOOLEAN = "simple_value_with_builder_and_nullable_fields.boxed_boolean";
  public static final String C_BOXED_INTEGER = "simple_value_with_builder_and_nullable_fields.boxed_integer";

  @Id
  @Nullable
  public abstract Long id();

  @Nullable
  public abstract String string();

  @Nullable
  public abstract Boolean boxedBoolean();

  @Nullable
  public abstract Integer boxedInteger();

  public static Builder builder() {
    return new AutoValue_SimpleValueWithBuilderAndNullableFields.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder id(Long id);

    public abstract Builder string(String string);

    public abstract Builder boxedBoolean(Boolean boxedBoolean);

    public abstract Builder boxedInteger(Integer boxedInteger);

    public abstract SimpleValueWithBuilderAndNullableFields build();
  }

  @IgnoreColumn
  public abstract Builder copy();

  public static Builder newRandom() {
    final Random r = new Random();
    return builder()
        .id(r.nextLong())
        .string(Utils.randomTableName())
        .boxedBoolean(r.nextBoolean())
        .boxedInteger(r.nextInt());
  }

  public boolean equalsWithoutId(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof SimpleValueWithBuilderAndNullableFields) {
      SimpleValueWithBuilderAndNullableFields that = (SimpleValueWithBuilderAndNullableFields) o;
      return ((this.string() == null) ? (that.string() == null) : this.string().equals(that.string()))
          && ((this.boxedBoolean() == null) ? (that.boxedBoolean() == null) : this.boxedBoolean().equals(that.boxedBoolean()))
          && ((this.boxedInteger() == null) ? (that.boxedInteger() == null) : this.boxedInteger().equals(that.boxedInteger()));
    }
    return false;
  }

  @Override
  public Long provideId() {
    return id();
  }

  public SqliteMagic_SimpleValueWithBuilderAndNullableFields_Handler.InsertBuilder insert() {
    return SqliteMagic_SimpleValueWithBuilderAndNullableFields_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_SimpleValueWithBuilderAndNullableFields_Handler.UpdateBuilder update() {
    return SqliteMagic_SimpleValueWithBuilderAndNullableFields_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_SimpleValueWithBuilderAndNullableFields_Handler.PersistBuilder persist() {
    return SqliteMagic_SimpleValueWithBuilderAndNullableFields_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_SimpleValueWithBuilderAndNullableFields_Handler.DeleteBuilder delete() {
    return SqliteMagic_SimpleValueWithBuilderAndNullableFields_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_SimpleValueWithBuilderAndNullableFields_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_SimpleValueWithBuilderAndNullableFields_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_SimpleValueWithBuilderAndNullableFields_Handler.BulkInsertBuilder insert(Iterable<SimpleValueWithBuilderAndNullableFields> o) {
    return SqliteMagic_SimpleValueWithBuilderAndNullableFields_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_SimpleValueWithBuilderAndNullableFields_Handler.BulkUpdateBuilder update(Iterable<SimpleValueWithBuilderAndNullableFields> o) {
    return SqliteMagic_SimpleValueWithBuilderAndNullableFields_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_SimpleValueWithBuilderAndNullableFields_Handler.BulkPersistBuilder persist(Iterable<SimpleValueWithBuilderAndNullableFields> o) {
    return SqliteMagic_SimpleValueWithBuilderAndNullableFields_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_SimpleValueWithBuilderAndNullableFields_Handler.BulkDeleteBuilder delete(Collection<SimpleValueWithBuilderAndNullableFields> o) {
    return SqliteMagic_SimpleValueWithBuilderAndNullableFields_Handler.BulkDeleteBuilder.create(o);
  }
}
