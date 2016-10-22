package com.siimkinks.sqlitemagic.model.immutable;

import android.support.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.model.TransformableObject;

import java.util.Random;

@Table(persistAll = true)
@AutoValue
public abstract class SimpleValueWithCreator implements ImmutableEquals {
  public static final String TABLE = "simplevaluewithcreator";
  public static final String C_ID = "simplevaluewithcreator.id";

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
}
