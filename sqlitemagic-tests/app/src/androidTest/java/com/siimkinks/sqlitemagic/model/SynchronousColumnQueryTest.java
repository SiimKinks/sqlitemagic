package com.siimkinks.sqlitemagic.model;

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
import static com.siimkinks.sqlitemagic.Select.abs;
import static com.siimkinks.sqlitemagic.Select.avg;
import static com.siimkinks.sqlitemagic.Select.groupConcat;
import static com.siimkinks.sqlitemagic.Select.length;
import static com.siimkinks.sqlitemagic.Select.lower;
import static com.siimkinks.sqlitemagic.Select.upper;
import static com.siimkinks.sqlitemagic.Select.val;
import static com.siimkinks.sqlitemagic.SimpleAllValuesMutableTable.SIMPLE_ALL_VALUES_MUTABLE;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertAuthors;
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
      public NumericColumn column(@NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable, ?> c1, @NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable, ?> c2) {
        return c1.add(c2);
      }

      @Override
      public Double func(@NonNull Short v1, @NonNull Short v2) {
        return (double) v1 + v2;
      }
    });

    assertArithmeticExpression(vals, new ArithmeticExpressionEvaluator() {
      @Override
      public NumericColumn column(@NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable, ?> c1, @NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable, ?> c2) {
        return c1.sub(c2);
      }

      @Override
      public Double func(@NonNull Short v1, @NonNull Short v2) {
        return (double) v1 - v2;
      }
    });

    assertArithmeticExpression(vals, new ArithmeticExpressionEvaluator() {
      @Override
      public NumericColumn column(@NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable, ?> c1, @NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable, ?> c2) {
        return c1.mul(c2);
      }

      @Override
      public Double func(@NonNull Short v1, @NonNull Short v2) {
        return (double) v1 * v2;
      }
    });

    assertArithmeticExpression(vals, new ArithmeticExpressionEvaluator() {
      @Override
      public NumericColumn column(@NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable, ?> c1, @NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable, ?> c2) {
        return c1.mod(c2);
      }

      @Override
      public Double func(@NonNull Short v1, @NonNull Short v2) {
        return (double) v1 % v2;
      }
    });

    assertArithmeticExpression(vals, new ArithmeticExpressionEvaluator() {
      @Override
      public NumericColumn column(@NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable, ?> c1, @NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable, ?> c2) {
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
    NumericColumn column(@NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable, ?> c1, @NonNull NumericColumn<Short, Short, Number, SimpleAllValuesMutable, ?> c2);

    Double func(@NonNull Short v1, @NonNull Short v2);
  }
}
