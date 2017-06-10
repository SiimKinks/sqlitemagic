package com.siimkinks.sqlitemagic.model;

import android.database.sqlite.SQLiteException;
import android.support.annotation.NonNull;
import android.support.test.runner.AndroidJUnit4;

import com.siimkinks.sqlitemagic.NumericColumn;
import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.SimpleMutableWithNullableFields;
import com.siimkinks.sqlitemagic.SqliteMagic;
import com.siimkinks.sqlitemagic.Transaction;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithCreator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import hu.akarnokd.rxjava2.math.MathObservable;
import io.reactivex.Observable;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Function;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE;
import static com.siimkinks.sqlitemagic.Select.abs;
import static com.siimkinks.sqlitemagic.Select.avg;
import static com.siimkinks.sqlitemagic.Select.groupConcat;
import static com.siimkinks.sqlitemagic.Select.length;
import static com.siimkinks.sqlitemagic.Select.lower;
import static com.siimkinks.sqlitemagic.Select.upper;
import static com.siimkinks.sqlitemagic.Select.val;
import static com.siimkinks.sqlitemagic.SimpleAllValuesMutableTable.SIMPLE_ALL_VALUES_MUTABLE;
import static com.siimkinks.sqlitemagic.SimpleMutableWithNullableFieldsTable.SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS;
import static com.siimkinks.sqlitemagic.SimpleValueWithCreatorTable.SIMPLE_VALUE_WITH_CREATOR;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertAuthors;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertMagazines;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertMutableWithNonNullFields;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertMutableWithSomeNullFields;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertSimpleAllValues;

@RunWith(AndroidJUnit4.class)
public final class SynchronousColumnQueryTest {

  public static final double SQLITE_DOUBLE_TOLERANCE = 0.9999999999;

  @Before
  public void setUp() {
    Author.deleteTable().execute();
    Magazine.deleteTable().execute();
    SimpleMutable.deleteTable().execute();
    SimpleMutableWithNullableFields.deleteTable().execute();
    SimpleValueWithCreator.deleteTable().execute();
    SimpleAllValuesMutable.deleteTable().execute();
  }

  @Test
  public void queryNonNullList() {
    final List<String> expected = Observable.fromIterable(insertMutableWithNonNullFields(5))
        .map(new Function<SimpleMutableWithNullableFields, String>() {
          @Override
          public String apply(SimpleMutableWithNullableFields author) {
            return author.nonNullString;
          }
        })
        .toList()
        .blockingGet();

    assertThat(Select
        .column(SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS.NON_NULL_STRING)
        .from(SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS)
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void queryNullableList() {
    final ArrayList<SimpleMutableWithNullableFields> inserts = insertMutableWithSomeNullFields(17);
    final ArrayList<String> expected = new ArrayList<>(inserts.size());
    for (SimpleMutableWithNullableFields insert : inserts) {
      expected.add(insert.nullableString);
    }

    assertThat(Select
        .column(SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS.NULLABLE_STRING)
        .from(SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS)
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void queryEmptyList() {
    assertThat(Select
        .column(SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS.NULLABLE_STRING)
        .from(SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS)
        .execute())
        .isEmpty();

    assertThat(Select
        .column(SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS.NON_NULL_STRING)
        .from(SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS)
        .execute())
        .isEmpty();
  }

  @Test
  public void queryComplexList() {
    final List<Long> expected = Observable.fromIterable(insertMagazines(7))
        .map(new Function<Magazine, Long>() {
          @Override
          public Long apply(Magazine magazine) {
            return magazine.author.id;
          }
        })
        .toList()
        .blockingGet();

    assertThat(Select
        .column(MAGAZINE.AUTHOR)
        .from(MAGAZINE)
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void queryTransformerList() {
    final List<Boolean> expected = Observable.fromIterable(insertAuthors(8))
        .map(new Function<Author, Boolean>() {
          @Override
          public Boolean apply(Author author) {
            return author.boxedBoolean;
          }
        })
        .toList()
        .blockingGet();

    assertThat(Select
        .column(AUTHOR.BOXED_BOOLEAN)
        .from(AUTHOR)
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void queryComplexChildColumn() {
    final List<String> expected = Observable.fromIterable(insertMagazines(7))
        .map(new Function<Magazine, String>() {
          @Override
          public String apply(Magazine magazine) {
            return magazine.author.name;
          }
        })
        .toList()
        .blockingGet();

    assertThat(Select
        .column(AUTHOR.NAME)
        .from(MAGAZINE)
        .execute())
        .isEqualTo(expected);
  }

  @Test(expected = SQLiteException.class)
  public void queryColumnListFromIrrelevantTableThrows() {
    final List<String> val = Select
        .column(AUTHOR.NAME)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .execute();
  }

  @Test
  public void count() {
    final int expected = 18;
    insertAuthors(expected);

    assertThat(Select
        .column(AUTHOR.ID)
        .from(AUTHOR)
        .count()
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void countEmpty() {
    assertThat(Select
        .column(AUTHOR.ID)
        .from(AUTHOR)
        .count()
        .execute())
        .isEqualTo(0);
  }

  @Test
  public void queryComplexChildColumnCount() {
    final long expected = 7;
    insertMagazines((int) expected);

    assertThat(Select
        .column(AUTHOR.NAME)
        .from(MAGAZINE)
        .count()
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void queryComplexChildFirstColumn() {
    final String expected = Observable.fromIterable(insertMagazines(7))
        .map(new Function<Magazine, String>() {
          @Override
          public String apply(Magazine magazine) {
            return magazine.author.name;
          }
        })
        .blockingFirst();

    assertThat(Select
        .column(AUTHOR.NAME)
        .from(MAGAZINE)
        .takeFirst()
        .execute())
        .isEqualTo(expected);
  }

  @Test(expected = SQLiteException.class)
  public void queryColumnFirstFromIrrelevantTableThrows() {
    final String val = Select
        .column(AUTHOR.NAME)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute();
  }

  @Test
  public void firstTransformerColumn() {
    final Boolean expected = insertAuthors(8).get(0).boxedBoolean;

    assertThat(Select
        .column(AUTHOR.BOXED_BOOLEAN)
        .from(AUTHOR)
        .takeFirst()
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void firstComplexColumn() {
    final Long expected = insertMagazines(8).get(0).author.id;

    assertThat(Select
        .column(MAGAZINE.AUTHOR)
        .from(MAGAZINE)
        .takeFirst()
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void firstString() {
    final Author first = insertAuthors(7).get(0);
    final String expected = first.name;

    assertThat(Select
        .column(AUTHOR.NAME)
        .from(AUTHOR)
        .takeFirst()
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void firstNullStringFromEmptyTable() {
    assertThat(Select
        .column(SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS.NULLABLE_STRING)
        .from(SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS)
        .takeFirst()
        .execute())
        .isNull();
  }

  @Test
  public void firstNotNullableColumnFromEmptyTable() {
    assertThat(Select
        .column(SIMPLE_VALUE_WITH_CREATOR.BOXED_BOOLEAN)
        .from(SIMPLE_VALUE_WITH_CREATOR)
        .takeFirst()
        .execute())
        .isNull();
  }

  @Test
  public void firstNullStringFromFilledTable() {
    insertMutableWithNonNullFields(7);

    assertThat(Select
        .column(SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS.NULLABLE_STRING)
        .from(SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS)
        .where(SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS.NON_NULL_STRING.isNull())
        .takeFirst()
        .execute())
        .isNull();
  }

  @Test
  public void firstNotNullableValueFromEmptyTable() {
    assertThat(Select
        .column(SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS.NON_NULL_STRING)
        .from(SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS)
        .takeFirst()
        .execute())
        .isNull();
  }

  @Test
  public void firstLong() {
    final SimpleAllValuesMutable first = insertSimpleAllValues(8).get(0);
    final long expectedPrimitive = first.primitiveLong;
    final Long expectedBoxed = first.boxedLong;

    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_LONG)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isEqualTo(expectedPrimitive);

    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_LONG)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isEqualTo(expectedBoxed);
  }

  @Test
  public void firstNullLong() {
    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_LONG)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isNull();
  }

  @Test
  public void firstNotNullableLongFromEmptyTable() {
    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_LONG)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isNull();
  }

  @Test
  public void firstInteger() {
    final SimpleAllValuesMutable first = insertSimpleAllValues(8).get(0);
    final int expectedPrimitive = first.primitiveInt;
    final Integer expectedBoxed = first.boxedInteger;

    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isEqualTo(expectedPrimitive);

    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_INTEGER)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isEqualTo(expectedBoxed);
  }

  @Test
  public void firstNullInteger() {
    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_INTEGER)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isNull();
  }

  @Test
  public void firstNotNullableIntegerFromEmptyTable() {
    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isNull();
  }

  @Test
  public void firstShort() {
    final SimpleAllValuesMutable first = insertSimpleAllValues(8).get(0);
    final short expectedPrimitive = first.primitiveShort;
    final Short expectedBoxed = first.boxedShort;

    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_SHORT)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isEqualTo(expectedPrimitive);

    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_SHORT)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isEqualTo(expectedBoxed);
  }

  @Test
  public void firstNullShort() {
    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_SHORT)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isNull();
  }

  @Test
  public void firstNotNullableShortFromEmptyTable() {
    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_SHORT)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isNull();
  }

  @Test
  public void firstDouble() {
    final SimpleAllValuesMutable first = insertSimpleAllValues(8).get(0);
    final double expectedPrimitive = first.primitiveDouble;
    final Double expectedBoxed = first.boxedDouble;

    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_DOUBLE)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isWithin(SQLITE_DOUBLE_TOLERANCE)
        .of(expectedPrimitive);

    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_DOUBLE)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isWithin(SQLITE_DOUBLE_TOLERANCE)
        .of(expectedBoxed);
  }

  @Test
  public void firstNullDouble() {
    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_DOUBLE)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isNull();
  }

  @Test
  public void firstNotNullableDoubleFromEmptyTable() {
    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_DOUBLE)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isNull();
  }

  @Test
  public void firstFloat() {
    final SimpleAllValuesMutable first = insertSimpleAllValues(8).get(0);
    final float expectedPrimitive = first.primitiveFloat;
    final Float expectedBoxed = first.boxedFloat;

    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_FLOAT)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isEqualTo(expectedPrimitive);

    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_FLOAT)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isEqualTo(expectedBoxed);
  }

  @Test
  public void firstNullFloat() {
    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_FLOAT)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isNull();
  }

  @Test
  public void firstNotNullableFloatFromEmptyTable() {
    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_FLOAT)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isNull();
  }

  @Test
  public void firstByte() {
    final SimpleAllValuesMutable first = insertSimpleAllValues(8).get(0);
    final byte expectedPrimitive = first.primitiveByte;
    final Byte expectedBoxed = first.boxedByte;

    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_BYTE)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isEqualTo(expectedPrimitive);

    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_BYTE)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isEqualTo(expectedBoxed);
  }

  @Test
  public void firstNullByte() {
    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_BYTE)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isNull();
  }

  @Test
  public void firstNotNullableByteFromEmptyTable() {
    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_BYTE)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isNull();
  }

  @Test
  public void firstByteArray() {
    final SimpleAllValuesMutable first = insertSimpleAllValues(8).get(0);
    final byte[] expectedPrimitive = first.primitiveByteArray;
    final Byte[] expectedBoxed = first.boxedByteArray;

    final byte[] result = Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_BYTE_ARRAY)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute();
    assertThat(result).isEqualTo(expectedPrimitive);

    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_BYTE_ARRAY)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isEqualTo(expectedBoxed);
  }

  @Test
  public void firstNullByteArray() {
    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_BYTE_ARRAY)
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isNull();
  }

  @Test
  public void aliasWithSpaces() {
    final int count = 8;
    final List<String> expected = Observable.fromIterable(insertAuthors(count))
        .map(new Function<Author, String>() {
          @Override
          public String apply(Author v) {
            return v.name;
          }
        })
        .toList()
        .blockingGet();

    assertThat(Select
        .column(AUTHOR.NAME.as("author name"))
        .from(AUTHOR)
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void transformableColumnVal() {
    final Author expected = Author.newRandom();
    expected.primitiveBoolean = true;
    expected.boxedBoolean = false;
    expected.insert().execute();

    assertThat(Select
        .from(AUTHOR)
        .where(AUTHOR.PRIMITIVE_BOOLEAN.is(val(true)).and(AUTHOR.BOXED_BOOLEAN.is(val(false))))
        .takeFirst()
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void avgFunction() {
    final double count = 8;
    final Integer sum = Observable.fromIterable(insertSimpleAllValues((int) count))
        .map(new Function<SimpleAllValuesMutable, Integer>() {
          @Override
          public Integer apply(SimpleAllValuesMutable v) {
            return (int) v.primitiveShort;
          }
        })
        .reduce(new BiFunction<Integer, Integer, Integer>() {
          @Override
          public Integer apply(Integer v1, Integer v2) {
            return v1 + v2;
          }
        })
        .blockingGet();

    final Double value = Select
        .column(avg(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_SHORT))
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute();
    assertThat(value).isEqualTo(sum.doubleValue() / count);
  }

  @Test
  public void countFunction() {
    final int expected = 8;
    insertAuthors(expected);

    assertThat(Select
        .column(Select.count())
        .from(AUTHOR)
        .takeFirst()
        .execute())
        .isEqualTo(expected);

    Author.deleteTable().execute();
    final ArrayList<Author> authors = new ArrayList<>();
    for (int i = 0; i < expected; i++) {
      final Author r = Author.newRandom();
      if (i % 2 == 0) {
        r.name = null;
      }
      authors.add(r);
    }
    Author.insert(authors).execute();

    assertThat(Select
        .column(Select.count(AUTHOR.NAME))
        .from(AUTHOR)
        .takeFirst()
        .execute())
        .isEqualTo(expected / 2);
  }

  @Test
  public void groupConcatFunction() {
    final int count = 8;
    final List<String> strings = Observable.fromIterable(insertAuthors(count))
        .map(new Function<Author, String>() {
          @Override
          public String apply(Author v) {
            return v.name;
          }
        })
        .toList()
        .blockingGet();

    assertThat(Select
        .column(groupConcat(AUTHOR.NAME))
        .from(AUTHOR)
        .takeFirst()
        .execute())
        .isEqualTo(StringUtil.join(",", strings));

    assertThat(Select
        .column(groupConcat(AUTHOR.NAME, " - "))
        .from(AUTHOR)
        .takeFirst()
        .execute())
        .isEqualTo(StringUtil.join(" - ", strings));
  }

  @Test
  public void maxFunction() {
    final List<SimpleAllValuesMutable> vals = insertSimpleAllValues(9);
    final Integer max = MathObservable.max(
        Observable.fromIterable(vals).map(new Function<SimpleAllValuesMutable, Integer>() {
          @Override
          public Integer apply(SimpleAllValuesMutable v) {
            return v.primitiveInt;
          }
        }))
        .blockingFirst();

    assertThat(Select
        .column(Select.max(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT))
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isEqualTo(max);

    Collections.sort(vals, new Comparator<SimpleAllValuesMutable>() {
      @Override
      public int compare(SimpleAllValuesMutable lhs, SimpleAllValuesMutable rhs) {
        return rhs.string.compareTo(lhs.string);
      }
    });
    assertThat(Select
        .column(Select.max(SIMPLE_ALL_VALUES_MUTABLE.STRING))
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isEqualTo(vals.get(0).string);
  }

  @Test
  public void minFunction() {
    final List<SimpleAllValuesMutable> vals = insertSimpleAllValues(9);
    final Integer min = MathObservable.min(
        Observable.fromIterable(vals)
            .map(new Function<SimpleAllValuesMutable, Integer>() {
              @Override
              public Integer apply(SimpleAllValuesMutable v) {
                return v.primitiveInt;
              }
            }))
        .blockingFirst();

    assertThat(Select
        .column(Select.min(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT))
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isEqualTo(min);

    Collections.sort(vals, new Comparator<SimpleAllValuesMutable>() {
      @Override
      public int compare(SimpleAllValuesMutable lhs, SimpleAllValuesMutable rhs) {
        return lhs.string.compareTo(rhs.string);
      }
    });
    assertThat(Select
        .column(Select.min(SIMPLE_ALL_VALUES_MUTABLE.STRING))
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isEqualTo(vals.get(0).string);
  }

  @Test
  public void sumFunction() {
    final Integer sum = MathObservable.sumInt(
        Observable.fromIterable(insertSimpleAllValues(9))
            .map(new Function<SimpleAllValuesMutable, Integer>() {
              @Override
              public Integer apply(SimpleAllValuesMutable v) {
                return (int) v.primitiveShort;
              }
            }))
        .blockingFirst();

    assertThat(Select
        .column(Select.sum(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_SHORT))
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .takeFirst()
        .execute())
        .isEqualTo(sum.doubleValue());
  }

  @Test
  public void concatFunction() {
    final List<String> expected = Observable.fromIterable(insertSimpleAllValues(5))
        .map(new Function<SimpleAllValuesMutable, String>() {
          @Override
          public String apply(SimpleAllValuesMutable v) {
            return v.string + v.primitiveShort;
          }
        })
        .toList()
        .blockingGet();

    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.STRING.concat(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_SHORT))
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .execute())
        .isEqualTo(expected);
  }

  static class Cls {
    @Override
    public String toString() {
      return "cls";
    }
  }

  @Test
  public void concatWithValFunction() {
    final List<SimpleAllValuesMutable> insertedVals = insertSimpleAllValues(5);
    List<String> expected = Observable.fromIterable(insertedVals)
        .map(new Function<SimpleAllValuesMutable, String>() {
          @Override
          public String apply(SimpleAllValuesMutable v) {
            return v.string + " - " + v.primitiveShort;
          }
        })
        .toList()
        .blockingGet();

    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.STRING.concat(val(" - ").concat(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_SHORT)))
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .execute())
        .isEqualTo(expected);

    expected = Observable.fromIterable(insertedVals)
        .map(new Function<SimpleAllValuesMutable, String>() {
          @Override
          public String apply(SimpleAllValuesMutable v) {
            return v.string + "8" + v.primitiveShort;
          }
        })
        .toList()
        .blockingGet();

    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.STRING.concat(val(8).concat(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_SHORT)))
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .execute())
        .isEqualTo(expected);

    expected = Observable.fromIterable(insertedVals)
        .map(new Function<SimpleAllValuesMutable, String>() {
          @Override
          public String apply(SimpleAllValuesMutable v) {
            return v.string + "cls" + v.primitiveShort;
          }
        })
        .toList()
        .blockingGet();

    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.STRING.concat(val(new Cls()).concat(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_SHORT)))
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .execute())
        .isEqualTo(expected);

    expected = Observable.fromIterable(insertedVals)
        .map(new Function<SimpleAllValuesMutable, String>() {
          @Override
          public String apply(SimpleAllValuesMutable v) {
            return v.string + "1" + v.primitiveShort;
          }
        })
        .toList()
        .blockingGet();

    assertThat(Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.STRING.concat(val(true).concat(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_SHORT)))
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void replaceFunction() {
    final List<String> expected = Observable.fromIterable(insertAuthors(10))
        .map(new Function<Author, String>() {
          @Override
          public String apply(Author author) {
            return author.name.replace("a", "___");
          }
        })
        .toList()
        .blockingGet();

    assertThat(Select
        .column(AUTHOR.NAME.replace("a", "___"))
        .from(AUTHOR)
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void substringFunction() {
    final List<String> expected = Observable.fromIterable(insertAuthors(10))
        .map(new Function<Author, String>() {
          @Override
          public String apply(Author author) {
            return author.name.substring(2);
          }
        })
        .toList()
        .blockingGet();

    assertThat(Select
        .column(AUTHOR.NAME.substring(3))
        .from(AUTHOR)
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void substringFunctionWithLen() {
    final List<String> expected = Observable.fromIterable(insertAuthors(10))
        .map(new Function<Author, String>() {
          @Override
          public String apply(Author author) {
            return author.name.substring(2, 4);
          }
        })
        .toList()
        .blockingGet();

    assertThat(Select
        .column(AUTHOR.NAME.substring(3, 2))
        .from(AUTHOR)
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void trimFunction() {
    final int count = 10;
    final ArrayList<String> expected = new ArrayList<>(count);
    final Transaction transaction = SqliteMagic.newTransaction();
    try {
      for (int i = 0; i < count; i++) {
        Author a = Author.newRandom();
        expected.add(a.name);
        a.name = "   " + a.name + "   ";
        assertThat(a.persist().execute()).isNotEqualTo(-1);
      }
      transaction.markSuccessful();
    } finally {
      transaction.end();
    }

    assertThat(Select
        .column(AUTHOR.NAME.trim())
        .from(AUTHOR)
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void trimFunctionWithParam() {
    final int count = 10;
    final ArrayList<String> expected = new ArrayList<>(count);
    final Transaction transaction = SqliteMagic.newTransaction();
    try {
      for (int i = 0; i < count; i++) {
        Author a = Author.newRandom();
        expected.add(a.name);
        a.name = "___" + a.name + "___";
        assertThat(a.persist().execute()).isNotEqualTo(-1);
      }
      transaction.markSuccessful();
    } finally {
      transaction.end();
    }

    assertThat(Select
        .column(AUTHOR.NAME.trim("___"))
        .from(AUTHOR)
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void absFunction() {
    final List<Integer> expected = Observable.fromIterable(insertSimpleAllValues(9))
        .map(new Function<SimpleAllValuesMutable, Integer>() {
          @Override
          public Integer apply(SimpleAllValuesMutable v) {
            return Math.abs(v.primitiveInt);
          }
        })
        .toList()
        .blockingGet();

    assertThat(Select
        .column(abs(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT))
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void lengthFunction() {
    final List<Long> expected = Observable.fromIterable(insertSimpleAllValues(9))
        .map(new Function<SimpleAllValuesMutable, Long>() {
          @Override
          public Long apply(SimpleAllValuesMutable v) {
            return (long) v.string.length();
          }
        })
        .toList()
        .blockingGet();

    assertThat(Select
        .column(length(SIMPLE_ALL_VALUES_MUTABLE.STRING))
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void lowerFunction() {
    final List<String> expected = Observable.fromIterable(insertSimpleAllValues(9))
        .map(new Function<SimpleAllValuesMutable, String>() {
          @Override
          public String apply(SimpleAllValuesMutable v) {
            return v.string.toLowerCase();
          }
        })
        .toList()
        .blockingGet();

    assertThat(Select
        .column(lower(SIMPLE_ALL_VALUES_MUTABLE.STRING))
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void upperFunction() {
    final List<String> expected = Observable.fromIterable(insertSimpleAllValues(9))
        .map(new Function<SimpleAllValuesMutable, String>() {
          @Override
          public String apply(SimpleAllValuesMutable v) {
            return v.string.toUpperCase();
          }
        })
        .toList()
        .blockingGet();

    assertThat(Select
        .column(upper(SIMPLE_ALL_VALUES_MUTABLE.STRING))
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void numericArithmeticExpressions() {
    final List<SimpleAllValuesMutable> vals = insertSimpleAllValues(7);
    assertArithmeticExpression(vals, new ArithmeticExpressionEvaluator() {
      @Override
      public NumericColumn column(@NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable> c1, @NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable> c2) {
        return c1.add(c2);
      }

      @Override
      public Double func(@NonNull Short v1, @NonNull Short v2) {
        return (double) v1 + v2;
      }
    });

    assertArithmeticExpression(vals, new ArithmeticExpressionEvaluator() {
      @Override
      public NumericColumn column(@NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable> c1, @NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable> c2) {
        return c1.sub(c2);
      }

      @Override
      public Double func(@NonNull Short v1, @NonNull Short v2) {
        return (double) v1 - v2;
      }
    });

    assertArithmeticExpression(vals, new ArithmeticExpressionEvaluator() {
      @Override
      public NumericColumn column(@NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable> c1, @NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable> c2) {
        return c1.mul(c2);
      }

      @Override
      public Double func(@NonNull Short v1, @NonNull Short v2) {
        return (double) v1 * v2;
      }
    });

    assertArithmeticExpression(vals, new ArithmeticExpressionEvaluator() {
      @Override
      public NumericColumn column(@NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable> c1, @NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable> c2) {
        return c1.mod(c2);
      }

      @Override
      public Double func(@NonNull Short v1, @NonNull Short v2) {
        return (double) v1 % v2;
      }
    });

    assertArithmeticExpression(vals, new ArithmeticExpressionEvaluator() {
      @Override
      public NumericColumn column(@NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable> c1, @NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable> c2) {
        return c1.div(c2);
      }

      @Override
      public Double func(@NonNull Short v1, @NonNull Short v2) {
        return (double) ((short) (v1 / v2));
      }
    });

  }

  private void assertArithmeticExpression(@NonNull List<SimpleAllValuesMutable> vals,
                                          @NonNull final ArithmeticExpressionEvaluator evaluator) {
    final List<Double> expected = Observable.fromIterable(vals)
        .map(new Function<SimpleAllValuesMutable, Double>() {
          @Override
          public Double apply(SimpleAllValuesMutable v) {
            return evaluator.func(v.primitiveShort, v.boxedShort);
          }
        })
        .toList()
        .blockingGet();

    assertThat(Select
        .column(evaluator.column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_SHORT, SIMPLE_ALL_VALUES_MUTABLE.BOXED_SHORT))
        .from(SIMPLE_ALL_VALUES_MUTABLE)
        .execute())
        .isEqualTo(expected);
  }

  public interface ArithmeticExpressionEvaluator {
    NumericColumn column(@NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable> c1, @NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable> c2);

    Double func(@NonNull Short v1, @NonNull Short v2);
  }
}
