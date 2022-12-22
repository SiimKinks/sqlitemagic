package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.SqliteMagic_SimpleValueWithCreator_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.model.ProvidesId;
import com.siimkinks.sqlitemagic.model.TransformableObject;

import java.util.Collection;
import java.util.Random;

@Table(persistAll = true)
@AutoValue
public abstract class SimpleValueWithCreator implements ImmutableEquals, ProvidesId {
  public static final String TABLE = "simple_value_with_creator";
  public static final String C_ID = "simple_value_with_creator.id";

  @Id
  @Nullable
  public abstract Long id();

  public abstract String stringValue();

  public abstract Boolean boxedBoolean();

  public abstract boolean aBoolean();

  public abstract int integer();

  public abstract TransformableObject transformableObject();

  public static SimpleValueWithCreator create(String stringValue, Boolean boxedBoolean, boolean aBoolean, int integer, TransformableObject transformableObject) {
    return new AutoValue_SimpleValueWithCreator(0L, stringValue, boxedBoolean, aBoolean, integer, transformableObject);
  }

  public static SimpleValueWithCreator createWithId(long id, String stringValue, Boolean boxedBoolean, boolean aBoolean, int integer, TransformableObject transformableObject) {
    return new AutoValue_SimpleValueWithCreator(id, stringValue, boxedBoolean, aBoolean, integer, transformableObject);
  }

  public static SimpleValueWithCreator newRandom() {
    return newRandom(null);
  }

  public static SimpleValueWithCreator newRandom(Long id) {
    final Random random = new Random();
    return SimpleValueWithCreator.createWithId(
        id == null ? 0L : id,
        Utils.randomTableName(),
        random.nextBoolean(),
        random.nextBoolean(),
        random.nextInt(),
        new TransformableObject(random.nextInt())
    );
  }

  public boolean equalsWithoutId(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof SimpleValueWithCreator) {
      SimpleValueWithCreator that = (SimpleValueWithCreator) o;
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

  public SqliteMagic_SimpleValueWithCreator_Handler.InsertBuilder insert() {
    return SqliteMagic_SimpleValueWithCreator_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_SimpleValueWithCreator_Handler.UpdateBuilder update() {
    return SqliteMagic_SimpleValueWithCreator_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_SimpleValueWithCreator_Handler.PersistBuilder persist() {
    return SqliteMagic_SimpleValueWithCreator_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_SimpleValueWithCreator_Handler.DeleteBuilder delete() {
    return SqliteMagic_SimpleValueWithCreator_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_SimpleValueWithCreator_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_SimpleValueWithCreator_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_SimpleValueWithCreator_Handler.BulkInsertBuilder insert(Iterable<SimpleValueWithCreator> o) {
    return SqliteMagic_SimpleValueWithCreator_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_SimpleValueWithCreator_Handler.BulkUpdateBuilder update(Iterable<SimpleValueWithCreator> o) {
    return SqliteMagic_SimpleValueWithCreator_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_SimpleValueWithCreator_Handler.BulkPersistBuilder persist(Iterable<SimpleValueWithCreator> o) {
    return SqliteMagic_SimpleValueWithCreator_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_SimpleValueWithCreator_Handler.BulkDeleteBuilder delete(Collection<SimpleValueWithCreator> o) {
    return SqliteMagic_SimpleValueWithCreator_Handler.BulkDeleteBuilder.create(o);
  }
}
