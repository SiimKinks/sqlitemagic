package com.siimkinks.sqlitemagic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.Random;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Table
@NoArgsConstructor
@ToString
@EqualsAndHashCode
public final class SimpleMutableWithNullableFields {
  @Id
  @Column
  public Long id;
  @Nullable
  @Column
  public String nullableString;
  @NonNull
  @Column
  public String nonNullString;

  public static SimpleMutableWithNullableFields newRandom() {
    final SimpleMutableWithNullableFields obj = new SimpleMutableWithNullableFields();
    fillWithRandomValues(obj);
    return obj;
  }

  public static void fillWithRandomValues(SimpleMutableWithNullableFields obj) {
    final Random r = new Random();
    obj.id = r.nextLong();
    obj.nullableString = Utils.randomTableName();
    obj.nonNullString = Utils.randomTableName();
  }
}
