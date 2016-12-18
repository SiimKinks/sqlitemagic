package com.siimkinks.sqlitemagic.model;

import android.support.test.runner.AndroidJUnit4;

import com.siimkinks.sqlitemagic.CompiledCountSelect;
import com.siimkinks.sqlitemagic.CompiledDelete;
import com.siimkinks.sqlitemagic.CompiledFirstSelect;
import com.siimkinks.sqlitemagic.Delete;
import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.Update;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.exception.OperationFailedException;
import com.siimkinks.sqlitemagic.model.immutable.BuilderMagazine;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithBuilder;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithBuilderAndNullableFields;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import rx.Single;
import rx.Subscription;
import rx.functions.Func1;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.BookTable.BOOK;
import static com.siimkinks.sqlitemagic.BuilderMagazineTable.BUILDER_MAGAZINE;
import static com.siimkinks.sqlitemagic.SimpleValueWithBuilderAndNullableFieldsTable.SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS;
import static com.siimkinks.sqlitemagic.SimpleValueWithBuilderTable.SIMPLE_VALUE_WITH_BUILDER;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertAuthors;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertBuilderSimpleValues;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertBuilderSimpleValuesAndNullableFields;
import static com.siimkinks.sqlitemagic.model.TestUtil.updateVals;

@RunWith(AndroidJUnit4.class)
public final class OperationObserveTest {
  private final CompiledFirstSelect<Author, Select.SelectN> selectFirstAuthor = Select.from(AUTHOR).takeFirst();
  private final CompiledCountSelect countAuthors = Select.from(AUTHOR).count();
  private final CompiledFirstSelect<SimpleValueWithBuilder, Select.SelectN> selectFirstVal = Select.from(SIMPLE_VALUE_WITH_BUILDER).takeFirst();
  private final CompiledCountSelect countVals = Select.from(SIMPLE_VALUE_WITH_BUILDER).count();

  @Before
  public void setUp() {
    Author.deleteTable().execute();
    Book.deleteTable().execute();
    Magazine.deleteTable().execute();
    SimpleMutable.deleteTable().execute();
    SimpleValueWithBuilder.deleteTable().execute();
    SimpleValueWithBuilderAndNullableFields.deleteTable().execute();
  }

  @Test
  public void mutableInsertObserve() {
    final Author author = Author.newRandom();
    final Long insertedId = author
        .insert()
        .observe()
        .toBlocking()
        .value();
    assertThat(countAuthors.execute()).isEqualTo(1L);
    assertThat(insertedId).isNotEqualTo(-1);
    assertThat(insertedId).isEqualTo(author.id);
    assertThat(author).isEqualTo(selectFirstAuthor.execute());
  }

  @Test
  public void immutableInsertObserve() {
    final SimpleValueWithBuilder val = SimpleValueWithBuilder.newRandom().build();
    final Long insertedId = val.insert()
        .observe()
        .toBlocking()
        .value();
    assertThat(countVals.execute()).isEqualTo(1L);
    assertThat(insertedId).isNotEqualTo(-1);
    assertThat(insertedId).isNotEqualTo(val.id());
    assertThat(val.equalsWithoutId(selectFirstVal.execute())).isTrue();
  }

  @Test
  public void mutableUpdateObserve() {
    final Author author = Author.newRandom();
    author.insert().execute();
    author.name = Utils.randomTableName();

    final Throwable e = author.update()
        .observe()
        .get();

    assertThat(countAuthors.execute()).isEqualTo(1L);
    assertThat(e).isNull();
    assertThat(author).isEqualTo(selectFirstAuthor.execute());
  }

  @Test
  public void immutableUpdateObserve() {
    final SimpleValueWithBuilder insertedVal = SimpleValueWithBuilder.newRandom().build();
    final long id = insertedVal.insert().execute();
    final Random r = new Random();
    final SimpleValueWithBuilder updatedVal = insertedVal.copy().id(id).integer(r.nextInt()).build();

    final Throwable e = updatedVal.update()
        .observe()
        .get();

    assertThat(countVals.execute()).isEqualTo(1L);
    assertThat(e).isNull();
    assertThat(updatedVal).isEqualTo(selectFirstVal.execute());
  }

  @Test
  public void mutablePersistObserve() {
    Author author = Author.newRandom();
    final Long insertedId = author.persist()
        .observe()
        .toBlocking()
        .value();
    assertThat(countAuthors.execute()).isEqualTo(1L);
    assertThat(insertedId).isNotEqualTo(-1);
    assertThat(insertedId).isEqualTo(author.id);
    assertThat(author).isEqualTo(selectFirstAuthor.execute());

    author = Author.newRandom();
    author.id = insertedId;

    final Long updatedId = author.persist()
        .observe()
        .toBlocking()
        .value();

    assertThat(author.id).isEqualTo(insertedId);
    assertThat(insertedId).isEqualTo(updatedId);
    assertThat(author).isEqualTo(selectFirstAuthor.execute());
    assertThat(countAuthors.execute()).isEqualTo(1L);
  }

  @Test
  public void immutablePersistObserve() {
    final SimpleValueWithBuilder val = SimpleValueWithBuilder.newRandom().build();
    final Long insertedId = val.persist()
        .observe()
        .toBlocking()
        .value();
    assertThat(countVals.execute()).isEqualTo(1L);
    assertThat(insertedId).isNotEqualTo(-1);
    assertThat(insertedId).isNotEqualTo(val.id());
    assertThat(val.equalsWithoutId(selectFirstVal.execute())).isTrue();

    final SimpleValueWithBuilder updatedVal = SimpleValueWithBuilder.newRandom()
        .id(insertedId)
        .build();

    final Long updatedId = updatedVal.persist()
        .observe()
        .toBlocking()
        .value();

    assertThat(updatedVal.id()).isEqualTo(insertedId);
    assertThat(insertedId).isEqualTo(updatedId);
    assertThat(updatedVal).isEqualTo(selectFirstVal.execute());
    assertThat(countVals.execute()).isEqualTo(1L);
  }

  @Test
  public void mutablePersistThatUpdatesIgnoringNullObserve() {
    final Author author = Author.newRandom();
    final long insertedId = author.persist().execute();

    author.primitiveBoolean = !author.primitiveBoolean;
    final Author updatedAuthor = new Author();
    updatedAuthor.id = insertedId;
    updatedAuthor.name = null;
    updatedAuthor.boxedBoolean = null;
    updatedAuthor.primitiveBoolean = author.primitiveBoolean;

    final Long updatedId = updatedAuthor.persist()
        .ignoreNullValues()
        .observe()
        .toBlocking()
        .value();

    assertThat(author.id).isEqualTo(updatedAuthor.id);
    assertThat(updatedAuthor.id).isEqualTo(insertedId);
    assertThat(insertedId).isEqualTo(updatedId);
    assertThat(author).isEqualTo(selectFirstAuthor.execute());
    assertThat(countAuthors.execute()).isEqualTo(1L);
  }

  @Test
  public void immutablePersistThatUpdatesIgnoringNullObserve() {
    SimpleValueWithBuilderAndNullableFields val = SimpleValueWithBuilderAndNullableFields.newRandom().build();
    final long insertedId = val.persist().execute();
    final SimpleValueWithBuilderAndNullableFields expected = val.copy()
        .id(insertedId)
        .boxedInteger(new Random().nextInt())
        .build();

    final SimpleValueWithBuilderAndNullableFields updatedVal = expected.copy()
        .id(insertedId)
        .string(null)
        .boxedBoolean(null)
        .build();

    final Long updatedId = updatedVal.persist()
        .ignoreNullValues()
        .observe()
        .toBlocking()
        .value();

    assertThat(expected.id()).isEqualTo(updatedVal.id());
    assertThat(updatedVal.id()).isEqualTo(insertedId);
    assertThat(insertedId).isEqualTo(updatedId);
    assertThat(expected).isEqualTo(Select.from(SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS).takeFirst().execute());
    assertThat(Select.from(SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS).count().execute()).isEqualTo(1L);
  }

  @Test
  public void mutableBulkInsertObserve() {
    final int testCount = 10;
    final ArrayList<Author> list = new ArrayList<>(testCount);
    for (int i = 0; i < testCount; i++) {
      list.add(Author.newRandom());
    }
    assertThat(Author.insert(list)
        .observe()
        .get())
        .isNull();

    final List<Author> expected = Select.from(AUTHOR).execute();
    assertThat(list).containsExactlyElementsIn(expected);
  }

  @Test
  public void immutableBulkInsertObserve() {
    final int testCount = 10;
    final ArrayList<SimpleValueWithBuilder> list = new ArrayList<>(testCount);
    for (int i = 0; i < testCount; i++) {
      list.add(SimpleValueWithBuilder.newRandom().build());
    }
    assertThat(SimpleValueWithBuilder
        .insert(list)
        .observe()
        .get())
        .isNull();

    final List<SimpleValueWithBuilder> expectedList = Select.from(SIMPLE_VALUE_WITH_BUILDER).execute();
    for (int i = 0, listSize = list.size(); i < listSize; i++) {
      final SimpleValueWithBuilder val = list.get(i);
      final SimpleValueWithBuilder expected = expectedList.get(i);
      assertThat(val.equalsWithoutId(expected)).isTrue();
    }
  }

  @Test
  public void earlyUnsubscribeDoesNotInsertAllValues() throws InterruptedException {
    final int testCount = 500;
    final ArrayList<Author> list = new ArrayList<>(testCount);
    for (int i = 0; i < testCount; i++) {
      list.add(Author.newRandom());
    }
    final Subscription subscription = Author.insert(list)
        .observe()
        .subscribeOn(Schedulers.io())
        .subscribe();
    subscription.unsubscribe();
    Thread.sleep(500);
    assertThat(Select
        .from(AUTHOR)
        .count()
        .execute())
        .isLessThan((long) testCount);
  }

  @Test
  public void mutableBulkUpdateObserve() {
    final int testCount = 10;
    final List<Author> authors = insertAuthors(testCount);
    for (Author author : authors) {
      author.name = Utils.randomTableName();
    }

    assertThat(Author.update(authors)
        .observe()
        .get())
        .isNull();

    final List<Author> expected = Select.from(AUTHOR).execute();
    assertThat(authors).containsExactlyElementsIn(expected);
  }

  @Test
  public void immutableBulkUpdateObserve() {
    final int testCount = 10;
    final List<SimpleValueWithBuilder> values = insertBuilderSimpleValues(testCount);
    final List<SimpleValueWithBuilder> updatedValues = new ArrayList<>(testCount);
    final Random r = new Random();
    for (SimpleValueWithBuilder insertValue : values) {
      updatedValues.add(insertValue
          .copy()
          .integer(r.nextInt())
          .build());
    }

    assertThat(SimpleValueWithBuilder.update(updatedValues)
        .observe()
        .get())
        .isNull();

    final List<SimpleValueWithBuilder> expected = Select.from(SIMPLE_VALUE_WITH_BUILDER).execute();
    assertThat(updatedValues).containsExactlyElementsIn(expected);
  }

  @Test
  public void earlyUnsubscribeRollbacksBulkUpdate() throws InterruptedException {
    final int testCount = 500;
    final List<Author> list = insertAuthors(testCount);
    for (Author author : list) {
      author.name = Utils.randomTableName();
    }
    final Subscription subscription = Author.update(list)
        .observe()
        .subscribeOn(Schedulers.io())
        .subscribe();
    subscription.unsubscribe();
    Thread.sleep(500);
    assertThat(Select
        .from(AUTHOR)
        .execute())
        .containsNoneIn(list);
  }

  @Test
  public void mutableBulkPersistThatInsertsObserve() {
    final int testCount = 10;
    final ArrayList<Author> list = new ArrayList<>(testCount);
    for (int i = 0; i < testCount; i++) {
      list.add(Author.newRandom());
    }

    assertThat(Author.persist(list)
        .observe()
        .get())
        .isNull();

    final List<Author> expected = Select.from(AUTHOR).execute();
    assertThat(list).containsExactlyElementsIn(expected);
  }

  @Test
  public void immutableBulkPersistThatInsertsObserve() {
    final int testCount = 10;
    final ArrayList<SimpleValueWithBuilder> list = new ArrayList<>(testCount);
    for (int i = 0; i < testCount; i++) {
      list.add(SimpleValueWithBuilder.newRandom().build());
    }

    assertThat(SimpleValueWithBuilder
        .persist(list)
        .observe()
        .get())
        .isNull();

    final List<SimpleValueWithBuilder> expectedList = Select.from(SIMPLE_VALUE_WITH_BUILDER).execute();
    for (int i = 0, listSize = list.size(); i < listSize; i++) {
      final SimpleValueWithBuilder val = list.get(i);
      final SimpleValueWithBuilder expected = expectedList.get(i);
      assertThat(val.equalsWithoutId(expected)).isTrue();
    }
  }

  @Test
  public void earlyUnsubscribeDoesNotPersistThatInsertsAllValues() throws InterruptedException {
    final int testCount = 500;
    final ArrayList<Author> list = new ArrayList<>(testCount);
    for (int i = 0; i < testCount; i++) {
      list.add(Author.newRandom());
    }
    final Subscription subscription = Author.persist(list)
        .observe()
        .subscribeOn(Schedulers.io())
        .subscribe();
    subscription.unsubscribe();
    Thread.sleep(500);
    assertThat(Select
        .from(AUTHOR)
        .count()
        .execute())
        .isLessThan((long) testCount);
  }

  @Test
  public void mutableBulkPersistThatUpdatesObserve() {
    final int testCount = 10;
    final List<Author> authors = insertAuthors(testCount);
    for (Author author : authors) {
      author.name = Utils.randomTableName();
    }

    assertThat(Author
        .persist(authors)
        .observe()
        .get())
        .isNull();

    final List<Author> expected = Select.from(AUTHOR).execute();
    assertThat(authors).containsExactlyElementsIn(expected);
  }

  @Test
  public void immutableBulkPersistThatUpdatesObserve() {
    final int testCount = 10;
    final List<SimpleValueWithBuilder> values = insertBuilderSimpleValues(testCount);
    final List<SimpleValueWithBuilder> updatedValues = new ArrayList<>(testCount);
    final Random r = new Random();
    for (SimpleValueWithBuilder insertValue : values) {
      updatedValues.add(insertValue
          .copy()
          .integer(r.nextInt())
          .build());
    }

    assertThat(SimpleValueWithBuilder
        .persist(updatedValues)
        .observe()
        .get())
        .isNull();

    final List<SimpleValueWithBuilder> expected = Select.from(SIMPLE_VALUE_WITH_BUILDER).execute();
    assertThat(updatedValues).containsExactlyElementsIn(expected);
  }

  @Test
  public void earlyUnsubscribeRollbacksBulkPersistThatUpdates() throws InterruptedException {
    final int testCount = 500;
    final List<Author> list = insertAuthors(testCount);
    for (Author author : list) {
      author.name = Utils.randomTableName();
    }
    final Subscription subscription = Author
        .persist(list)
        .observe()
        .subscribeOn(Schedulers.io())
        .subscribe();
    subscription.unsubscribe();
    Thread.sleep(500);
    assertThat(Select
        .from(AUTHOR)
        .execute())
        .containsNoneIn(list);
  }

  @Test
  public void mutableBulkPersistThatUpdatesIgnoringNullObserve() {
    final int testCount = 10;
    final List<Author> authors = insertAuthors(testCount);
    final List<Author> updatedAuthors = new ArrayList<>(testCount);
    for (Author author : authors) {
      Author a = new Author();
      a.id = author.id;
      a.name = null;
      a.boxedBoolean = null;
      a.primitiveBoolean = author.primitiveBoolean;
      updatedAuthors.add(a);
    }

    assertThat(Author.persist(updatedAuthors)
        .ignoreNullValues()
        .observe()
        .get())
        .isNull();

    final List<Author> expected = Select.from(AUTHOR).execute();
    assertThat(authors).containsExactlyElementsIn(expected);
  }

  @Test
  public void immutableBulkPersistThatUpdatesIgnoringNullObserve() {
    final int testCount = 10;
    final List<SimpleValueWithBuilderAndNullableFields> values = insertBuilderSimpleValuesAndNullableFields(testCount);
    final List<SimpleValueWithBuilderAndNullableFields> updatedValues = new ArrayList<>(testCount);
    for (SimpleValueWithBuilderAndNullableFields insertValue : values) {
      updatedValues.add(insertValue
          .copy()
          .string(null)
          .boxedBoolean(null)
          .boxedInteger(null)
          .build());
    }

    assertThat(SimpleValueWithBuilderAndNullableFields
        .persist(updatedValues)
        .ignoreNullValues()
        .observe()
        .get())
        .isNull();

    final List<SimpleValueWithBuilderAndNullableFields> expected = Select.from(SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS).execute();
    assertThat(values).containsExactlyElementsIn(expected);
  }

  @Test
  public void earlyUnsubscribeRollbacksBulkPersistThatUpdatesWithIgnoringNull() throws InterruptedException {
    final int testCount = 500;
    final List<Author> list = insertAuthors(testCount);
    for (Author author : list) {
      author.name = null;
    }
    final Subscription subscription = Author
        .persist(list)
        .ignoreNullValues()
        .observe()
        .subscribeOn(Schedulers.io())
        .subscribe();
    subscription.unsubscribe();
    Thread.sleep(500);
    assertThat(Select
        .from(AUTHOR)
        .execute())
        .containsNoneIn(list);
  }

  @Test
  public void deleteObserve() {
    final int testCount = 10;
    final List<Author> list = TestUtil.insertAuthors(testCount);
    assertThat(countAuthors.execute()).isEqualTo(testCount);

    final Author randAuthor = list.get(new Random().nextInt(testCount));
    final int affectedRows = randAuthor.delete()
        .observe()
        .toBlocking()
        .value();

    assertThat(affectedRows).isEqualTo(1);
    assertThat(countAuthors.execute()).isEqualTo(testCount - 1);
  }

  @Test
  public void deleteTableObserve() {
    final int testCount = 10;
    TestUtil.insertAuthors(testCount);
    assertThat(countAuthors.execute()).isEqualTo(testCount);

    final int affectedRows = Author.deleteTable()
        .observe()
        .toBlocking()
        .value();
    assertThat(affectedRows).isEqualTo(testCount);
    assertThat(countAuthors.execute()).isEqualTo(0);
  }

  @Test
  public void updateBuilderObserve() {
    BuilderMagazine.deleteTable().execute();
    BuilderMagazine magazine = BuilderMagazine.newRandom().build();
    assertThat(magazine.persist().execute()).isNotEqualTo(-1);
    magazine = Select
        .from(BUILDER_MAGAZINE)
        .queryDeep()
        .takeFirst()
        .execute();

    BuilderMagazine expected = magazine.copy()
        .name("asdasd")
        .build();

    assertThat(Update
        .table(BUILDER_MAGAZINE)
        .set(BUILDER_MAGAZINE.NAME, "asdasd")
        .observe()
        .toBlocking()
        .value())
        .isEqualTo(1);
    assertThat(Select
        .from(BUILDER_MAGAZINE)
        .where(BUILDER_MAGAZINE.ID.is(expected.id()))
        .queryDeep()
        .takeFirst()
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void deleteBuilderObserve() {
    final CompiledCountSelect compiledCountSelect = Select.from(BOOK).count();
    final CompiledDelete compiledDelete = Delete.from(BOOK).compile();

    assertThat(compiledDelete.observe().toBlocking().value()).isEqualTo(0L);
    assertThat(compiledCountSelect.execute()).isEqualTo(0);

    final int testCount = 10;
    for (int i = 0; i < testCount; i++) {
      final long id = Book.newRandom().persist().execute();
      assertThat(id).isNotEqualTo(-1);
    }
    assertThat(compiledCountSelect.execute()).isEqualTo(testCount);

    assertThat(compiledDelete.observe().toBlocking().value()).isEqualTo(testCount);
    assertThat(compiledCountSelect.execute()).isEqualTo(0);
  }

  @Test
  public void failedInsertEmitsError() {
    final SimpleMutable value = SimpleMutable.newRandom();
    value.insert().execute();

    final TestSubscriber<Long> ts = new TestSubscriber<>();
    value.insert()
        .observe()
        .subscribe(ts);

    ts.awaitTerminalEvent(2, TimeUnit.SECONDS);
    ts.assertNoValues();
    final List<Throwable> errors = ts.getOnErrorEvents();
    assertThat(errors.size()).isEqualTo(1);
  }

  @Test
  public void failedUpdateEmitsError() {
    final SimpleMutable value = SimpleMutable.newRandom();
    value.id = 1;

    final TestSubscriber<Boolean> ts = new TestSubscriber<>();
    value.update()
        .observe()
        .subscribe(ts);

    ts.awaitTerminalEvent(2, TimeUnit.SECONDS);
    ts.assertNoValues();
    ts.assertError(OperationFailedException.class);
  }

  @Test
  public void streamedBulkInsertOperation() {
    final int size = 10;
    final List<Author> vals1 = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      vals1.add(Author.newRandom());
    }
    final List<SimpleMutable> vals2 = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      vals2.add(SimpleMutable.newRandom());
    }

    final TestSubscriber<Boolean> ts = new TestSubscriber<>();
    Author.insert(vals1)
        .observe()
        .toSingleDefault(Boolean.TRUE)
        .flatMap(new Func1<Boolean, Single<Boolean>>() {
          @Override
          public Single<Boolean> call(Boolean aBoolean) {
            return SimpleMutable
                .insert(vals2)
                .observe()
                .toSingleDefault(Boolean.TRUE);
          }
        })
        .subscribeOn(Schedulers.io())
        .subscribe(ts);

    ts.awaitTerminalEvent();
    ts.assertNoErrors();
    ts.assertCompleted();
    ts.assertValue(Boolean.TRUE);
  }

  @Test
  public void streamedBulkUpdateOperation() {
    final int size = 10;
    List<Author> vals1 = insertAuthors(size);
    vals1 = updateVals(vals1, new Func1<Author, Author>() {
      @Override
      public Author call(Author author) {
        author.name = "asd";
        return author;
      }
    });

    final List<SimpleMutable> vals2 = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      final SimpleMutable val = SimpleMutable.newRandom();
      val.insert().execute();
      val.name = "asd";
      vals2.add(val);
    }

    final TestSubscriber<Boolean> ts = new TestSubscriber<>();
    Author.update(vals1)
        .observe()
        .toSingleDefault(Boolean.TRUE)
        .flatMap(new Func1<Boolean, Single<Boolean>>() {
          @Override
          public Single<Boolean> call(Boolean aBoolean) {
            return SimpleMutable
                .update(vals2)
                .observe()
                .toSingleDefault(Boolean.TRUE);
          }
        })
        .subscribeOn(Schedulers.io())
        .subscribe(ts);

    ts.awaitTerminalEvent();
    ts.assertNoErrors();
    ts.assertCompleted();
    ts.assertValue(Boolean.TRUE);
  }

  @Test
  public void streamedBulkPersistOperation() {
    final int size = 10;
    final List<Author> vals1 = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      vals1.add(Author.newRandom());
    }
    final List<SimpleMutable> vals2 = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      vals2.add(SimpleMutable.newRandom());
    }

    final TestSubscriber<Boolean> ts = new TestSubscriber<>();
    Author.persist(vals1)
        .observe()
        .toSingleDefault(Boolean.TRUE)
        .flatMap(new Func1<Boolean, Single<Boolean>>() {
          @Override
          public Single<Boolean> call(Boolean aBoolean) {
            return SimpleMutable
                .persist(vals2)
                .observe()
                .toSingleDefault(Boolean.TRUE);
          }
        })
        .subscribeOn(Schedulers.io())
        .subscribe(ts);

    ts.awaitTerminalEvent();
    ts.assertNoErrors();
    ts.assertCompleted();
    ts.assertValue(Boolean.TRUE);
  }
}
