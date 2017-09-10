package com.siimkinks.sqlitemagic.model.immutable;

import android.support.annotation.Nullable;

import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.model.Author;

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
}
