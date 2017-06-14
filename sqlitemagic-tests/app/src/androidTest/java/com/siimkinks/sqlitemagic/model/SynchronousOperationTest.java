package com.siimkinks.sqlitemagic.model;

import android.support.test.runner.AndroidJUnit4;

import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.Table;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.model.immutable.BuilderMagazine;
import com.siimkinks.sqlitemagic.model.immutable.CreatorMagazine;
import com.siimkinks.sqlitemagic.model.immutable.DataClassWithFields;
import com.siimkinks.sqlitemagic.model.immutable.DataClassWithMethods;
import com.siimkinks.sqlitemagic.model.immutable.ImmutableEquals;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithBuilder;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithBuilderAndNullableFields;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithCreator;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithCreatorAndNullableFields;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

import io.reactivex.functions.Function;

import static android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL;
import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.BuilderMagazineTable.BUILDER_MAGAZINE;
import static com.siimkinks.sqlitemagic.CreatorMagazineTable.CREATOR_MAGAZINE;
import static com.siimkinks.sqlitemagic.DataClassWithFieldsTable.DATA_CLASS_WITH_FIELDS;
import static com.siimkinks.sqlitemagic.DataClassWithMethodsTable.DATA_CLASS_WITH_METHODS;
import static com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE;
import static com.siimkinks.sqlitemagic.SimpleValueWithBuilderAndNullableFieldsTable.SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS;
import static com.siimkinks.sqlitemagic.SimpleValueWithBuilderTable.SIMPLE_VALUE_WITH_BUILDER;
import static com.siimkinks.sqlitemagic.SimpleValueWithCreatorAndNullableFieldsTable.SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS;
import static com.siimkinks.sqlitemagic.SimpleValueWithCreatorTable.SIMPLE_VALUE_WITH_CREATOR;
import static com.siimkinks.sqlitemagic.model.TestUtil.createVals;
import static com.siimkinks.sqlitemagic.model.TestUtil.updateVals;

@RunWith(AndroidJUnit4.class)
public final class SynchronousOperationTest {

  @Before
  public void setUp() {
    Author.deleteTable().execute();
    SimpleValueWithBuilder.deleteTable().execute();
    SimpleValueWithCreator.deleteTable().execute();
    Magazine.deleteTable().execute();
    BuilderMagazine.deleteTable().execute();
    CreatorMagazine.deleteTable().execute();
    SimpleValueWithBuilderAndNullableFields.deleteTable().execute();
    SimpleValueWithCreatorAndNullableFields.deleteTable().execute();
    DataClassWithMethods.deleteTable().execute();
    DataClassWithFields.deleteTable().execute();
  }

  @Test
  public void simpleMutableInsert() {
    final Author val = Author.newRandom();
    final long id = val.insert().execute();

    assertThat(id).isNotEqualTo(-1);
    assertThat(val.id).isEqualTo(id);

    assertMutableValue(val, AUTHOR);
  }

  @Test
  public void simpleImmutableWithBuilderInsert() {
    final SimpleValueWithBuilder val = SimpleValueWithBuilder.newRandom().build();
    final long id = val.insert().execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, SIMPLE_VALUE_WITH_BUILDER);
  }

  @Test
  public void simpleImmutableWithCreatorInsert() {
    SimpleValueWithCreator val = SimpleValueWithCreator.newRandom();
    final long id = val.insert().execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, SIMPLE_VALUE_WITH_CREATOR);
  }

  @Test
  public void simpleDataClassWithMethodsInsert() {
    DataClassWithMethods val = DataClassWithMethods.newRandom();
    final long id = val.insert().execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, DATA_CLASS_WITH_METHODS);
  }

  @Test
  public void simpleDataClassWithFieldsInsert() {
    DataClassWithFields val = DataClassWithFields.newRandom();
    final long id = val.insert().execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, DATA_CLASS_WITH_FIELDS);
  }

  @Test
  public void complexMutableInsert() {
    final Magazine val = Magazine.newRandom();
    final long id = val.insert().execute();

    assertThat(id).isNotEqualTo(-1);
    assertThat(val.id).isEqualTo(id);

    assertMutableValue(val, MAGAZINE);
  }

  @Test
  public void complexImmutableWithBuilderInsert() {
    final BuilderMagazine val = BuilderMagazine.newRandom().build();
    final long id = val.insert().execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, BUILDER_MAGAZINE);
  }

  @Test
  public void complexImmutableWithCreatorInsert() {
    CreatorMagazine val = CreatorMagazine.newRandom();
    final long id = val.insert().execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, CREATOR_MAGAZINE);
  }

  @Test
  public void simpleMutableInsertWithConflictAlgorithm() {
    final Author val = Author.newRandom();
    final long id = val.insert().conflictAlgorithm(CONFLICT_FAIL).execute();

    assertThat(id).isNotEqualTo(-1);
    assertThat(val.id).isEqualTo(id);

    assertMutableValue(val, AUTHOR);
  }

  @Test
  public void simpleImmutableWithBuilderInsertWithConflictAlgorithm() {
    final SimpleValueWithBuilder val = SimpleValueWithBuilder.newRandom().build();
    final long id = val.insert().conflictAlgorithm(CONFLICT_FAIL).execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, SIMPLE_VALUE_WITH_BUILDER);
  }

  @Test
  public void simpleImmutableWithCreatorInsertWithConflictAlgorithm() {
    SimpleValueWithCreator val = SimpleValueWithCreator.newRandom();
    final long id = val.insert().conflictAlgorithm(CONFLICT_FAIL).execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, SIMPLE_VALUE_WITH_CREATOR);
  }

  @Test
  public void complexMutableInsertWithConflictAlgorithm() {
    final Magazine val = Magazine.newRandom();
    final long id = val.insert().conflictAlgorithm(CONFLICT_FAIL).execute();

    assertThat(id).isNotEqualTo(-1);
    assertThat(val.id).isEqualTo(id);

    assertMutableValue(val, MAGAZINE);
  }

  @Test
  public void complexImmutableWithBuilderInsertWithConflictAlgorithm() {
    final BuilderMagazine val = BuilderMagazine.newRandom().build();
    final long id = val.insert().conflictAlgorithm(CONFLICT_FAIL).execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, BUILDER_MAGAZINE);
  }

  @Test
  public void complexImmutableWithCreatorInsertWithConflictAlgorithm() {
    CreatorMagazine val = CreatorMagazine.newRandom();
    final long id = val.insert().conflictAlgorithm(CONFLICT_FAIL).execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, CREATOR_MAGAZINE);
  }

  @Test
  public void simpleMutableUpdate() {
    Author val = Author.newRandom();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    val = Author.newRandom();
    val.id = id;

    final boolean success = val.update().execute();
    assertThat(success).isTrue();

    assertMutableValue(val, AUTHOR);
  }

  @Test
  public void simpleImmutableWithBuilderUpdate() {
    SimpleValueWithBuilder val = SimpleValueWithBuilder.newRandom().build();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    val = SimpleValueWithBuilder.newRandom()
        .id(id)
        .build();
    final boolean success = val.update().execute();
    assertThat(success).isTrue();

    assertImmutableValue(val, SIMPLE_VALUE_WITH_BUILDER);
  }

  @Test
  public void simpleImmutableWithCreatorUpdate() {
    SimpleValueWithCreator val = SimpleValueWithCreator.newRandom();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    val = SimpleValueWithCreator.newRandom(id);
    final boolean success = val.update().execute();
    assertThat(success).isTrue();

    assertImmutableValue(val, SIMPLE_VALUE_WITH_CREATOR);
  }

  @Test
  public void simpleDataClassWithMethodsUpdate() {
    DataClassWithMethods val = DataClassWithMethods.newRandom();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    val = DataClassWithMethods.newRandom(id);
    final boolean success = val.update().execute();
    assertThat(success).isTrue();

    assertImmutableValue(val, DATA_CLASS_WITH_METHODS);
  }

  @Test
  public void simpleDataClassWithFieldsUpdate() {
    DataClassWithFields val = DataClassWithFields.newRandom();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    val = DataClassWithFields.newRandom(id);
    final boolean success = val.update().execute();
    assertThat(success).isTrue();

    assertImmutableValue(val, DATA_CLASS_WITH_FIELDS);
  }

  @Test
  public void complexMutableUpdate() {
    Magazine val = Magazine.newRandom();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    final long authorId = val.author.id;
    val = Magazine.newRandom();
    val.id = id;
    val.author.id = authorId;

    final boolean success = val.update().execute();
    assertThat(success).isTrue();

    assertMutableValue(val, MAGAZINE);
  }

  @Test
  public void complexImmutableWithBuilderUpdate() {
    BuilderMagazine val = BuilderMagazine.newRandom().build();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    final Author author = Author.newRandom();
    author.id = val.author().id;
    val = BuilderMagazine.builder()
        .id(id)
        .name(Utils.randomTableName())
        .author(author)
        .simpleValueWithBuilder(SimpleValueWithBuilder.newRandom()
            .id(Select.column(SIMPLE_VALUE_WITH_BUILDER.ID).from(SIMPLE_VALUE_WITH_BUILDER).takeFirst().execute())
            .build())
        .simpleValueWithCreator(SimpleValueWithCreator.newRandom(Select.column(SIMPLE_VALUE_WITH_CREATOR.ID).from(SIMPLE_VALUE_WITH_CREATOR).takeFirst().execute()))
        .build();

    final boolean success = val.update().execute();
    assertThat(success).isTrue();

    assertImmutableValue(val, BUILDER_MAGAZINE);
  }

  @Test
  public void complexImmutableWithCreatorUpdate() {
    CreatorMagazine val = CreatorMagazine.newRandom();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    final Author author = Author.newRandom();
    author.id = val.author().id;
    val = CreatorMagazine.create(
        id,
        Utils.randomTableName(),
        author,
        SimpleValueWithBuilder.newRandom()
            .id(Select.column(SIMPLE_VALUE_WITH_BUILDER.ID).from(SIMPLE_VALUE_WITH_BUILDER).takeFirst().execute())
            .build(),
        SimpleValueWithCreator.newRandom(Select.column(SIMPLE_VALUE_WITH_CREATOR.ID).from(SIMPLE_VALUE_WITH_CREATOR).takeFirst().execute())
    );

    final boolean success = val.update().execute();
    assertThat(success).isTrue();

    assertImmutableValue(val, CREATOR_MAGAZINE);
  }

  @Test
  public void simpleMutableUpdateWithConflictAlgorithm() {
    Author val = Author.newRandom();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    val = Author.newRandom();
    val.id = id;

    final boolean success = val.update().conflictAlgorithm(CONFLICT_FAIL).execute();
    assertThat(success).isTrue();

    assertMutableValue(val, AUTHOR);
  }

  @Test
  public void simpleImmutableWithBuilderUpdateWithConflictAlgorithm() {
    SimpleValueWithBuilder val = SimpleValueWithBuilder.newRandom().build();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    val = SimpleValueWithBuilder.newRandom()
        .id(id)
        .build();
    final boolean success = val.update().conflictAlgorithm(CONFLICT_FAIL).execute();
    assertThat(success).isTrue();

    assertImmutableValue(val, SIMPLE_VALUE_WITH_BUILDER);
  }

  @Test
  public void simpleImmutableWithCreatorUpdateWithConflictAlgorithm() {
    SimpleValueWithCreator val = SimpleValueWithCreator.newRandom();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    val = SimpleValueWithCreator.newRandom(id);
    final boolean success = val.update().conflictAlgorithm(CONFLICT_FAIL).execute();
    assertThat(success).isTrue();

    assertImmutableValue(val, SIMPLE_VALUE_WITH_CREATOR);
  }

  @Test
  public void complexMutableUpdateWithConflictAlgorithm() {
    Magazine val = Magazine.newRandom();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    final long authorId = val.author.id;
    val = Magazine.newRandom();
    val.id = id;
    val.author.id = authorId;

    final boolean success = val.update().conflictAlgorithm(CONFLICT_FAIL).execute();
    assertThat(success).isTrue();

    assertMutableValue(val, MAGAZINE);
  }

  @Test
  public void complexImmutableWithBuilderUpdateWithConflictAlgorithm() {
    BuilderMagazine val = BuilderMagazine.newRandom().build();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    final Author author = Author.newRandom();
    author.id = val.author().id;
    val = BuilderMagazine.builder()
        .id(id)
        .name(Utils.randomTableName())
        .author(author)
        .simpleValueWithBuilder(SimpleValueWithBuilder.newRandom()
            .id(Select.column(SIMPLE_VALUE_WITH_BUILDER.ID).from(SIMPLE_VALUE_WITH_BUILDER).takeFirst().execute())
            .build())
        .simpleValueWithCreator(SimpleValueWithCreator.newRandom(Select.column(SIMPLE_VALUE_WITH_CREATOR.ID).from(SIMPLE_VALUE_WITH_CREATOR).takeFirst().execute()))
        .build();

    final boolean success = val.update().conflictAlgorithm(CONFLICT_FAIL).execute();
    assertThat(success).isTrue();

    assertImmutableValue(val, BUILDER_MAGAZINE);
  }

  @Test
  public void complexImmutableWithCreatorUpdateWithConflictAlgorithm() {
    CreatorMagazine val = CreatorMagazine.newRandom();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    final Author author = Author.newRandom();
    author.id = val.author().id;
    val = CreatorMagazine.create(
        id,
        Utils.randomTableName(),
        author,
        SimpleValueWithBuilder.newRandom()
            .id(Select.column(SIMPLE_VALUE_WITH_BUILDER.ID).from(SIMPLE_VALUE_WITH_BUILDER).takeFirst().execute())
            .build(),
        SimpleValueWithCreator.newRandom(Select.column(SIMPLE_VALUE_WITH_CREATOR.ID).from(SIMPLE_VALUE_WITH_CREATOR).takeFirst().execute())
    );

    final boolean success = val.update().conflictAlgorithm(CONFLICT_FAIL).execute();
    assertThat(success).isTrue();

    assertImmutableValue(val, CREATOR_MAGAZINE);
  }

  @Test
  public void simpleMutablePersistWithInsert() {
    final Author val = Author.newRandom();
    final long id = val.persist().execute();

    assertThat(id).isNotEqualTo(-1);
    assertThat(val.id).isEqualTo(id);

    assertMutableValue(val, AUTHOR);
  }

  @Test
  public void simpleImmutableWithBuilderPersistWithInsert() {
    final SimpleValueWithBuilder val = SimpleValueWithBuilder.newRandom().build();
    final long id = val.persist().execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, SIMPLE_VALUE_WITH_BUILDER);
  }

  @Test
  public void simpleImmutableWithCreatorPersistWithInsert() {
    SimpleValueWithCreator val = SimpleValueWithCreator.newRandom();
    final long id = val.persist().execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, SIMPLE_VALUE_WITH_CREATOR);
  }

  @Test
  public void dataClassWithMethodsPersistWithInsert() {
    DataClassWithMethods val = DataClassWithMethods.newRandom();
    final long id = val.persist().execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, DATA_CLASS_WITH_METHODS);
  }

  @Test
  public void dataClassWithFieldsPersistWithInsert() {
    DataClassWithFields val = DataClassWithFields.newRandom();
    final long id = val.persist().execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, DATA_CLASS_WITH_FIELDS);
  }

  @Test
  public void complexMutablePersistWithInsert() {
    final Magazine val = Magazine.newRandom();
    final long id = val.persist().execute();

    assertThat(id).isNotEqualTo(-1);
    assertThat(val.id).isEqualTo(id);

    assertMutableValue(val, MAGAZINE);
  }

  @Test
  public void complexImmutableWithBuilderPersistWithInsert() {
    final BuilderMagazine val = BuilderMagazine.newRandom().build();
    final long id = val.persist().execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, BUILDER_MAGAZINE);
  }

  @Test
  public void complexImmutableWithCreatorPersistWithInsert() {
    CreatorMagazine val = CreatorMagazine.newRandom();
    final long id = val.persist().execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, CREATOR_MAGAZINE);
  }

  @Test
  public void simpleMutablePersistWithUpdate() {
    Author val = Author.newRandom();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    val = Author.newRandom();
    val.id = id;

    final long persistId = val.persist().execute();
    assertThat(persistId).isEqualTo(id);

    assertMutableValue(val, AUTHOR);
  }

  @Test
  public void simpleImmutableWithBuilderPersistWithUpdate() {
    SimpleValueWithBuilder val = SimpleValueWithBuilder.newRandom().build();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    val = SimpleValueWithBuilder.newRandom()
        .id(id)
        .build();
    final long persistId = val.persist().execute();
    assertThat(persistId).isEqualTo(id);

    assertImmutableValue(val, SIMPLE_VALUE_WITH_BUILDER);
  }

  @Test
  public void simpleImmutableWithCreatorPersistWithUpdate() {
    SimpleValueWithCreator val = SimpleValueWithCreator.newRandom();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    val = SimpleValueWithCreator.newRandom(id);
    final long persistId = val.persist().execute();
    assertThat(persistId).isEqualTo(id);

    assertImmutableValue(val, SIMPLE_VALUE_WITH_CREATOR);
  }

  @Test
  public void dataClassWithMethodsPersistWithUpdate() {
    DataClassWithMethods val = DataClassWithMethods.newRandom();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    val = DataClassWithMethods.newRandom(id);
    final long persistId = val.persist().execute();
    assertThat(persistId).isEqualTo(id);

    assertImmutableValue(val, DATA_CLASS_WITH_METHODS);
  }

  @Test
  public void dataClassWithFieldsPersistWithUpdate() {
    DataClassWithFields val = DataClassWithFields.newRandom();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    val = DataClassWithFields.newRandom(id);
    final long persistId = val.persist().execute();
    assertThat(persistId).isEqualTo(id);

    assertImmutableValue(val, DATA_CLASS_WITH_FIELDS);
  }

  @Test
  public void complexMutablePersistWithUpdate() {
    Magazine val = Magazine.newRandom();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    final long authorId = val.author.id;
    val = Magazine.newRandom();
    val.id = id;
    val.author.id = authorId;

    final long persistId = val.persist().execute();
    assertThat(persistId).isEqualTo(id);

    assertMutableValue(val, MAGAZINE);
  }

  @Test
  public void complexImmutableWithBuilderPersistWithUpdate() {
    BuilderMagazine val = BuilderMagazine.newRandom().build();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    final Author author = Author.newRandom();
    author.id = val.author().id;
    val = BuilderMagazine.builder()
        .id(id)
        .name(Utils.randomTableName())
        .author(author)
        .simpleValueWithBuilder(SimpleValueWithBuilder.newRandom()
            .id(Select.column(SIMPLE_VALUE_WITH_BUILDER.ID).from(SIMPLE_VALUE_WITH_BUILDER).takeFirst().execute())
            .build())
        .simpleValueWithCreator(SimpleValueWithCreator.newRandom(Select.column(SIMPLE_VALUE_WITH_CREATOR.ID).from(SIMPLE_VALUE_WITH_CREATOR).takeFirst().execute()))
        .build();

    final long persistId = val.persist().execute();
    assertThat(persistId).isEqualTo(id);

    assertImmutableValue(val, BUILDER_MAGAZINE);
  }

  @Test
  public void complexImmutableWithCreatorPersistWithUpdate() {
    CreatorMagazine val = CreatorMagazine.newRandom();
    final long id = val.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    final Author author = Author.newRandom();
    author.id = val.author().id;
    val = CreatorMagazine.create(
        id,
        Utils.randomTableName(),
        author,
        SimpleValueWithBuilder.newRandom()
            .id(Select.column(SIMPLE_VALUE_WITH_BUILDER.ID).from(SIMPLE_VALUE_WITH_BUILDER).takeFirst().execute())
            .build(),
        SimpleValueWithCreator.newRandom(Select.column(SIMPLE_VALUE_WITH_CREATOR.ID).from(SIMPLE_VALUE_WITH_CREATOR).takeFirst().execute())
    );

    final long persistId = val.persist().execute();
    assertThat(persistId).isEqualTo(id);

    assertImmutableValue(val, CREATOR_MAGAZINE);
  }

  @Test
  public void simpleMutablePersistWithInsertIgnoringNull() {
    final Author val = Author.newRandom();
    val.name = null;
    val.boxedBoolean = null;
    final long id = val.persist().ignoreNullValues().execute();

    assertThat(id).isNotEqualTo(-1);
    assertThat(val.id).isEqualTo(id);

    assertMutableValue(val, AUTHOR);
  }

  @Test
  public void simpleImmutableWithBuilderPersistWithInsertIgnoringNull() {
    final SimpleValueWithBuilderAndNullableFields val = SimpleValueWithBuilderAndNullableFields.newRandom()
        .string(null)
        .boxedBoolean(null)
        .build();
    final long id = val.persist().ignoreNullValues().execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS);
  }

  @Test
  public void simpleImmutableWithCreatorPersistWithInsertIgnoringNull() {
    SimpleValueWithCreatorAndNullableFields val = SimpleValueWithCreatorAndNullableFields.create(
        null,
        null,
        new Random().nextInt()
    );
    final long id = val.persist().ignoreNullValues().execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS);
  }

  @Test
  public void complexMutablePersistWithInsertIgnoringNull() {
    final Magazine val = Magazine.newRandom();
    val.name = null;
    final long id = val.persist().ignoreNullValues().execute();

    assertThat(id).isNotEqualTo(-1);
    assertThat(val.id).isEqualTo(id);

    assertMutableValue(val, MAGAZINE);
  }

  @Test
  public void complexMutablePersistWithInsertIgnoringComplexNull() {
    final Magazine val = Magazine.newRandom();
    val.name = null;
    val.author = null;
    final long id = val.persist().ignoreNullValues().execute();

    assertThat(id).isNotEqualTo(-1);
    assertThat(val.id).isEqualTo(id);

    assertMutableValue(val, MAGAZINE);
  }

  @Test
  public void complexImmutableWithBuilderPersistWithInsertIgnoringNull() {
    final BuilderMagazine val = BuilderMagazine.newRandom()
        .name(null)
        .build();
    final long id = val.persist().ignoreNullValues().execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, BUILDER_MAGAZINE);
  }

  @Test
  public void complexImmutableWithBuilderPersistWithInsertIgnoringComplexNull() {
    final BuilderMagazine val = BuilderMagazine.newRandom()
        .name(null)
        .author(null)
        .build();
    final long id = val.persist().ignoreNullValues().execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, BUILDER_MAGAZINE);
  }

  @Test
  public void complexImmutableWithCreatorPersistWithInsertIgnoringNull() {
    final Random r = new Random();
    CreatorMagazine val = CreatorMagazine.create(
        r.nextLong(),
        null,
        Author.newRandom(),
        SimpleValueWithBuilder.newRandom().build(),
        SimpleValueWithCreator.newRandom()
    );

    final long id = val.persist().ignoreNullValues().execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, CREATOR_MAGAZINE);
  }

  @Test
  public void complexImmutableWithCreatorPersistWithInsertIgnoringComplexNull() {
    final Random r = new Random();
    CreatorMagazine val = CreatorMagazine.create(
        r.nextLong(),
        null,
        null,
        SimpleValueWithBuilder.newRandom().build(),
        SimpleValueWithCreator.newRandom()
    );

    final long id = val.persist().ignoreNullValues().execute();

    assertThat(id).isNotEqualTo(-1);
    assertImmutableValue(val, CREATOR_MAGAZINE);
  }

  @Test
  public void simpleMutablePersistWithUpdateIgnoringNull() {
    final Author insertVal = Author.newRandom();
    final long id = insertVal.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    final Author updateVal = Author.newRandom();
    updateVal.id = id;
    updateVal.name = null;
    updateVal.boxedBoolean = null;

    final long persistId = updateVal.persist().ignoreNullValues().execute();
    assertThat(persistId).isEqualTo(id);

    final Author val = new Author();
    val.id = id;
    val.name = insertVal.name;
    val.boxedBoolean = insertVal.boxedBoolean;
    val.primitiveBoolean = updateVal.primitiveBoolean;

    assertMutableValue(val, AUTHOR);
  }

  @Test
  public void simpleImmutableWithBuilderPersistWithUpdateIgnoringNull() {
    final SimpleValueWithBuilderAndNullableFields insertVal = SimpleValueWithBuilderAndNullableFields.newRandom().build();
    final long id = insertVal.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    final SimpleValueWithBuilderAndNullableFields updateVal = SimpleValueWithBuilderAndNullableFields.newRandom()
        .id(id)
        .string(null)
        .boxedBoolean(null)
        .build();
    final long persistId = updateVal.persist().ignoreNullValues().execute();
    assertThat(persistId).isEqualTo(id);

    final SimpleValueWithBuilderAndNullableFields val = SimpleValueWithBuilderAndNullableFields.builder()
        .id(id)
        .string(insertVal.string())
        .boxedBoolean(insertVal.boxedBoolean())
        .boxedInteger(updateVal.boxedInteger())
        .build();

    assertImmutableValue(val, SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS);
  }

  @Test
  public void simpleImmutableWithCreatorPersistWithUpdateIgnoringNull() {
    final SimpleValueWithCreatorAndNullableFields insertVal = SimpleValueWithCreatorAndNullableFields.newRandom();
    final long id = insertVal.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    final SimpleValueWithCreatorAndNullableFields updateVal = SimpleValueWithCreatorAndNullableFields.createWithId(
        id,
        null,
        null,
        new Random().nextInt()
    );
    final long persistId = updateVal.persist().ignoreNullValues().execute();
    assertThat(persistId).isEqualTo(id);

    final SimpleValueWithCreatorAndNullableFields val = SimpleValueWithCreatorAndNullableFields.createWithId(
        id,
        insertVal.string(),
        insertVal.boxedBoolean(),
        updateVal.boxedInteger()
    );

    assertImmutableValue(val, SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS);
  }

  @Test
  public void complexMutablePersistWithUpdateIgnoringNull() {
    final Magazine insertVal = Magazine.newRandom();
    final long id = insertVal.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    final long authorId = insertVal.author.id;
    final Magazine updateVal = Magazine.newRandom();
    updateVal.id = id;
    updateVal.name = null;
    updateVal.author.id = authorId;

    final long persistId = updateVal.persist().ignoreNullValues().execute();
    assertThat(persistId).isEqualTo(id);

    final Magazine val = new Magazine();
    val.id = id;
    val.name = insertVal.name;
    val.author = updateVal.author;
    val.nrOfReleases = updateVal.nrOfReleases;

    assertMutableValue(val, MAGAZINE);
  }

  @Test
  public void complexMutablePersistWithUpdateIgnoringComplexNull() {
    final Magazine insertVal = Magazine.newRandom();
    final long id = insertVal.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    final Magazine updateVal = Magazine.newRandom();
    updateVal.id = id;
    updateVal.name = null;
    updateVal.author = null;

    final long persistId = updateVal.persist().ignoreNullValues().execute();
    assertThat(persistId).isEqualTo(id);

    final Magazine val = new Magazine();
    val.id = id;
    val.name = insertVal.name;
    val.author = insertVal.author;
    val.nrOfReleases = updateVal.nrOfReleases;

    assertMutableValue(val, MAGAZINE);
  }

  @Test
  public void complexImmutableWithBuilderPersistWithUpdateIgnoringNull() {
    final BuilderMagazine insertVal = BuilderMagazine.newRandom().build();
    final long id = insertVal.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    final Author author = Author.newRandom();
    author.id = insertVal.author().id;
    final SimpleValueWithBuilder simpleValueWithBuilder = SimpleValueWithBuilder.newRandom()
        .id(Select.column(SIMPLE_VALUE_WITH_BUILDER.ID).from(SIMPLE_VALUE_WITH_BUILDER).takeFirst().execute())
        .build();
    final SimpleValueWithCreator simpleValueWithCreator = SimpleValueWithCreator.newRandom(Select.column(SIMPLE_VALUE_WITH_CREATOR.ID).from(SIMPLE_VALUE_WITH_CREATOR).takeFirst().execute());
    final BuilderMagazine updateVal = BuilderMagazine.builder()
        .id(id)
        .name(null)
        .author(author)
        .simpleValueWithBuilder(simpleValueWithBuilder)
        .simpleValueWithCreator(simpleValueWithCreator)
        .build();

    final long persistId = updateVal.persist().ignoreNullValues().execute();
    assertThat(persistId).isEqualTo(id);

    final BuilderMagazine val = BuilderMagazine.builder()
        .id(id)
        .name(insertVal.name())
        .author(author)
        .simpleValueWithBuilder(simpleValueWithBuilder)
        .simpleValueWithCreator(simpleValueWithCreator)
        .build();

    assertImmutableValue(val, BUILDER_MAGAZINE);
  }

  @Test
  public void complexImmutableWithBuilderPersistWithUpdateIgnoringComplexNull() {
    final BuilderMagazine insertVal = BuilderMagazine.newRandom().build();
    final long id = insertVal.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    final SimpleValueWithCreator simpleValueWithCreator = SimpleValueWithCreator.newRandom(Select.column(SIMPLE_VALUE_WITH_CREATOR.ID).from(SIMPLE_VALUE_WITH_CREATOR).takeFirst().execute());
    final BuilderMagazine updateVal = BuilderMagazine.builder()
        .id(id)
        .name(null)
        .author(null)
        .simpleValueWithBuilder(null)
        .simpleValueWithCreator(simpleValueWithCreator)
        .build();

    final long persistId = updateVal.persist().ignoreNullValues().execute();
    assertThat(persistId).isEqualTo(id);

    final SimpleValueWithBuilder simpleValueWithBuilder = insertVal.simpleValueWithBuilder()
        .copy()
        .id(Select.column(SIMPLE_VALUE_WITH_BUILDER.ID).from(SIMPLE_VALUE_WITH_BUILDER).takeFirst().execute())
        .build();
    final BuilderMagazine val = BuilderMagazine.builder()
        .id(id)
        .name(insertVal.name())
        .author(insertVal.author())
        .simpleValueWithBuilder(simpleValueWithBuilder)
        .simpleValueWithCreator(simpleValueWithCreator)
        .build();

    assertImmutableValue(val, BUILDER_MAGAZINE);
  }

  @Test
  public void complexImmutableWithCreatorPersistWithUpdateIgnoringNull() {
    final CreatorMagazine insertVal = CreatorMagazine.newRandom();
    final long id = insertVal.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    final Author author = Author.newRandom();
    author.id = insertVal.author().id;
    final SimpleValueWithBuilder simpleValueWithBuilder = SimpleValueWithBuilder.newRandom()
        .id(Select.column(SIMPLE_VALUE_WITH_BUILDER.ID).from(SIMPLE_VALUE_WITH_BUILDER).takeFirst().execute())
        .build();
    final SimpleValueWithCreator simpleValueWithCreator = SimpleValueWithCreator.newRandom(Select.column(SIMPLE_VALUE_WITH_CREATOR.ID).from(SIMPLE_VALUE_WITH_CREATOR).takeFirst().execute());
    final CreatorMagazine updateVal = CreatorMagazine.create(
        id,
        null,
        author,
        simpleValueWithBuilder,
        simpleValueWithCreator
    );

    final long persistId = updateVal.persist().ignoreNullValues().execute();
    assertThat(persistId).isEqualTo(id);

    final CreatorMagazine val = CreatorMagazine.create(
        id,
        insertVal.name(),
        author,
        simpleValueWithBuilder,
        simpleValueWithCreator
    );

    assertImmutableValue(val, CREATOR_MAGAZINE);
  }

  @Test
  public void complexImmutableWithCreatorPersistWithUpdateIgnoringComplexNull() {
    final CreatorMagazine insertVal = CreatorMagazine.newRandom();
    final long id = insertVal.insert().execute();
    assertThat(id).isNotEqualTo(-1);

    final SimpleValueWithCreator simpleValueWithCreator = SimpleValueWithCreator.newRandom(Select.column(SIMPLE_VALUE_WITH_CREATOR.ID).from(SIMPLE_VALUE_WITH_CREATOR).takeFirst().execute());
    final CreatorMagazine updateVal = CreatorMagazine.create(
        id,
        null,
        null,
        null,
        simpleValueWithCreator
    );

    final long persistId = updateVal.persist().ignoreNullValues().execute();
    assertThat(persistId).isEqualTo(id);

    final SimpleValueWithBuilder simpleValueWithBuilder = insertVal.simpleValueWithBuilder()
        .copy()
        .id(Select.column(SIMPLE_VALUE_WITH_BUILDER.ID).from(SIMPLE_VALUE_WITH_BUILDER).takeFirst().execute())
        .build();
    final CreatorMagazine val = CreatorMagazine.create(
        id,
        insertVal.name(),
        insertVal.author(),
        simpleValueWithBuilder,
        simpleValueWithCreator
    );

    assertImmutableValue(val, CREATOR_MAGAZINE);
  }

  @Test
  public void simpleMutableBulkInsert() {
    final List<Author> vals = createVals(new Function<Integer, Author>() {
      @Override
      public Author apply(Integer integer) {
        return Author.newRandom();
      }
    });
    final boolean success = Author.insert(vals).execute();

    assertThat(success).isTrue();

    assertMutableValue(vals, AUTHOR);
  }

  @Test
  public void simpleImmutableWithBuilderBulkInsert() {
    final List<SimpleValueWithBuilder> vals = createVals(new Function<Integer, SimpleValueWithBuilder>() {
      @Override
      public SimpleValueWithBuilder apply(Integer integer) {
        return SimpleValueWithBuilder.newRandom().build();
      }
    });
    final boolean success = SimpleValueWithBuilder.insert(vals).execute();

    assertThat(success).isTrue();
    assertImmutableValue(vals, SIMPLE_VALUE_WITH_BUILDER);
  }

  @Test
  public void simpleImmutableWithCreatorBulkInsert() {
    final List<SimpleValueWithCreator> vals = createVals(new Function<Integer, SimpleValueWithCreator>() {
      @Override
      public SimpleValueWithCreator apply(Integer integer) {
        return SimpleValueWithCreator.newRandom();
      }
    });
    final boolean success = SimpleValueWithCreator.insert(vals).execute();

    assertThat(success).isTrue();
    assertImmutableValue(vals, SIMPLE_VALUE_WITH_CREATOR);
  }

  @Test
  public void dataClassWithMethodsBulkInsert() {
    final List<DataClassWithMethods> vals = createVals(new Function<Integer, DataClassWithMethods>() {
      @Override
      public DataClassWithMethods apply(Integer integer) {
        return DataClassWithMethods.newRandom();
      }
    });
    final boolean success = DataClassWithMethods.insert(vals).execute();

    assertThat(success).isTrue();
    assertImmutableValue(vals, DATA_CLASS_WITH_METHODS);
  }

  @Test
  public void dataClassWithFieldsBulkInsert() {
    final List<DataClassWithFields> vals = createVals(new Function<Integer, DataClassWithFields>() {
      @Override
      public DataClassWithFields apply(Integer integer) {
        return DataClassWithFields.newRandom();
      }
    });
    final boolean success = DataClassWithFields.insert(vals).execute();

    assertThat(success).isTrue();
    assertImmutableValue(vals, DATA_CLASS_WITH_FIELDS);
  }

  @Test
  public void complexMutableBulkInsert() {
    final List<Magazine> val = createVals(new Function<Integer, Magazine>() {
      @Override
      public Magazine apply(Integer integer) {
        return Magazine.newRandom();
      }
    });
    final boolean success = Magazine.insert(val).execute();

    assertThat(success).isTrue();
    assertMutableValue(val, MAGAZINE);
  }

  @Test
  public void complexImmutableWithBuilderBulkInsert() {
    final List<BuilderMagazine> val = createVals(new Function<Integer, BuilderMagazine>() {
      @Override
      public BuilderMagazine apply(Integer integer) {
        return BuilderMagazine.newRandom().build();
      }
    });
    final boolean success = BuilderMagazine.insert(val).execute();

    assertThat(success).isTrue();
    assertImmutableValue(val, BUILDER_MAGAZINE);
  }

  @Test
  public void complexImmutableWithCreatorBulkInsert() {
    final List<CreatorMagazine> val = createVals(new Function<Integer, CreatorMagazine>() {
      @Override
      public CreatorMagazine apply(Integer integer) {
        return CreatorMagazine.newRandom();
      }
    });
    final boolean success = CreatorMagazine.insert(val).execute();

    assertThat(success).isTrue();
    assertImmutableValue(val, CREATOR_MAGAZINE);
  }

  @Test
  public void simpleMutableBulkUpdate() {
    List<Author> vals = createVals(new Function<Integer, Author>() {
      @Override
      public Author apply(Integer integer) {
        return Author.newRandom();
      }
    });
    boolean success = Author.insert(vals).execute();
    assertThat(success).isTrue();

    vals = updateVals(vals, new Function<Author, Author>() {
      @Override
      public Author apply(Author author) {
        final Author val = Author.newRandom();
        val.id = author.id;
        return val;
      }
    });

    success = Author.update(vals).execute();
    assertThat(success).isTrue();

    assertMutableValue(vals, AUTHOR);
  }

  @Test
  public void simpleImmutableWithBuilderBulkUpdate() {
    List<SimpleValueWithBuilder> vals = createVals(new Function<Integer, SimpleValueWithBuilder>() {
      @Override
      public SimpleValueWithBuilder apply(Integer integer) {
        return SimpleValueWithBuilder.newRandom().build();
      }
    });
    boolean success = SimpleValueWithBuilder.insert(vals).execute();
    assertThat(success).isTrue();

    vals = updateVals(Select.from(SIMPLE_VALUE_WITH_BUILDER).execute(), new Function<SimpleValueWithBuilder, SimpleValueWithBuilder>() {
      @Override
      public SimpleValueWithBuilder apply(SimpleValueWithBuilder val) {
        return SimpleValueWithBuilder.newRandom()
            .id(val.id())
            .build();
      }
    });

    success = SimpleValueWithBuilder.update(vals).execute();
    assertThat(success).isTrue();

    assertImmutableValue(vals, SIMPLE_VALUE_WITH_BUILDER);
  }

  @Test
  public void simpleImmutableWithCreatorBulkUpdate() {
    List<SimpleValueWithCreator> vals = createVals(new Function<Integer, SimpleValueWithCreator>() {
      @Override
      public SimpleValueWithCreator apply(Integer integer) {
        return SimpleValueWithCreator.newRandom();
      }
    });
    boolean success = SimpleValueWithCreator.insert(vals).execute();
    assertThat(success).isTrue();

    vals = updateVals(Select.from(SIMPLE_VALUE_WITH_CREATOR).execute(), new Function<SimpleValueWithCreator, SimpleValueWithCreator>() {
      @Override
      public SimpleValueWithCreator apply(SimpleValueWithCreator val) {
        return SimpleValueWithCreator.newRandom(val.id());
      }
    });

    success = SimpleValueWithCreator.update(vals).execute();
    assertThat(success).isTrue();

    assertImmutableValue(vals, SIMPLE_VALUE_WITH_CREATOR);
  }

  @Test
  public void dataClassWithMethodsBulkUpdate() {
    List<DataClassWithMethods> vals = createVals(new Function<Integer, DataClassWithMethods>() {
      @Override
      public DataClassWithMethods apply(Integer integer) {
        return DataClassWithMethods.newRandom();
      }
    });
    boolean success = DataClassWithMethods.insert(vals).execute();
    assertThat(success).isTrue();

    vals = updateVals(Select.from(DATA_CLASS_WITH_METHODS).execute(), new Function<DataClassWithMethods, DataClassWithMethods>() {
      @Override
      public DataClassWithMethods apply(DataClassWithMethods val) {
        return DataClassWithMethods.newRandom(val.getId());
      }
    });

    success = DataClassWithMethods.update(vals).execute();
    assertThat(success).isTrue();

    assertImmutableValue(vals, DATA_CLASS_WITH_METHODS);
  }

  @Test
  public void dataClassWithFieldsBulkUpdate() {
    List<DataClassWithFields> vals = createVals(new Function<Integer, DataClassWithFields>() {
      @Override
      public DataClassWithFields apply(Integer integer) {
        return DataClassWithFields.newRandom();
      }
    });
    boolean success = DataClassWithFields.insert(vals).execute();
    assertThat(success).isTrue();

    vals = updateVals(Select.from(DATA_CLASS_WITH_FIELDS).execute(), new Function<DataClassWithFields, DataClassWithFields>() {
      @Override
      public DataClassWithFields apply(DataClassWithFields val) {
        return DataClassWithFields.newRandom(val.id);
      }
    });

    success = DataClassWithFields.update(vals).execute();
    assertThat(success).isTrue();

    assertImmutableValue(vals, DATA_CLASS_WITH_FIELDS);
  }

  @Test
  public void complexMutableBulkUpdate() {
    List<Magazine> vals = createVals(new Function<Integer, Magazine>() {
      @Override
      public Magazine apply(Integer integer) {
        return Magazine.newRandom();
      }
    });
    boolean success = Magazine.insert(vals).execute();
    assertThat(success).isTrue();

    vals = updateVals(vals, new Function<Magazine, Magazine>() {
      @Override
      public Magazine apply(Magazine magazine) {
        final Magazine val = Magazine.newRandom();
        val.id = magazine.id;
        val.author.id = magazine.author.id;
        return val;
      }
    });

    success = Magazine.update(vals).execute();
    assertThat(success).isTrue();

    assertMutableValue(vals, MAGAZINE);
  }

  @Test
  public void complexImmutableWithBuilderBulkUpdate() {
    List<BuilderMagazine> vals = createVals(new Function<Integer, BuilderMagazine>() {
      @Override
      public BuilderMagazine apply(Integer integer) {
        return BuilderMagazine.newRandom().build();
      }
    });
    boolean success = BuilderMagazine.insert(vals).execute();
    assertThat(success).isTrue();

    vals = updateVals(Select.from(BUILDER_MAGAZINE).execute(), new Function<BuilderMagazine, BuilderMagazine>() {
      @Override
      public BuilderMagazine apply(BuilderMagazine builderMagazine) {
        final Author author = Author.newRandom();
        author.id = builderMagazine.author().id;
        return BuilderMagazine.builder()
            .id(builderMagazine.id())
            .name(Utils.randomTableName())
            .author(author)
            .simpleValueWithBuilder(SimpleValueWithBuilder.newRandom()
                .id(builderMagazine.simpleValueWithBuilder().id())
                .build())
            .simpleValueWithCreator(SimpleValueWithCreator.newRandom(builderMagazine.simpleValueWithCreator().id()))
            .build();
      }
    });

    success = BuilderMagazine.update(vals).execute();
    assertThat(success).isTrue();

    assertImmutableValue(vals, BUILDER_MAGAZINE);
  }

  @Test
  public void complexImmutableWithCreatorBulkUpdate() {
    List<CreatorMagazine> vals = createVals(new Function<Integer, CreatorMagazine>() {
      @Override
      public CreatorMagazine apply(Integer integer) {
        return CreatorMagazine.newRandom();
      }
    });
    boolean success = CreatorMagazine.insert(vals).execute();
    assertThat(success).isTrue();

    vals = updateVals(Select.from(CREATOR_MAGAZINE).execute(), new Function<CreatorMagazine, CreatorMagazine>() {
      @Override
      public CreatorMagazine apply(CreatorMagazine builderMagazine) {
        final Author author = Author.newRandom();
        author.id = builderMagazine.author().id;
        return CreatorMagazine.create(
            builderMagazine.id(),
            Utils.randomTableName(),
            author,
            SimpleValueWithBuilder.newRandom()
                .id(builderMagazine.simpleValueWithBuilder().id())
                .build(),
            SimpleValueWithCreator.newRandom(builderMagazine.simpleValueWithCreator().id()));
      }
    });

    success = CreatorMagazine.update(vals).execute();
    assertThat(success).isTrue();

    assertImmutableValue(vals, CREATOR_MAGAZINE);
  }

  @Test
  public void simpleMutableBulkPersistWithInsert() {
    final List<Author> vals = createVals(new Function<Integer, Author>() {
      @Override
      public Author apply(Integer integer) {
        return Author.newRandom();
      }
    });
    final boolean success = Author.persist(vals).execute();

    assertThat(success).isTrue();

    assertMutableValue(vals, AUTHOR);
  }

  @Test
  public void simpleImmutableWithBuilderBulkPersistWithInsert() {
    final List<SimpleValueWithBuilder> vals = createVals(new Function<Integer, SimpleValueWithBuilder>() {
      @Override
      public SimpleValueWithBuilder apply(Integer integer) {
        return SimpleValueWithBuilder.newRandom().build();
      }
    });
    final boolean success = SimpleValueWithBuilder.persist(vals).execute();

    assertThat(success).isTrue();
    assertImmutableValue(vals, SIMPLE_VALUE_WITH_BUILDER);
  }

  @Test
  public void simpleImmutableWithCreatorBulkPersistWithInsert() {
    final List<SimpleValueWithCreator> vals = createVals(new Function<Integer, SimpleValueWithCreator>() {
      @Override
      public SimpleValueWithCreator apply(Integer integer) {
        return SimpleValueWithCreator.newRandom();
      }
    });
    final boolean success = SimpleValueWithCreator.persist(vals).execute();

    assertThat(success).isTrue();
    assertImmutableValue(vals, SIMPLE_VALUE_WITH_CREATOR);
  }

  @Test
  public void dataClassWithMethodsBulkPersistWithInsert() {
    final List<DataClassWithMethods> vals = createVals(new Function<Integer, DataClassWithMethods>() {
      @Override
      public DataClassWithMethods apply(Integer integer) {
        return DataClassWithMethods.newRandom();
      }
    });
    final boolean success = DataClassWithMethods.persist(vals).execute();

    assertThat(success).isTrue();
    assertImmutableValue(vals, DATA_CLASS_WITH_METHODS);
  }

  @Test
  public void DataClassWithFieldsBulkPersistWithInsert() {
    final List<DataClassWithFields> vals = createVals(new Function<Integer, DataClassWithFields>() {
      @Override
      public DataClassWithFields apply(Integer integer) {
        return DataClassWithFields.newRandom();
      }
    });
    final boolean success = DataClassWithFields.persist(vals).execute();

    assertThat(success).isTrue();
    assertImmutableValue(vals, DATA_CLASS_WITH_FIELDS);
  }

  @Test
  public void complexMutableBulkPersistWithInsert() {
    final List<Magazine> val = createVals(new Function<Integer, Magazine>() {
      @Override
      public Magazine apply(Integer integer) {
        return Magazine.newRandom();
      }
    });
    final boolean success = Magazine.persist(val).execute();

    assertThat(success).isTrue();
    assertMutableValue(val, MAGAZINE);
  }

  @Test
  public void complexImmutableWithBuilderBulkPersistWithInsert() {
    final List<BuilderMagazine> val = createVals(new Function<Integer, BuilderMagazine>() {
      @Override
      public BuilderMagazine apply(Integer integer) {
        return BuilderMagazine.newRandom().build();
      }
    });
    final boolean success = BuilderMagazine.persist(val).execute();

    assertThat(success).isTrue();
    assertImmutableValue(val, BUILDER_MAGAZINE);
  }

  @Test
  public void complexImmutableWithCreatorBulkPersistWithInsert() {
    final List<CreatorMagazine> val = createVals(new Function<Integer, CreatorMagazine>() {
      @Override
      public CreatorMagazine apply(Integer integer) {
        return CreatorMagazine.newRandom();
      }
    });
    final boolean success = CreatorMagazine.persist(val).execute();

    assertThat(success).isTrue();
    assertImmutableValue(val, CREATOR_MAGAZINE);
  }

  @Test
  public void simpleMutableBulkPersistWithUpdate() {
    List<Author> vals = createVals(new Function<Integer, Author>() {
      @Override
      public Author apply(Integer integer) {
        return Author.newRandom();
      }
    });
    boolean success = Author.insert(vals).execute();
    assertThat(success).isTrue();

    vals = updateVals(vals, new Function<Author, Author>() {
      @Override
      public Author apply(Author author) {
        final Author val = Author.newRandom();
        val.id = author.id;
        return val;
      }
    });

    success = Author.persist(vals).execute();
    assertThat(success).isTrue();

    assertMutableValue(vals, AUTHOR);
  }

  @Test
  public void simpleImmutableWithBuilderBulkPersistWithUpdate() {
    List<SimpleValueWithBuilder> vals = createVals(new Function<Integer, SimpleValueWithBuilder>() {
      @Override
      public SimpleValueWithBuilder apply(Integer integer) {
        return SimpleValueWithBuilder.newRandom().build();
      }
    });
    boolean success = SimpleValueWithBuilder.insert(vals).execute();
    assertThat(success).isTrue();

    vals = updateVals(Select.from(SIMPLE_VALUE_WITH_BUILDER).execute(), new Function<SimpleValueWithBuilder, SimpleValueWithBuilder>() {
      @Override
      public SimpleValueWithBuilder apply(SimpleValueWithBuilder val) {
        return SimpleValueWithBuilder.newRandom()
            .id(val.id())
            .build();
      }
    });

    success = SimpleValueWithBuilder.persist(vals).execute();
    assertThat(success).isTrue();

    assertImmutableValue(vals, SIMPLE_VALUE_WITH_BUILDER);
  }

  @Test
  public void simpleImmutableWithCreatorBulkPersistWithUpdate() {
    List<SimpleValueWithCreator> vals = createVals(new Function<Integer, SimpleValueWithCreator>() {
      @Override
      public SimpleValueWithCreator apply(Integer integer) {
        return SimpleValueWithCreator.newRandom();
      }
    });
    boolean success = SimpleValueWithCreator.insert(vals).execute();
    assertThat(success).isTrue();

    vals = updateVals(Select.from(SIMPLE_VALUE_WITH_CREATOR).execute(), new Function<SimpleValueWithCreator, SimpleValueWithCreator>() {
      @Override
      public SimpleValueWithCreator apply(SimpleValueWithCreator val) {
        return SimpleValueWithCreator.newRandom(val.id());
      }
    });

    success = SimpleValueWithCreator.persist(vals).execute();
    assertThat(success).isTrue();

    assertImmutableValue(vals, SIMPLE_VALUE_WITH_CREATOR);
  }

  @Test
  public void dataClassWithMethodsBulkPersistWithUpdate() {
    List<DataClassWithMethods> vals = createVals(new Function<Integer, DataClassWithMethods>() {
      @Override
      public DataClassWithMethods apply(Integer integer) {
        return DataClassWithMethods.newRandom();
      }
    });
    boolean success = DataClassWithMethods.insert(vals).execute();
    assertThat(success).isTrue();

    vals = updateVals(Select.from(DATA_CLASS_WITH_METHODS).execute(), new Function<DataClassWithMethods, DataClassWithMethods>() {
      @Override
      public DataClassWithMethods apply(DataClassWithMethods val) {
        return DataClassWithMethods.newRandom(val.getId());
      }
    });

    success = DataClassWithMethods.persist(vals).execute();
    assertThat(success).isTrue();

    assertImmutableValue(vals, DATA_CLASS_WITH_METHODS);
  }

  @Test
  public void dataClassWithFieldsBulkPersistWithUpdate() {
    List<DataClassWithFields> vals = createVals(new Function<Integer, DataClassWithFields>() {
      @Override
      public DataClassWithFields apply(Integer integer) {
        return DataClassWithFields.newRandom();
      }
    });
    boolean success = DataClassWithFields.insert(vals).execute();
    assertThat(success).isTrue();

    vals = updateVals(Select.from(DATA_CLASS_WITH_FIELDS).execute(), new Function<DataClassWithFields, DataClassWithFields>() {
      @Override
      public DataClassWithFields apply(DataClassWithFields val) {
        return DataClassWithFields.newRandom(val.id);
      }
    });

    success = DataClassWithFields.persist(vals).execute();
    assertThat(success).isTrue();

    assertImmutableValue(vals, DATA_CLASS_WITH_FIELDS);
  }

  @Test
  public void complexMutableBulkPersistWithUpdate() {
    List<Magazine> vals = createVals(new Function<Integer, Magazine>() {
      @Override
      public Magazine apply(Integer integer) {
        return Magazine.newRandom();
      }
    });
    boolean success = Magazine.insert(vals).execute();
    assertThat(success).isTrue();

    vals = updateVals(vals, new Function<Magazine, Magazine>() {
      @Override
      public Magazine apply(Magazine magazine) {
        final Magazine val = Magazine.newRandom();
        val.id = magazine.id;
        val.author.id = magazine.author.id;
        return val;
      }
    });

    success = Magazine.persist(vals).execute();
    assertThat(success).isTrue();

    assertMutableValue(vals, MAGAZINE);
  }

  @Test
  public void complexImmutableWithBuilderBulkPersistWithUpdate() {
    List<BuilderMagazine> vals = createVals(new Function<Integer, BuilderMagazine>() {
      @Override
      public BuilderMagazine apply(Integer integer) {
        return BuilderMagazine.newRandom().build();
      }
    });
    boolean success = BuilderMagazine.insert(vals).execute();
    assertThat(success).isTrue();

    vals = updateVals(Select.from(BUILDER_MAGAZINE).execute(), new Function<BuilderMagazine, BuilderMagazine>() {
      @Override
      public BuilderMagazine apply(BuilderMagazine builderMagazine) {
        final Author author = Author.newRandom();
        author.id = builderMagazine.author().id;
        return BuilderMagazine.builder()
            .id(builderMagazine.id())
            .name(Utils.randomTableName())
            .author(author)
            .simpleValueWithBuilder(SimpleValueWithBuilder.newRandom()
                .id(builderMagazine.simpleValueWithBuilder().id())
                .build())
            .simpleValueWithCreator(SimpleValueWithCreator.newRandom(builderMagazine.simpleValueWithCreator().id()))
            .build();
      }
    });

    success = BuilderMagazine.persist(vals).execute();
    assertThat(success).isTrue();

    assertImmutableValue(vals, BUILDER_MAGAZINE);
  }

  @Test
  public void complexImmutableWithCreatorBulkPersistWithUpdate() {
    List<CreatorMagazine> vals = createVals(new Function<Integer, CreatorMagazine>() {
      @Override
      public CreatorMagazine apply(Integer integer) {
        return CreatorMagazine.newRandom();
      }
    });
    boolean success = CreatorMagazine.insert(vals).execute();
    assertThat(success).isTrue();

    vals = updateVals(Select.from(CREATOR_MAGAZINE).execute(), new Function<CreatorMagazine, CreatorMagazine>() {
      @Override
      public CreatorMagazine apply(CreatorMagazine builderMagazine) {
        final Author author = Author.newRandom();
        author.id = builderMagazine.author().id;
        return CreatorMagazine.create(
            builderMagazine.id(),
            Utils.randomTableName(),
            author,
            SimpleValueWithBuilder.newRandom()
                .id(builderMagazine.simpleValueWithBuilder().id())
                .build(),
            SimpleValueWithCreator.newRandom(builderMagazine.simpleValueWithCreator().id()));
      }
    });

    success = CreatorMagazine.persist(vals).execute();
    assertThat(success).isTrue();

    assertImmutableValue(vals, CREATOR_MAGAZINE);
  }

  @Test
  public void simpleMutableBulkPersistWithInsertIgnoringNull() {
    final List<Author> vals = createVals(new Function<Integer, Author>() {
      @Override
      public Author apply(Integer integer) {
        final Author val = Author.newRandom();
        val.name = null;
        val.boxedBoolean = null;
        return val;
      }
    });
    final boolean success = Author.persist(vals).ignoreNullValues().execute();

    assertThat(success).isTrue();

    assertMutableValue(vals, AUTHOR);
  }

  @Test
  public void simpleImmutableWithBuilderBulkPersistWithInsertIgnoringNull() {
    final List<SimpleValueWithBuilderAndNullableFields> vals = createVals(new Function<Integer, SimpleValueWithBuilderAndNullableFields>() {
      @Override
      public SimpleValueWithBuilderAndNullableFields apply(Integer integer) {
        return SimpleValueWithBuilderAndNullableFields.newRandom()
            .string(null)
            .boxedBoolean(null)
            .build();
      }
    });
    final boolean success = SimpleValueWithBuilderAndNullableFields.persist(vals).ignoreNullValues().execute();

    assertThat(success).isTrue();
    assertImmutableValue(vals, SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS);
  }

  @Test
  public void simpleImmutableWithCreatorBulkPersistWithInsertIgnoringNull() {
    final List<SimpleValueWithCreatorAndNullableFields> vals = createVals(new Function<Integer, SimpleValueWithCreatorAndNullableFields>() {
      @Override
      public SimpleValueWithCreatorAndNullableFields apply(Integer integer) {
        return SimpleValueWithCreatorAndNullableFields.create(
            null,
            null,
            new Random().nextInt()
        );
      }
    });
    final boolean success = SimpleValueWithCreatorAndNullableFields.persist(vals).ignoreNullValues().execute();

    assertThat(success).isTrue();
    assertImmutableValue(vals, SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS);
  }

  @Test
  public void complexMutableBulkPersistWithInsertIgnoringNull() {
    final List<Magazine> val = createVals(new Function<Integer, Magazine>() {
      @Override
      public Magazine apply(Integer integer) {
        final Magazine val = Magazine.newRandom();
        val.name = null;
        return val;
      }
    });
    final boolean success = Magazine.persist(val).ignoreNullValues().execute();

    assertThat(success).isTrue();
    assertMutableValue(val, MAGAZINE);
  }

  @Test
  public void complexMutableBulkPersistWithInsertIgnoringComplexNull() {
    final List<Magazine> val = createVals(new Function<Integer, Magazine>() {
      @Override
      public Magazine apply(Integer integer) {
        final Magazine val = Magazine.newRandom();
        val.name = null;
        val.author = null;
        return val;
      }
    });
    final boolean success = Magazine.persist(val).ignoreNullValues().execute();

    assertThat(success).isTrue();
    assertMutableValue(val, MAGAZINE);
  }

  @Test
  public void complexImmutableWithBuilderBulkPersistWithInsertIgnoringNull() {
    final List<BuilderMagazine> val = createVals(new Function<Integer, BuilderMagazine>() {
      @Override
      public BuilderMagazine apply(Integer integer) {
        return BuilderMagazine.newRandom()
            .name(null)
            .build();
      }
    });
    final boolean success = BuilderMagazine.persist(val).ignoreNullValues().execute();

    assertThat(success).isTrue();
    assertImmutableValue(val, BUILDER_MAGAZINE);
  }

  @Test
  public void complexImmutableWithBuilderBulkPersistWithInsertIgnoringComplexNull() {
    final List<BuilderMagazine> val = createVals(new Function<Integer, BuilderMagazine>() {
      @Override
      public BuilderMagazine apply(Integer integer) {
        return BuilderMagazine.newRandom()
            .name(null)
            .author(null)
            .build();
      }
    });
    final boolean success = BuilderMagazine.persist(val).ignoreNullValues().execute();

    assertThat(success).isTrue();
    assertImmutableValue(val, BUILDER_MAGAZINE);
  }

  @Test
  public void complexImmutableWithCreatorBulkPersistWithInsertIgnoringNull() {
    final List<CreatorMagazine> val = createVals(new Function<Integer, CreatorMagazine>() {
      @Override
      public CreatorMagazine apply(Integer integer) {
        final Random r = new Random();
        return CreatorMagazine.create(
            r.nextLong(),
            null,
            Author.newRandom(),
            SimpleValueWithBuilder.newRandom().build(),
            SimpleValueWithCreator.newRandom()
        );
      }
    });
    final boolean success = CreatorMagazine.persist(val).ignoreNullValues().execute();

    assertThat(success).isTrue();
    assertImmutableValue(val, CREATOR_MAGAZINE);
  }

  @Test
  public void complexImmutableWithCreatorBulkPersistWithInsertIgnoringComplexNull() {
    final List<CreatorMagazine> val = createVals(new Function<Integer, CreatorMagazine>() {
      @Override
      public CreatorMagazine apply(Integer integer) {
        final Random r = new Random();
        return CreatorMagazine.create(
            r.nextLong(),
            null,
            null,
            SimpleValueWithBuilder.newRandom().build(),
            SimpleValueWithCreator.newRandom()
        );
      }
    });
    final boolean success = CreatorMagazine.persist(val).ignoreNullValues().execute();

    assertThat(success).isTrue();
    assertImmutableValue(val, CREATOR_MAGAZINE);
  }

  @Test
  public void simpleMutableBulkPersistWithUpdateIgnoringNull() {
    final List<Author> insertVals = createVals(new Function<Integer, Author>() {
      @Override
      public Author apply(Integer integer) {
        return Author.newRandom();
      }
    });
    boolean success = Author.insert(insertVals).execute();
    assertThat(success).isTrue();

    final List<Author> updateVals = updateVals(insertVals, new Function<Author, Author>() {
      @Override
      public Author apply(Author author) {
        final Author val = Author.newRandom();
        val.id = author.id;
        val.name = null;
        val.boxedBoolean = null;
        return val;
      }
    });

    success = Author.persist(updateVals).ignoreNullValues().execute();
    assertThat(success).isTrue();

    final Iterator<Author> updateValsIter = updateVals.iterator();
    final List<Author> vals = updateVals(insertVals, new Function<Author, Author>() {
      @Override
      public Author apply(Author author) {
        final Author val = Author.newRandom();
        val.id = author.id;
        val.name = author.name;
        val.boxedBoolean = author.boxedBoolean;
        val.primitiveBoolean = updateValsIter.next().primitiveBoolean;
        return val;
      }
    });

    assertMutableValue(vals, AUTHOR);
  }

  @Test
  public void simpleImmutableWithBuilderBulkPersistWithUpdateIgnoringNull() {
    List<SimpleValueWithBuilderAndNullableFields> insertVals = createVals(new Function<Integer, SimpleValueWithBuilderAndNullableFields>() {
      @Override
      public SimpleValueWithBuilderAndNullableFields apply(Integer integer) {
        return SimpleValueWithBuilderAndNullableFields.newRandom().build();
      }
    });
    boolean success = SimpleValueWithBuilderAndNullableFields.insert(insertVals).execute();
    assertThat(success).isTrue();

    insertVals = Select.from(SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS).execute();
    final List<SimpleValueWithBuilderAndNullableFields> updateVals = updateVals(insertVals, new Function<SimpleValueWithBuilderAndNullableFields, SimpleValueWithBuilderAndNullableFields>() {
      @Override
      public SimpleValueWithBuilderAndNullableFields apply(SimpleValueWithBuilderAndNullableFields val) {
        return SimpleValueWithBuilderAndNullableFields.newRandom()
            .id(val.id())
            .string(null)
            .boxedBoolean(null)
            .build();
      }
    });

    success = SimpleValueWithBuilderAndNullableFields.persist(updateVals).ignoreNullValues().execute();
    assertThat(success).isTrue();

    final Iterator<SimpleValueWithBuilderAndNullableFields> updateValsIter = updateVals.iterator();
    final List<SimpleValueWithBuilderAndNullableFields> vals = updateVals(insertVals, new Function<SimpleValueWithBuilderAndNullableFields, SimpleValueWithBuilderAndNullableFields>() {
      @Override
      public SimpleValueWithBuilderAndNullableFields apply(SimpleValueWithBuilderAndNullableFields val) {
        return SimpleValueWithBuilderAndNullableFields.newRandom()
            .id(val.id())
            .string(val.string())
            .boxedBoolean(val.boxedBoolean())
            .boxedInteger(updateValsIter.next().boxedInteger())
            .build();
      }
    });

    assertImmutableValue(vals, SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS);
  }

  @Test
  public void simpleImmutableWithCreatorBulkPersistWithUpdateIgnoringNull() {
    List<SimpleValueWithCreatorAndNullableFields> insertVals = createVals(new Function<Integer, SimpleValueWithCreatorAndNullableFields>() {
      @Override
      public SimpleValueWithCreatorAndNullableFields apply(Integer integer) {
        return SimpleValueWithCreatorAndNullableFields.newRandom();
      }
    });
    boolean success = SimpleValueWithCreatorAndNullableFields.insert(insertVals).execute();
    assertThat(success).isTrue();

    insertVals = Select.from(SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS).execute();
    final List<SimpleValueWithCreatorAndNullableFields> updateVals = updateVals(insertVals, new Function<SimpleValueWithCreatorAndNullableFields, SimpleValueWithCreatorAndNullableFields>() {
      @Override
      public SimpleValueWithCreatorAndNullableFields apply(SimpleValueWithCreatorAndNullableFields val) {
        return SimpleValueWithCreatorAndNullableFields.createWithId(
            val.id(),
            null,
            null,
            new Random().nextInt()
        );
      }
    });

    success = SimpleValueWithCreatorAndNullableFields.persist(updateVals).ignoreNullValues().execute();
    assertThat(success).isTrue();

    final Iterator<SimpleValueWithCreatorAndNullableFields> updateValsIter = updateVals.iterator();
    final List<SimpleValueWithCreatorAndNullableFields> vals = updateVals(insertVals, new Function<SimpleValueWithCreatorAndNullableFields, SimpleValueWithCreatorAndNullableFields>() {
      @Override
      public SimpleValueWithCreatorAndNullableFields apply(SimpleValueWithCreatorAndNullableFields val) {
        return SimpleValueWithCreatorAndNullableFields.createWithId(
            val.id(),
            val.string(),
            val.boxedBoolean(),
            updateValsIter.next().boxedInteger()
        );
      }
    });

    assertImmutableValue(vals, SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS);
  }

  @Test
  public void complexMutableBulkPersistWithUpdateIgnoringNull() {
    final List<Magazine> insertVals = createVals(new Function<Integer, Magazine>() {
      @Override
      public Magazine apply(Integer integer) {
        return Magazine.newRandom();
      }
    });
    boolean success = Magazine.insert(insertVals).execute();
    assertThat(success).isTrue();

    final List<Magazine> updateVals = updateVals(insertVals, new Function<Magazine, Magazine>() {
      @Override
      public Magazine apply(Magazine magazine) {
        final Magazine val = Magazine.newRandom();
        val.id = magazine.id;
        val.name = null;
        val.author.id = magazine.author.id;
        return val;
      }
    });

    success = Magazine.persist(updateVals).ignoreNullValues().execute();
    assertThat(success).isTrue();

    final Iterator<Magazine> updateValsIter = updateVals.iterator();
    final List<Magazine> vals = updateVals(insertVals, new Function<Magazine, Magazine>() {
      @Override
      public Magazine apply(Magazine magazine) {
        final Magazine val = new Magazine();
        val.id = magazine.id;
        val.name = magazine.name;
        final Magazine updateVal = updateValsIter.next();
        val.author = updateVal.author;
        val.nrOfReleases = updateVal.nrOfReleases;
        return val;
      }
    });

    assertMutableValue(vals, MAGAZINE);
  }

  @Test
  public void complexMutableBulkPersistWithUpdateIgnoringComplexNull() {
    final List<Magazine> insertVals = createVals(new Function<Integer, Magazine>() {
      @Override
      public Magazine apply(Integer integer) {
        return Magazine.newRandom();
      }
    });
    boolean success = Magazine.insert(insertVals).execute();
    assertThat(success).isTrue();

    final List<Magazine> updateVals = updateVals(insertVals, new Function<Magazine, Magazine>() {
      @Override
      public Magazine apply(Magazine magazine) {
        final Magazine val = Magazine.newRandom();
        val.id = magazine.id;
        val.name = null;
        val.author = null;
        return val;
      }
    });

    success = Magazine.persist(updateVals).ignoreNullValues().execute();
    assertThat(success).isTrue();

    final Iterator<Magazine> updateValsIter = updateVals.iterator();
    final List<Magazine> vals = updateVals(insertVals, new Function<Magazine, Magazine>() {
      @Override
      public Magazine apply(Magazine magazine) {
        final Magazine val = new Magazine();
        val.id = magazine.id;
        val.name = magazine.name;
        val.author = magazine.author;
        val.nrOfReleases = updateValsIter.next().nrOfReleases;
        return val;
      }
    });

    assertMutableValue(vals, MAGAZINE);
  }

  @Test
  public void complexImmutableWithBuilderBulkPersistWithUpdateIgnoringNull() {
    List<BuilderMagazine> insertVals = createVals(new Function<Integer, BuilderMagazine>() {
      @Override
      public BuilderMagazine apply(Integer integer) {
        return BuilderMagazine.newRandom().build();
      }
    });
    boolean success = BuilderMagazine.insert(insertVals).execute();
    assertThat(success).isTrue();

    insertVals = Select.from(BUILDER_MAGAZINE).execute();
    final List<BuilderMagazine> updateVals = updateVals(insertVals, new Function<BuilderMagazine, BuilderMagazine>() {
      @Override
      public BuilderMagazine apply(BuilderMagazine builderMagazine) {
        final Author author = Author.newRandom();
        author.id = builderMagazine.author().id;
        return BuilderMagazine.builder()
            .id(builderMagazine.id())
            .name(null)
            .author(author)
            .simpleValueWithBuilder(SimpleValueWithBuilder.newRandom()
                .id(builderMagazine.simpleValueWithBuilder().id())
                .build())
            .simpleValueWithCreator(SimpleValueWithCreator.newRandom(builderMagazine.simpleValueWithCreator().id()))
            .build();
      }
    });

    success = BuilderMagazine.persist(updateVals).ignoreNullValues().execute();
    assertThat(success).isTrue();

    final Iterator<BuilderMagazine> updateValsIter = updateVals.iterator();
    final List<BuilderMagazine> vals = updateVals(insertVals, new Function<BuilderMagazine, BuilderMagazine>() {
      @Override
      public BuilderMagazine apply(BuilderMagazine builderMagazine) {
        final BuilderMagazine updateVal = updateValsIter.next();
        return BuilderMagazine.builder()
            .id(builderMagazine.id())
            .name(builderMagazine.name())
            .author(updateVal.author())
            .simpleValueWithBuilder(updateVal.simpleValueWithBuilder())
            .simpleValueWithCreator(updateVal.simpleValueWithCreator())
            .build();
      }
    });

    assertImmutableValue(vals, BUILDER_MAGAZINE);
  }

  @Test
  public void complexImmutableWithBuilderBulkPersistWithUpdateIgnoringComplexNull() {
    List<BuilderMagazine> insertVals = createVals(new Function<Integer, BuilderMagazine>() {
      @Override
      public BuilderMagazine apply(Integer integer) {
        return BuilderMagazine.newRandom().build();
      }
    });
    boolean success = BuilderMagazine.insert(insertVals).execute();
    assertThat(success).isTrue();

    insertVals = Select.from(BUILDER_MAGAZINE).queryDeep().execute();
    final List<BuilderMagazine> updateVals = updateVals(insertVals, new Function<BuilderMagazine, BuilderMagazine>() {
      @Override
      public BuilderMagazine apply(BuilderMagazine builderMagazine) {
        return BuilderMagazine.builder()
            .id(builderMagazine.id())
            .name(null)
            .author(null)
            .simpleValueWithBuilder(SimpleValueWithBuilder.newRandom()
                .id(builderMagazine.simpleValueWithBuilder().id())
                .build())
            .simpleValueWithCreator(SimpleValueWithCreator.newRandom(builderMagazine.simpleValueWithCreator().id()))
            .build();
      }
    });

    success = BuilderMagazine.persist(updateVals).ignoreNullValues().execute();
    assertThat(success).isTrue();

    final Iterator<BuilderMagazine> updateValsIter = updateVals.iterator();
    final List<BuilderMagazine> vals = updateVals(insertVals, new Function<BuilderMagazine, BuilderMagazine>() {
      @Override
      public BuilderMagazine apply(BuilderMagazine builderMagazine) {
        final BuilderMagazine updateVal = updateValsIter.next();
        return BuilderMagazine.builder()
            .id(builderMagazine.id())
            .name(builderMagazine.name())
            .author(builderMagazine.author())
            .simpleValueWithBuilder(updateVal.simpleValueWithBuilder())
            .simpleValueWithCreator(updateVal.simpleValueWithCreator())
            .build();
      }
    });

    assertImmutableValue(vals, BUILDER_MAGAZINE);
  }

  @Test
  public void complexImmutableWithCreatorBulkPersistWithUpdateIgnoringNull() {
    List<CreatorMagazine> insertVals = createVals(new Function<Integer, CreatorMagazine>() {
      @Override
      public CreatorMagazine apply(Integer integer) {
        return CreatorMagazine.newRandom();
      }
    });
    boolean success = CreatorMagazine.insert(insertVals).execute();
    assertThat(success).isTrue();

    insertVals = Select.from(CREATOR_MAGAZINE).execute();
    final List<CreatorMagazine> updateVals = updateVals(insertVals, new Function<CreatorMagazine, CreatorMagazine>() {
      @Override
      public CreatorMagazine apply(CreatorMagazine builderMagazine) {
        final Author author = Author.newRandom();
        author.id = builderMagazine.author().id;
        return CreatorMagazine.create(
            builderMagazine.id(),
            null,
            author,
            SimpleValueWithBuilder.newRandom()
                .id(builderMagazine.simpleValueWithBuilder().id())
                .build(),
            SimpleValueWithCreator.newRandom(builderMagazine.simpleValueWithCreator().id()));
      }
    });

    success = CreatorMagazine.persist(updateVals).ignoreNullValues().execute();
    assertThat(success).isTrue();

    final Iterator<CreatorMagazine> updateValsIter = updateVals.iterator();
    final List<CreatorMagazine> vals = updateVals(insertVals, new Function<CreatorMagazine, CreatorMagazine>() {
      @Override
      public CreatorMagazine apply(CreatorMagazine builderMagazine) {
        final CreatorMagazine updateVal = updateValsIter.next();
        return CreatorMagazine.create(
            builderMagazine.id(),
            builderMagazine.name(),
            updateVal.author(),
            updateVal.simpleValueWithBuilder(),
            updateVal.simpleValueWithCreator());
      }
    });

    assertImmutableValue(vals, CREATOR_MAGAZINE);
  }

  @Test
  public void complexImmutableWithCreatorBulkPersistWithUpdateIgnoringComplexNull() {
    List<CreatorMagazine> insertVals = createVals(new Function<Integer, CreatorMagazine>() {
      @Override
      public CreatorMagazine apply(Integer integer) {
        return CreatorMagazine.newRandom();
      }
    });
    boolean success = CreatorMagazine.insert(insertVals).execute();
    assertThat(success).isTrue();

    insertVals = Select.from(CREATOR_MAGAZINE).queryDeep().execute();
    final List<CreatorMagazine> updateVals = updateVals(insertVals, new Function<CreatorMagazine, CreatorMagazine>() {
      @Override
      public CreatorMagazine apply(CreatorMagazine builderMagazine) {
        return CreatorMagazine.create(
            builderMagazine.id(),
            null,
            null,
            SimpleValueWithBuilder.newRandom()
                .id(builderMagazine.simpleValueWithBuilder().id())
                .build(),
            SimpleValueWithCreator.newRandom(builderMagazine.simpleValueWithCreator().id()));
      }
    });

    success = CreatorMagazine.persist(updateVals).ignoreNullValues().execute();
    assertThat(success).isTrue();

    final Iterator<CreatorMagazine> updateValsIter = updateVals.iterator();
    final List<CreatorMagazine> vals = updateVals(insertVals, new Function<CreatorMagazine, CreatorMagazine>() {
      @Override
      public CreatorMagazine apply(CreatorMagazine builderMagazine) {
        final CreatorMagazine updateVal = updateValsIter.next();
        return CreatorMagazine.create(
            builderMagazine.id(),
            builderMagazine.name(),
            builderMagazine.author(),
            updateVal.simpleValueWithBuilder(),
            updateVal.simpleValueWithCreator());
      }
    });

    assertImmutableValue(vals, CREATOR_MAGAZINE);
  }

  private <T> void assertMutableValue(T val, Table<T> table) {
    assertThat(val).isEqualTo(Select.from(table).queryDeep().takeFirst().execute());
  }

  private <T> void assertMutableValue(List<T> vals, Table<T> table) {
    assertThat(vals).isEqualTo(Select.from(table).queryDeep().execute());
  }

  private <T extends ImmutableEquals> void assertImmutableValue(T val, Table<T> table) {
    assertThat(val.equalsWithoutId(Select.from(table).queryDeep().takeFirst().execute())).isTrue();
  }

  private <T extends ImmutableEquals> void assertImmutableValue(List<T> val, Table<T> table) {
    final List<T> expected = Select.from(table).queryDeep().execute();

    assertThat(val.size()).isEqualTo(expected.size());

    final Iterator<T> valIter = val.iterator();
    final Iterator<T> expectedIter = expected.iterator();
    while (valIter.hasNext()) {
      assertThat(valIter.next().equalsWithoutId(expectedIter.next())).isTrue();
    }
  }
}
