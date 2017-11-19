package com.siimkinks.sqlitemagic.model;

import com.siimkinks.sqlitemagic.CompiledCountSelect;
import com.siimkinks.sqlitemagic.CompiledFirstSelect;
import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.Select.SelectN;
import com.siimkinks.sqlitemagic.SelectSqlNode;
import com.siimkinks.sqlitemagic.SimpleMutableWithNullableFields;
import com.siimkinks.sqlitemagic.SqliteMagic;
import com.siimkinks.sqlitemagic.Table;
import com.siimkinks.sqlitemagic.Transaction;
import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder;
import com.siimkinks.sqlitemagic.entity.EntityPersistBuilder;
import com.siimkinks.sqlitemagic.entity.EntityUpdateBuilder;
import com.siimkinks.sqlitemagic.model.immutable.ImmutableEquals;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithBuilder;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithBuilderAndNullableFields;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithCreator;
import com.siimkinks.sqlitemagic.model.immutable.SqliteMagic_SimpleValueWithCreator_Dao;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import io.reactivex.Observable;
import io.reactivex.functions.Function;
import io.reactivex.observers.TestObserver;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.SimpleValueWithBuilderTable.SIMPLE_VALUE_WITH_BUILDER;
import static java.util.concurrent.TimeUnit.SECONDS;

public final class TestUtil {
  public static final CompiledFirstSelect<Author, SelectN> SELECT_FIRST_AUTHOR = Select.from(AUTHOR).takeFirst();
  public static final CompiledCountSelect COUNT_AUTHORS = Select.from(AUTHOR).count();
  public static final CompiledFirstSelect<SimpleValueWithBuilder, SelectN> SELECT_FIRST_VAL = Select.from(SIMPLE_VALUE_WITH_BUILDER).takeFirst();
  public static final CompiledCountSelect COUNT_VALS = Select.from(SIMPLE_VALUE_WITH_BUILDER).count();

  public static <T> void testMutableObjectWithDefinedIdPersistAndRetrieve(boolean queryDeep,
                                                                          Class<T> testObjectClass,
                                                                          Table<T> table,
                                                                          T initialObject,
                                                                          EntityInsertBuilder insertBuilder,
                                                                          EntityUpdateBuilder updateBuilder,
                                                                          EntityPersistBuilder persistBuilder,
                                                                          DeleteCallback deleteCallback,
                                                                          UpdateObjectCallback<T> updateObjectCallback) {
    testMutableObjectPersistAndRetrieve(queryDeep, testObjectClass, table, initialObject, false, insertBuilder, updateBuilder, persistBuilder, deleteCallback, updateObjectCallback);
  }

  public static <T> void testMutableObjectPersistAndRetrieve(boolean queryDeep,
                                                             Class<T> testObjectClass,
                                                             Table<T> table,
                                                             T initialObject,
                                                             EntityInsertBuilder insertBuilder,
                                                             EntityUpdateBuilder updateBuilder,
                                                             EntityPersistBuilder persistBuilder,
                                                             DeleteCallback deleteCallback,
                                                             UpdateObjectCallback<T> updateObjectCallback) {
    testMutableObjectPersistAndRetrieve(queryDeep, testObjectClass, table, initialObject, true, insertBuilder, updateBuilder, persistBuilder, deleteCallback, updateObjectCallback);
  }

  private static <T> void testMutableObjectPersistAndRetrieve(boolean queryDeep,
                                                              Class<T> testObjectClass,
                                                              Table<T> table,
                                                              T initialObject,
                                                              boolean autoincrementedId,
                                                              EntityInsertBuilder insertBuilder,
                                                              EntityUpdateBuilder updateBuilder,
                                                              EntityPersistBuilder persistBuilder,
                                                              DeleteCallback deleteCallback,
                                                              UpdateObjectCallback<T> updateObjectCallback) {
    try {
      final Field idField = testObjectClass.getDeclaredField("id");
      SelectSqlNode.SelectNode<T, SelectN, ?> selectBuilder = Select
          .from(table);
      if (queryDeep) {
        selectBuilder = selectBuilder.queryDeep();
      }
      final CompiledFirstSelect<T, SelectN> compiledFirstSelect = selectBuilder
          .takeFirst();
      deleteCallback.deleteTable();
      // insert
      final Object idBeforeInsert = idField.get(initialObject);
      final long insertId = insertBuilder.execute();
      assertThat(insertId).isNotEqualTo(-1);
      if (autoincrementedId) {
        assertThat(idBeforeInsert).isNotEqualTo(insertId);
      } else {
        assertThat(idBeforeInsert).isEqualTo(insertId);
      }
      final Object objectId = idField.get(initialObject);
      assertThat(objectId).isEqualTo(insertId);
      assertThat(initialObject).isEqualTo(compiledFirstSelect.execute());
      // update
      updateObjectCallback.update(insertId, initialObject);
      assertThat(updateBuilder.execute()).isTrue();
      assertThat(idField.get(initialObject)).isEqualTo(insertId);
      assertThat(initialObject).isEqualTo(compiledFirstSelect.execute());
      // persist w/update
      updateObjectCallback.update(insertId, initialObject);
      assertThat(insertId).isEqualTo(persistBuilder.execute());
      assertThat(idField.get(initialObject)).isEqualTo(insertId);
      assertThat(initialObject).isEqualTo(compiledFirstSelect.execute());
      // persist w/insert
      deleteCallback.deleteTable();
      final long persistId = persistBuilder.execute();
      assertThat(persistId).isNotEqualTo(-1);
      if (autoincrementedId) {
        assertThat(persistId).isNotEqualTo(insertId);
      } else {
        assertThat(persistId).isEqualTo(insertId);
      }
      assertThat(idField.get(initialObject)).isEqualTo(persistId);
      assertThat(initialObject).isEqualTo(compiledFirstSelect.execute());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static List<Magazine> insertComplexValues(int count) {
    final Transaction transaction = SqliteMagic.newTransaction();
    try {
      final ArrayList<Magazine> values = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        Magazine a = Magazine.newRandom();
        assertThat(a.persist().execute()).isNotEqualTo(-1);
        values.add(a);
      }
      transaction.markSuccessful();
      return values;
    } finally {
      transaction.end();
    }
  }

  public static List<SimpleValueWithBuilder> insertBuilderSimpleValues(int count) {
    final Transaction transaction = SqliteMagic.newTransaction();
    try {
      final ArrayList<SimpleValueWithBuilder> values = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        final SimpleValueWithBuilder value = SimpleValueWithBuilder.newRandom().build();
        final long id = value.persist().execute();
        assertThat(id).isNotEqualTo(-1);
        values.add(value.copy().id(id).build());
      }
      transaction.markSuccessful();
      return values;
    } finally {
      transaction.end();
    }
  }

  public static List<SimpleValueWithCreator> insertCreatorSimpleValues(int count) {
    final Transaction transaction = SqliteMagic.newTransaction();
    try {
      final ArrayList<SimpleValueWithCreator> values = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        final SimpleValueWithCreator value = SimpleValueWithCreator.newRandom();
        final long id = value.persist().execute();
        assertThat(id).isNotEqualTo(-1);
        values.add(SqliteMagic_SimpleValueWithCreator_Dao.setId(value, id));
      }
      transaction.markSuccessful();
      return values;
    } finally {
      transaction.end();
    }
  }

  public static List<SimpleValueWithBuilderAndNullableFields> insertBuilderSimpleValuesAndNullableFields(int count) {
    final Transaction transaction = SqliteMagic.newTransaction();
    try {
      final ArrayList<SimpleValueWithBuilderAndNullableFields> values = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        final SimpleValueWithBuilderAndNullableFields value = SimpleValueWithBuilderAndNullableFields.newRandom().build();
        final long id = value.persist().execute();
        assertThat(id).isNotEqualTo(-1);
        values.add(value.copy().id(id).build());
      }
      transaction.markSuccessful();
      return values;
    } finally {
      transaction.end();
    }
  }

  public static ArrayList<SimpleMutableWithNullableFields> insertMutableWithNonNullFields(int count) {
    final Transaction transaction = SqliteMagic.newTransaction();
    try {
      final ArrayList<SimpleMutableWithNullableFields> vals = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        final SimpleMutableWithNullableFields a = SimpleMutableWithNullableFields.newRandom();
        assertThat(a.persist().execute()).isNotEqualTo(-1);
        vals.add(a);
      }
      transaction.markSuccessful();
      return vals;
    } finally {
      transaction.end();
    }
  }

  public static ArrayList<SimpleMutableWithNullableFields> insertMutableWithSomeNullFields(int count) {
    final Random random = new Random();
    final Transaction transaction = SqliteMagic.newTransaction();
    try {
      final ArrayList<SimpleMutableWithNullableFields> vals = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        final SimpleMutableWithNullableFields a = SimpleMutableWithNullableFields.newRandom();
        a.nullableString = random.nextBoolean() ? null : a.nullableString;
        assertThat(a.persist().execute()).isNotEqualTo(-1);
        vals.add(a);
      }
      transaction.markSuccessful();
      return vals;
    } finally {
      transaction.end();
    }
  }

  public static ArrayList<Author> insertAuthors(int count) {
    final Transaction transaction = SqliteMagic.newTransaction();
    try {
      final ArrayList<Author> authors = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        Author a = Author.newRandom();
        assertThat(a.insert().execute()).isNotEqualTo(-1);
        authors.add(a);
      }
      transaction.markSuccessful();
      return authors;
    } finally {
      transaction.end();
    }
  }

  public static List<Magazine> insertMagazines(int count) {
    final Transaction transaction = SqliteMagic.newTransaction();
    try {
      final ArrayList<Magazine> magazines = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        Magazine m = Magazine.newRandom();
        assertThat(m.insert().execute()).isNotEqualTo(-1);
        magazines.add(m);
      }
      transaction.markSuccessful();
      return magazines;
    } finally {
      transaction.end();
    }
  }

  public static List<SimpleMutable> insertSimpleValues(int count) {
    final Transaction transaction = SqliteMagic.newTransaction();
    try {
      final ArrayList<SimpleMutable> values = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        SimpleMutable a = SimpleMutable.newRandom();
        assertThat(a.persist().execute()).isNotEqualTo(-1);
        values.add(a);
      }
      Collections.sort(values, new Comparator<SimpleMutable>() {
        @Override
        public int compare(SimpleMutable lhs, SimpleMutable rhs) {
          return Long.valueOf(lhs.id).compareTo(rhs.id);
        }
      });
      transaction.markSuccessful();
      return values;
    } finally {
      transaction.end();
    }
  }

  public static List<SimpleAllValuesMutable> insertSimpleAllValues(int count) {
    final Transaction transaction = SqliteMagic.newTransaction();
    try {
      final ArrayList<SimpleAllValuesMutable> values = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        SimpleAllValuesMutable a = SimpleAllValuesMutable.newRandom();
        assertThat(a.persist().execute()).isNotEqualTo(-1);
        values.add(a);
      }
      Collections.sort(values, new Comparator<SimpleAllValuesMutable>() {
        @Override
        public int compare(SimpleAllValuesMutable lhs, SimpleAllValuesMutable rhs) {
          return Long.valueOf(lhs.id).compareTo(rhs.id);
        }
      });
      transaction.markSuccessful();
      return values;
    } finally {
      transaction.end();
    }
  }

  public static List<ComplexObjectWithSameLeafs> insertComplexValuesWithSameLeafs(int count) {
    final Transaction transaction = SqliteMagic.newTransaction();
    try {
      final ArrayList<ComplexObjectWithSameLeafs> values = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        ComplexObjectWithSameLeafs a = ComplexObjectWithSameLeafs.newRandom();
        assertThat(a.persist().execute()).isNotEqualTo(-1);
        values.add(a);
      }
      Collections.sort(values, new Comparator<ComplexObjectWithSameLeafs>() {
        @Override
        public int compare(ComplexObjectWithSameLeafs lhs, ComplexObjectWithSameLeafs rhs) {
          return Long.valueOf(lhs.id).compareTo(rhs.id);
        }
      });
      transaction.markSuccessful();
      return values;
    } finally {
      transaction.end();
    }
  }

  public static <T> List<T> createVals(Function<Integer, T> createFunc) {
    return Observable.range(0, 5)
        .map(createFunc)
        .toList()
        .blockingGet();
  }

  public static <T> List<T> updateVals(List<T> vals, Function<T, T> updateFunc) {
    return Observable.fromIterable(vals)
        .map(updateFunc)
        .toList()
        .blockingGet();
  }

  public static void assertTableCount(long count, Table<?> table) {
    assertThat(count).isEqualTo(Select.from(table).count().execute());
  }

  public static <T> void assertValueEqualsWithDb(T val, Table<T> table) {
    assertThat(val).isEqualTo(getDbValue(table));
  }

  public static <T> T getDbValue(Table<T> table) {
    return Select.from(table).queryDeep().takeFirst().execute();
  }

  public static <T> void assertValuesEqualWithDb(List<T> vals, Table<T> table) {
    assertThat(vals).containsExactlyElementsIn(Select.from(table).queryDeep().execute());
  }

  @Deprecated
  public static <T extends ImmutableEquals> void assertImmutableValue(T val, Table<T> table) {
    assertThat(val.equalsWithoutId(Select.from(table).queryDeep().takeFirst().execute())).isTrue();
  }

  public static <T extends ImmutableEquals> void assertValueEqualsWithoutIdWithDb(T val, Table<T> table) {
    assertThat(val.equalsWithoutId(Select.from(table).queryDeep().takeFirst().execute())).isTrue();
  }

  public static <T extends ImmutableEquals> void assertValuesEqualWithoutIdWithDb(List<T> val, Table<T> table) {
    final List<T> expected = Select.from(table).queryDeep().execute();

    assertThat(val.size()).isEqualTo(expected.size());

    final Iterator<T> valIter = val.iterator();
    final Iterator<T> expectedIter = expected.iterator();
    while (valIter.hasNext()) {
      assertThat(valIter.next().equalsWithoutId(expectedIter.next())).isTrue();
    }
  }

  public static void awaitTerminalEvent(TestObserver<?> ts) {
    ts.awaitTerminalEvent(10, SECONDS);
  }

  public interface UpdateObjectCallback<R> {
    void update(long insertedId, R object);
  }

  public interface CreateCallback<R> {
    R create(long id);
  }

  public interface DeleteCallback {
    void deleteTable();
  }
}
