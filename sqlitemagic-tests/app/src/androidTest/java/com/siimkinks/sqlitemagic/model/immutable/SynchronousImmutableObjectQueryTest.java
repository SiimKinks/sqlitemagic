package com.siimkinks.sqlitemagic.model.immutable;

import android.database.Cursor;
import android.database.SQLException;
import android.support.test.runner.AndroidJUnit4;

import com.siimkinks.sqlitemagic.AuthorTable;
import com.siimkinks.sqlitemagic.CompiledCursorSelect;
import com.siimkinks.sqlitemagic.CompiledFirstSelect;
import com.siimkinks.sqlitemagic.CompiledSelect;
import com.siimkinks.sqlitemagic.Expr;
import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.Select.SelectN;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.model.Author;
import com.siimkinks.sqlitemagic.model.ComplexObjectWithSameLeafs;
import com.siimkinks.sqlitemagic.model.TestUtil.CreateCallback;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import lombok.Cleanup;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.BookTable.BOOK;
import static com.siimkinks.sqlitemagic.BuilderMagazineTable.BUILDER_MAGAZINE;
import static com.siimkinks.sqlitemagic.BuilderWithColumnOptionsTable.BUILDER_WITH_COLUMN_OPTIONS;
import static com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.COMPLEX_OBJECT_WITH_SAME_LEAFS;
import static com.siimkinks.sqlitemagic.ComplexValueWithBuilderTable.COMPLEX_VALUE_WITH_BUILDER;
import static com.siimkinks.sqlitemagic.ComplexValueWithCreatorTable.COMPLEX_VALUE_WITH_CREATOR;
import static com.siimkinks.sqlitemagic.CreatorMagazineTable.CREATOR_MAGAZINE;
import static com.siimkinks.sqlitemagic.CreatorWithColumnOptionsTable.CREATOR_WITH_COLUMN_OPTIONS;
import static com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE;
import static com.siimkinks.sqlitemagic.SimpleAllValuesImmutableWithBuilderTable.SIMPLE_ALL_VALUES_IMMUTABLE_WITH_BUILDER;
import static com.siimkinks.sqlitemagic.SimpleAllValuesImmutableWithCreatorTable.SIMPLE_ALL_VALUES_IMMUTABLE_WITH_CREATOR;
import static com.siimkinks.sqlitemagic.SimpleValueWithBuilderAndNullableFieldsTable.SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS;
import static com.siimkinks.sqlitemagic.SimpleValueWithBuilderTable.SIMPLE_VALUE_WITH_BUILDER;
import static com.siimkinks.sqlitemagic.SimpleValueWithCreatorAndNullableFieldsTable.SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS;
import static com.siimkinks.sqlitemagic.SimpleValueWithCreatorTable.SIMPLE_VALUE_WITH_CREATOR;

@RunWith(AndroidJUnit4.class)
public final class SynchronousImmutableObjectQueryTest {

  @Test
  public void countWithBuilder() {
    BuilderMagazine.deleteTable().execute();
    final int testSize = 10;
    for (int i = 0; i < testSize; i++) {
      final BuilderMagazine m = BuilderMagazine.newRandom()
          .name("asd")
          .build();
      final long persistId = m.persist().execute();
      assertThat(persistId).isNotEqualTo(-1);
      assertThat(persistId).isNotEqualTo(m.id());
    }
    assertThat(BuilderMagazine.newRandom().build().persist().execute()).isNotEqualTo(-1);
    assertThat(BuilderMagazine.newRandom().build().persist().execute()).isNotEqualTo(-1);
    assertThat(BuilderMagazine.newRandom().build().persist().execute()).isNotEqualTo(-1);

    assertThat(testSize).isNotEqualTo(Select
        .from(BUILDER_MAGAZINE)
        .count()
        .execute());
    assertThat(testSize).isEqualTo(Select
        .from(BUILDER_MAGAZINE)
        .where(BUILDER_MAGAZINE.NAME.is("asd"))
        .count()
        .execute());
  }

  @Test
  public void countWithCreator() {
    CreatorMagazine.deleteTable().execute();
    final int testSize = 10;
    for (int i = 0; i < testSize; i++) {
      CreatorMagazine m = CreatorMagazine.newRandom();
      m = CreatorMagazine.create(
          m.id(),
          "asd",
          m.author(),
          m.simpleValueWithBuilder(),
          m.simpleValueWithCreator()
      );
      final long persistId = m.persist().execute();
      assertThat(persistId).isNotEqualTo(-1);
      assertThat(persistId).isNotEqualTo(m.id());
    }
    assertThat(CreatorMagazine.newRandom().persist().execute()).isNotEqualTo(-1);
    assertThat(CreatorMagazine.newRandom().persist().execute()).isNotEqualTo(-1);
    assertThat(CreatorMagazine.newRandom().persist().execute()).isNotEqualTo(-1);

    assertThat(testSize).isNotEqualTo(Select
        .from(CREATOR_MAGAZINE)
        .count()
        .execute());
    assertThat(testSize).isEqualTo(Select
        .from(CREATOR_MAGAZINE)
        .where(CREATOR_MAGAZINE.NAME.is("asd"))
        .count()
        .execute());
  }

  @Test
  public void simpleEmptyTable() {
    final CompiledSelect<SimpleValueWithBuilder, SelectN> compiledBuilderSelect = Select
        .from(SIMPLE_VALUE_WITH_BUILDER).compile();
    final CompiledSelect<SimpleValueWithCreator, SelectN> compiledCreatorSelect = Select
        .from(SIMPLE_VALUE_WITH_CREATOR).compile();

    SimpleValueWithBuilder.deleteTable().execute();
    SimpleValueWithCreator.deleteTable().execute();

    final List<SimpleValueWithBuilder> allBuilderItems = compiledBuilderSelect.execute();
    assertThat(allBuilderItems).isNotNull();
    assertThat(allBuilderItems).isEmpty();
    assertThat(compiledBuilderSelect.takeFirst().execute()).isNull();

    final List<SimpleValueWithCreator> allCreatorItems = compiledCreatorSelect.execute();
    assertThat(allCreatorItems).isNotNull();
    assertThat(allCreatorItems).isEmpty();
    assertThat(compiledCreatorSelect.takeFirst().execute()).isNull();
  }

  @Test
  public void simpleWithBuilder() {
    SimpleValueWithBuilder.deleteTable().execute();

    SimpleValueWithBuilder object = SimpleValueWithBuilder.newRandom().build();

    long persistedId = object.insert().execute();
    SimpleValueWithBuilder expected = SqliteMagic_SimpleValueWithBuilder_Dao.setId(object, persistedId);

    final CompiledFirstSelect<SimpleValueWithBuilder, SelectN> compiledFirstSelect = Select
        .from(SIMPLE_VALUE_WITH_BUILDER)
        .where(SIMPLE_VALUE_WITH_BUILDER.ID.is(persistedId))
        .takeFirst();

    assertThat(expected).isEqualTo(compiledFirstSelect.execute());

    expected = SimpleValueWithBuilder.newRandom()
        .id(persistedId)
        .build();

    assertThat(expected.update().execute()).isTrue();
    assertThat(expected).isEqualTo(compiledFirstSelect.execute());

    expected = SimpleValueWithBuilder.newRandom()
        .id(persistedId)
        .build();

    assertThat(expected.persist().execute()).isEqualTo(persistedId);
    assertThat(expected).isEqualTo(compiledFirstSelect.execute());

    SimpleValueWithBuilder.deleteTable().execute();

    object = SimpleValueWithBuilder.newRandom().build();
    persistedId = object.persist().execute();

    expected = SqliteMagic_SimpleValueWithBuilder_Dao.setId(object, persistedId);
    assertThat(expected).isEqualTo(Select
        .from(SIMPLE_VALUE_WITH_BUILDER)
        .where(SIMPLE_VALUE_WITH_BUILDER.ID.is(persistedId))
        .takeFirst()
        .execute());
  }

  @Test
  public void simpleWithCreator() {
    SimpleValueWithCreator.deleteTable().execute();

    SimpleValueWithCreator object = SimpleValueWithCreator.newRandom();

    long persistedId = object.insert().execute();
    SimpleValueWithCreator expected = SqliteMagic_SimpleValueWithCreator_Dao.setId(object, persistedId);

    final CompiledFirstSelect<SimpleValueWithCreator, SelectN> compiledFirstSelect = Select
        .from(SIMPLE_VALUE_WITH_CREATOR)
        .where(SIMPLE_VALUE_WITH_CREATOR.ID.is(persistedId))
        .takeFirst();

    assertThat(expected).isEqualTo(compiledFirstSelect.execute());

    expected = SimpleValueWithCreator.newRandom(persistedId);

    assertThat(expected.update().execute()).isTrue();
    assertThat(expected).isEqualTo(compiledFirstSelect.execute());

    expected = SimpleValueWithCreator.newRandom(persistedId);

    assertThat(expected.persist().execute()).isEqualTo(persistedId);
    assertThat(expected).isEqualTo(compiledFirstSelect.execute());

    SimpleValueWithCreator.deleteTable().execute();

    object = SimpleValueWithCreator.newRandom();
    persistedId = object.persist().execute();

    expected = SqliteMagic_SimpleValueWithCreator_Dao.setId(object, persistedId);
    assertThat(expected).isEqualTo(Select
        .from(SIMPLE_VALUE_WITH_CREATOR)
        .where(SIMPLE_VALUE_WITH_CREATOR.ID.is(persistedId))
        .takeFirst()
        .execute());
  }

  @Test
  public void simplePersistIgnoringNullWithBuilder() {
    SimpleValueWithBuilderAndNullableFields.deleteTable().execute();
    SimpleValueWithBuilderAndNullableFields random = SimpleValueWithBuilderAndNullableFields.newRandom().build();
    final long id = random.persist().execute();
    assertThat(id).isNotEqualTo(-1);
    final CompiledFirstSelect<SimpleValueWithBuilderAndNullableFields, SelectN> firstSelect = Select
        .from(SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS)
        .where(SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS.ID.is(id))
        .takeFirst();
    SimpleValueWithBuilderAndNullableFields simple = firstSelect.execute();
    assertThat(simple.equalsWithoutId(random)).isTrue();

    SimpleValueWithBuilderAndNullableFields newSimple = simple.copy()
        .string(null)
        .boxedBoolean(null)
        .boxedInteger(null)
        .build();
    assertThat(newSimple.persist().ignoreNullValues().execute()).isEqualTo(id);
    assertThat(firstSelect.execute())
        .isEqualTo(simple);

    newSimple = simple.copy()
        .string("asd")
        .boxedBoolean(null)
        .boxedInteger(null)
        .build();
    assertThat(newSimple.persist().ignoreNullValues().execute()).isEqualTo(id);
    assertThat(firstSelect.execute())
        .isEqualTo(simple.copy().string("asd").build());
  }

  @Test
  public void simplePersistIgnoringNullWithCreator() {
    SimpleValueWithCreatorAndNullableFields.deleteTable().execute();
    SimpleValueWithCreatorAndNullableFields random = SimpleValueWithCreatorAndNullableFields.newRandom();
    final long id = random.persist().execute();
    assertThat(id).isNotEqualTo(-1);
    final CompiledFirstSelect<SimpleValueWithCreatorAndNullableFields, SelectN> firstSelect = Select
        .from(SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS)
        .where(SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS.ID.is(id))
        .takeFirst();
    SimpleValueWithCreatorAndNullableFields simple = firstSelect.execute();
    assertThat(simple.equalsWithoutId(random)).isTrue();

    SimpleValueWithCreatorAndNullableFields newSimple = SimpleValueWithCreatorAndNullableFields.createWithId(
        simple.id(),
        null,
        null,
        null
    );
    assertThat(newSimple.persist().ignoreNullValues().execute()).isEqualTo(id);
    assertThat(firstSelect.execute())
        .isEqualTo(simple);

    newSimple = SimpleValueWithCreatorAndNullableFields.createWithId(
        simple.id(),
        "asd",
        null,
        null);
    assertThat(newSimple.persist().ignoreNullValues().execute()).isEqualTo(id);
    assertThat(firstSelect.execute())
        .isEqualTo(SimpleValueWithCreatorAndNullableFields.createWithId(
            simple.id(),
            "asd",
            simple.boxedBoolean(),
            simple.boxedInteger()));
  }

  @Test
  public void simpleBulkWithBuilder() {
    final CompiledSelect<SimpleValueWithBuilderAndNullableFields, SelectN> compiledSelectAll = Select.from(SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS).compile();

    SimpleValueWithBuilderAndNullableFields.deleteTable().execute();
    final int count = 10;
    final List<SimpleValueWithBuilderAndNullableFields> randomValues = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      randomValues.add(SimpleValueWithBuilderAndNullableFields.newRandom().build());
    }

    assertThat(SimpleValueWithBuilderAndNullableFields.insert(randomValues).execute()).isTrue();

    List<SimpleValueWithBuilderAndNullableFields> values = compiledSelectAll.execute();
    for (int i = 0, randomValuesSize = randomValues.size(); i < randomValuesSize; i++) {
      SimpleValueWithBuilderAndNullableFields randomValue = randomValues.get(i);
      assertThat(randomValue.equalsWithoutId(values.get(i))).isTrue();
    }

    final List<SimpleValueWithBuilderAndNullableFields> newValues = new ArrayList<>(values.size());
    for (SimpleValueWithBuilderAndNullableFields v : values) {
      newValues.add(SimpleValueWithBuilderAndNullableFields.newRandom()
          .id(v.id())
          .build());
    }
    assertThat(SimpleValueWithBuilderAndNullableFields.update(newValues).execute()).isTrue();
    assertThat(newValues).containsExactlyElementsIn(compiledSelectAll.execute());

    newValues.clear();
    for (SimpleValueWithBuilderAndNullableFields v : values) {
      newValues.add(SimpleValueWithBuilderAndNullableFields.newRandom()
          .id(v.id())
          .build());
    }
    assertThat(SimpleValueWithBuilderAndNullableFields.persist(newValues).execute()).isTrue();
    assertThat(newValues).containsExactlyElementsIn(compiledSelectAll.execute());

    SimpleValueWithBuilderAndNullableFields.deleteTable().execute();
    assertThat(SimpleValueWithBuilderAndNullableFields.persist(newValues).execute()).isTrue();
    values = compiledSelectAll.execute();
    for (int i = 0, newValuesSize = newValues.size(); i < newValuesSize; i++) {
      SimpleValueWithBuilderAndNullableFields value = newValues.get(i);
      assertThat(value.equalsWithoutId(values.get(i))).isTrue();
    }
  }

  @Test
  public void simpleBulkPersistIgnoringNullWithBuilder() {
    final CompiledSelect<SimpleValueWithBuilderAndNullableFields, SelectN> compiledSelectAll = Select.from(SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS).compile();
    SimpleValueWithBuilderAndNullableFields.deleteTable().execute();
    final int count = 10;
    final List<SimpleValueWithBuilderAndNullableFields> randomValues = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      randomValues.add(SimpleValueWithBuilderAndNullableFields.newRandom().build());
    }
    assertThat(SimpleValueWithBuilderAndNullableFields.persist(randomValues).execute()).isTrue();
    final List<SimpleValueWithBuilderAndNullableFields> values = compiledSelectAll.execute();
    for (int i = 0, size = randomValues.size(); i < size; i++) {
      assertThat(randomValues.get(i).equalsWithoutId(values.get(i))).isTrue();
    }

    final List<SimpleValueWithBuilderAndNullableFields> newValues = new ArrayList<>(count);
    for (SimpleValueWithBuilderAndNullableFields value : values) {
      SimpleValueWithBuilderAndNullableFields a = SimpleValueWithBuilderAndNullableFields.builder()
          .id(value.id())
          .build();
      newValues.add(a);
    }
    assertThat(SimpleValueWithBuilderAndNullableFields.persist(newValues).ignoreNullValues().execute()).isTrue();
    assertThat(values).containsExactlyElementsIn(compiledSelectAll.execute());

    newValues.clear();
    for (SimpleValueWithBuilderAndNullableFields value : values) {
      newValues.add(SimpleValueWithBuilderAndNullableFields.builder()
          .id(value.id())
          .string("asd")
          .build());
    }
    assertThat(SimpleValueWithBuilderAndNullableFields.persist(newValues).ignoreNullValues().execute()).isTrue();
    final List<SimpleValueWithBuilderAndNullableFields> expected = new ArrayList<>(values.size());
    for (SimpleValueWithBuilderAndNullableFields value : values) {
      expected.add(value.copy()
          .string("asd")
          .build());
    }
    assertThat(compiledSelectAll.execute()).containsExactlyElementsIn(expected);
  }

  @Test
  public void simpleBulkWithCreator() {
    final CompiledSelect<SimpleValueWithCreatorAndNullableFields, SelectN> compiledSelectAll = Select.from(SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS).compile();

    SimpleValueWithCreatorAndNullableFields.deleteTable().execute();
    final int count = 10;
    final List<SimpleValueWithCreatorAndNullableFields> randomValues = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      randomValues.add(SimpleValueWithCreatorAndNullableFields.newRandom());
    }

    assertThat(SimpleValueWithCreatorAndNullableFields.insert(randomValues).execute()).isTrue();

    List<SimpleValueWithCreatorAndNullableFields> values = compiledSelectAll.execute();
    for (int i = 0, randomValuesSize = randomValues.size(); i < randomValuesSize; i++) {
      SimpleValueWithCreatorAndNullableFields randomValue = randomValues.get(i);
      assertThat(randomValue.equalsWithoutId(values.get(i))).isTrue();
    }

    final List<SimpleValueWithCreatorAndNullableFields> newValues = new ArrayList<>(values.size());
    for (SimpleValueWithCreatorAndNullableFields v : values) {
      newValues.add(SimpleValueWithCreatorAndNullableFields.newRandomWithId(v.id()));
    }
    assertThat(SimpleValueWithCreatorAndNullableFields.update(newValues).execute()).isTrue();
    assertThat(newValues).containsExactlyElementsIn(compiledSelectAll.execute());

    newValues.clear();
    for (SimpleValueWithCreatorAndNullableFields v : values) {
      newValues.add(SimpleValueWithCreatorAndNullableFields.newRandomWithId(v.id()));
    }
    assertThat(SimpleValueWithCreatorAndNullableFields.persist(newValues).execute()).isTrue();
    assertThat(newValues).containsExactlyElementsIn(compiledSelectAll.execute());

    SimpleValueWithCreatorAndNullableFields.deleteTable().execute();
    assertThat(SimpleValueWithCreatorAndNullableFields.persist(newValues).execute()).isTrue();
    values = compiledSelectAll.execute();
    for (int i = 0, newValuesSize = newValues.size(); i < newValuesSize; i++) {
      SimpleValueWithCreatorAndNullableFields value = newValues.get(i);
      assertThat(value.equalsWithoutId(values.get(i))).isTrue();
    }
  }

  @Test
  public void simpleBulkPersistIgnoringNullWithCreator() {
    final CompiledSelect<SimpleValueWithCreatorAndNullableFields, SelectN> compiledSelectAll = Select.from(SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS).compile();
    SimpleValueWithCreatorAndNullableFields.deleteTable().execute();
    final int count = 10;
    final List<SimpleValueWithCreatorAndNullableFields> randomValues = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      randomValues.add(SimpleValueWithCreatorAndNullableFields.newRandom());
    }
    assertThat(SimpleValueWithCreatorAndNullableFields.persist(randomValues).execute()).isTrue();
    final List<SimpleValueWithCreatorAndNullableFields> values = compiledSelectAll.execute();
    for (int i = 0, size = randomValues.size(); i < size; i++) {
      assertThat(randomValues.get(i).equalsWithoutId(values.get(i))).isTrue();
    }

    final List<SimpleValueWithCreatorAndNullableFields> newValues = new ArrayList<>(count);
    for (SimpleValueWithCreatorAndNullableFields value : values) {
      SimpleValueWithCreatorAndNullableFields a = SimpleValueWithCreatorAndNullableFields.createWithId(
          value.id(),
          null,
          null,
          null
      );
      newValues.add(a);
    }
    assertThat(SimpleValueWithCreatorAndNullableFields.persist(newValues).ignoreNullValues().execute()).isTrue();
    assertThat(values).containsExactlyElementsIn(compiledSelectAll.execute());

    newValues.clear();
    for (SimpleValueWithCreatorAndNullableFields value : values) {
      newValues.add(SimpleValueWithCreatorAndNullableFields.createWithId(
          value.id(),
          "asd",
          null,
          null
      ));
    }
    assertThat(SimpleValueWithCreatorAndNullableFields.persist(newValues).ignoreNullValues().execute()).isTrue();
    final List<SimpleValueWithCreatorAndNullableFields> expected = new ArrayList<>(values.size());
    for (SimpleValueWithCreatorAndNullableFields value : values) {
      expected.add(SimpleValueWithCreatorAndNullableFields.createWithId(
          value.id(),
          "asd",
          value.boxedBoolean(),
          value.boxedInteger()
      ));
    }
    assertThat(compiledSelectAll.execute()).containsExactlyElementsIn(expected);
  }

  @Test
  public void simpleAllValuesWithBuilder() {
    final SimpleAllValuesImmutableWithBuilder object = SimpleAllValuesImmutableWithBuilder.builder()
        .string("asd")
        .primitiveShort((short) 3)
        .boxedShort((short) 4)
        .primitiveLong(5555)
        .boxedLong(66666L)
        .primitiveInt(44)
        .boxedInteger(213)
        .primitiveFloat(44.44f)
        .boxedFloat(22.2222f)
        .primitiveDouble(33.666666)
        .boxedDouble(11.2222)
        .primitiveByte((byte) 0x13)
        .boxedByte((byte) 0xF)
        .primitiveByteArray(new byte[]{0x65, 0x16, 0x54})
        .primitiveBoolean(true)
        .boxedBoolean(Boolean.TRUE)
        .calendar(Calendar.getInstance())
        .utilDate(new Date())
        .build();
    testSimpleAllValuesWithBuilder(object, new CreateCallback<SimpleAllValuesImmutableWithBuilder>() {
      @Override
      public SimpleAllValuesImmutableWithBuilder create(long id) {
        return SimpleAllValuesImmutableWithBuilder.builder()
            .id(id)
            .string("asd")
            .primitiveShort((short) 34)
            .boxedShort((short) 43)
            .primitiveLong(55554)
            .boxedLong(666665L)
            .primitiveInt(446)
            .boxedInteger(2133)
            .primitiveFloat(244.44f)
            .boxedFloat(22.22422f)
            .primitiveDouble(313.666666)
            .boxedDouble(11.22242)
            .primitiveByte((byte) 0x23)
            .boxedByte((byte) 0xA)
            .primitiveByteArray(new byte[]{0x65, 0x16, 0x54})
            .primitiveBoolean(false)
            .boxedBoolean(Boolean.FALSE)
            .calendar(Calendar.getInstance())
            .utilDate(new Date(123))
            .build();
      }
    });
  }

  @Test
  public void simpleAllPrimitiveValuesWithBuilder() {
    final SimpleAllValuesImmutableWithBuilder object = SimpleAllValuesImmutableWithBuilder.builder()
        .primitiveShort((short) 3)
        .primitiveLong(5555)
        .primitiveInt(44)
        .primitiveFloat(44.44f)
        .primitiveDouble(33.666666)
        .primitiveByte((byte) 0x13)
        .primitiveByteArray(new byte[]{0x65, 0x16, 0x54})
        .primitiveBoolean(true)
        .build();
    testSimpleAllValuesWithBuilder(object, new CreateCallback<SimpleAllValuesImmutableWithBuilder>() {
      @Override
      public SimpleAllValuesImmutableWithBuilder create(long id) {
        return SimpleAllValuesImmutableWithBuilder.builder()
            .id(id)
            .primitiveShort((short) 34)
            .primitiveLong(55554)
            .primitiveInt(446)
            .primitiveFloat(244.44f)
            .primitiveDouble(313.666666)
            .primitiveByte((byte) 0x23)
            .primitiveByteArray(new byte[]{0x65, 0x16, 0x54})
            .primitiveBoolean(false)
            .build();
      }
    });
  }

  private void testSimpleAllValuesWithBuilder(SimpleAllValuesImmutableWithBuilder object, CreateCallback<SimpleAllValuesImmutableWithBuilder> updatedObjectCreateCallback) {
    SimpleAllValuesImmutableWithBuilder.deleteTable().execute();

    long insertedId = object.insert().execute();
    final SimpleAllValuesImmutableWithBuilder expected = SqliteMagic_SimpleAllValuesImmutableWithBuilder_Dao.setId(object, insertedId);

    final CompiledFirstSelect<SimpleAllValuesImmutableWithBuilder, SelectN> compiledFirstSelect = Select
        .from(SIMPLE_ALL_VALUES_IMMUTABLE_WITH_BUILDER)
        .where(SIMPLE_ALL_VALUES_IMMUTABLE_WITH_BUILDER.ID.is(insertedId))
        .takeFirst();

    assertThat(expected).isEqualTo(compiledFirstSelect.execute());


    SimpleAllValuesImmutableWithBuilder updatedObject = updatedObjectCreateCallback.create(insertedId);
    assertThat(updatedObject.update().execute()).isTrue();
    assertThat(updatedObject).isEqualTo(compiledFirstSelect.execute());

    assertThat(insertedId).isEqualTo(expected.persist().execute());
    assertThat(expected).isEqualTo(compiledFirstSelect.execute());

    SimpleAllValuesImmutableWithBuilder.deleteTable().execute();

    final long persistedId = object.persist().execute();
    assertThat(SqliteMagic_SimpleAllValuesImmutableWithBuilder_Dao.setId(object, persistedId))
        .isEqualTo(Select
            .from(SIMPLE_ALL_VALUES_IMMUTABLE_WITH_BUILDER)
            .where(SIMPLE_ALL_VALUES_IMMUTABLE_WITH_BUILDER.ID.is(persistedId))
            .takeFirst()
            .execute());
  }

  @Test
  public void simpleAllValuesWithCreator() {
    final SimpleAllValuesImmutableWithCreator object = SimpleAllValuesImmutableWithCreator.create(
        "asd",
        (short) 3,
        (short) 4,
        5555,
        66666L,
        44,
        213,
        44.44f,
        22.2222f,
        33.666666,
        11.2222,
        (byte) 0x13,
        (byte) 0xF,
        new byte[]{0x65, 0x16, 0x54},
        true,
        Boolean.TRUE,
        Calendar.getInstance(),
        new Date());
    testSimpleAllValuesWithCreator(object, new CreateCallback<SimpleAllValuesImmutableWithCreator>() {
      @Override
      public SimpleAllValuesImmutableWithCreator create(long id) {
        return SimpleAllValuesImmutableWithCreator.createWithId(
            id,
            "as3d",
            (short) 5,
            (short) 5,
            55535,
            666636L,
            443,
            2133,
            44.434f,
            22.22322f,
            33.6666366,
            11.22232,
            (byte) 0x33,
            (byte) 0x3,
            new byte[]{0x35, 0x36, 0x53},
            false,
            Boolean.FALSE,
            Calendar.getInstance(),
            new Date(123123));
      }
    });
  }

  @Test
  public void simpleAllPrimitiveValuesWithCreator() {
    final SimpleAllValuesImmutableWithCreator object = SimpleAllValuesImmutableWithCreator.create(
        "asd",
        (short) 3,
        null,
        5555,
        null,
        44,
        null,
        44.44f,
        null,
        33.666666,
        null,
        (byte) 0x13,
        null,
        new byte[]{0x65, 0x16, 0x54},
        true,
        null,
        null,
        null);
    testSimpleAllValuesWithCreator(object, new CreateCallback<SimpleAllValuesImmutableWithCreator>() {
      @Override
      public SimpleAllValuesImmutableWithCreator create(long id) {
        return SimpleAllValuesImmutableWithCreator.createWithId(
            id,
            "asd",
            (short) 5,
            null,
            55355,
            null,
            443,
            null,
            44.444f,
            null,
            33.6646666,
            null,
            (byte) 0x33,
            null,
            new byte[]{0x45, 0x36, 0x34},
            true,
            null,
            null,
            null);
      }
    });
  }

  private void testSimpleAllValuesWithCreator(SimpleAllValuesImmutableWithCreator object, CreateCallback<SimpleAllValuesImmutableWithCreator> updatedObjectCreateCallback) {
    SimpleAllValuesImmutableWithCreator.deleteTable().execute();

    long insertedId = object.insert().execute();
    final SimpleAllValuesImmutableWithCreator expected = SqliteMagic_SimpleAllValuesImmutableWithCreator_Dao.setId(object, insertedId);

    final CompiledFirstSelect<SimpleAllValuesImmutableWithCreator, SelectN> compiledFirstSelect = Select
        .from(SIMPLE_ALL_VALUES_IMMUTABLE_WITH_CREATOR)
        .where(SIMPLE_ALL_VALUES_IMMUTABLE_WITH_CREATOR.ID.is(insertedId))
        .takeFirst();

    assertThat(expected).isEqualTo(compiledFirstSelect.execute());


    SimpleAllValuesImmutableWithCreator updatedObject = updatedObjectCreateCallback.create(insertedId);
    assertThat(updatedObject.update().execute()).isTrue();
    assertThat(updatedObject).isEqualTo(compiledFirstSelect.execute());

    assertThat(insertedId).isEqualTo(expected.persist().execute());
    assertThat(expected).isEqualTo(compiledFirstSelect.execute());

    SimpleAllValuesImmutableWithCreator.deleteTable().execute();

    final long persistedId = object.persist().execute();
    assertThat(SqliteMagic_SimpleAllValuesImmutableWithCreator_Dao.setId(object, persistedId))
        .isEqualTo(Select
            .from(SIMPLE_ALL_VALUES_IMMUTABLE_WITH_CREATOR)
            .where(SIMPLE_ALL_VALUES_IMMUTABLE_WITH_CREATOR.ID.is(persistedId))
            .takeFirst()
            .execute());
  }

  @Test(expected = SQLException.class)
  public void simpleBuilderWithMissingNotNullableSelectionFails() {
    SimpleValueWithBuilder.deleteTable().execute();
    final SimpleValueWithBuilder object = SimpleValueWithBuilder.newRandom().build();

    assertThat(object.insert().execute()).isNotEqualTo(-1);
    final SimpleValueWithBuilder selectedObject = Select
        .columns(SIMPLE_VALUE_WITH_BUILDER.ID)
        .from(SIMPLE_VALUE_WITH_BUILDER)
        .takeFirst()
        .execute();
  }

  @Test(expected = SQLException.class)
  public void simpleCreatorWithMissingNotNullableSelectionFails() {
    SimpleValueWithCreator.deleteTable().execute();
    final SimpleValueWithCreator object = SimpleValueWithCreator.newRandom();

    assertThat(object.insert().execute()).isNotEqualTo(-1);
    final SimpleValueWithCreator selectedObject = Select
        .columns(SIMPLE_VALUE_WITH_CREATOR.ID)
        .from(SIMPLE_VALUE_WITH_CREATOR)
        .takeFirst()
        .execute();
  }

  @Test
  public void simpleBuilderWithSelection() {
    SimpleValueWithBuilderAndNullableFields.deleteTable().execute();
    SimpleValueWithBuilderAndNullableFields object = SimpleValueWithBuilderAndNullableFields.builder()
        .string("asdasd")
        .boxedBoolean(Boolean.TRUE)
        .boxedInteger(123123)
        .build();
    final long insertId = object.insert().execute();
    assertThat(insertId).isNotEqualTo(-1);

    assertThat(SimpleValueWithBuilderAndNullableFields.builder()
        .string("asdasd")
        .boxedInteger(123123)
        .build())
        .isEqualTo(Select
            .columns(SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS.BOXED_INTEGER,
                SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS.STRING)
            .from(SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS)
            .where(SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS.ID.is(insertId))
            .takeFirst()
            .execute());
  }

  @Test
  public void simpleCreatorWithSelection() {
    SimpleValueWithCreatorAndNullableFields.deleteTable().execute();
    SimpleValueWithCreatorAndNullableFields object = SimpleValueWithCreatorAndNullableFields.create(
        "asdasd",
        Boolean.TRUE,
        123123);
    final long insertId = object.insert().execute();
    assertThat(insertId).isNotEqualTo(-1);

    assertThat(SimpleValueWithCreatorAndNullableFields.create(
        "asdasd",
        null,
        123123))
        .isEqualTo(Select
            .columns(
                SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS.BOXED_INTEGER,
                SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS.STRING)
            .from(SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS)
            .where(SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS.ID.is(insertId))
            .takeFirst()
            .execute());
  }

  @Test
  public void complexWithBuilder() {
    BuilderMagazine.deleteTable().execute();

    final BuilderMagazine object = BuilderMagazine.newRandom().build();
    final long insertId = object.insert().execute();
    assertThat(insertId).isNotEqualTo(-1);

    final CompiledFirstSelect<BuilderMagazine, SelectN> compiledFirstSelect = Select
        .from(BUILDER_MAGAZINE)
        .where(BUILDER_MAGAZINE.ID.is(insertId))
        .queryDeep()
        .takeFirst();

    BuilderMagazine retrievedObject = compiledFirstSelect.execute();
    assertThat(insertId).isEqualTo(retrievedObject.id());
    assertThat(object.equalsWithoutId(retrievedObject)).isTrue();

    BuilderMagazine expected = retrievedObject.copy()
        .name(Utils.randomTableName())
        .build();
    assertThat(expected.update().execute()).isTrue();
    assertThat(expected.equalsWithoutId(compiledFirstSelect.execute())).isTrue();

    expected = retrievedObject.copy()
        .name(Utils.randomTableName())
        .build();
    long persistId = expected.persist().execute();
    assertThat(persistId).isNotEqualTo(-1);
    assertThat(insertId).isEqualTo(persistId);
    assertThat(expected.equalsWithoutId(compiledFirstSelect.execute())).isTrue();

    BuilderMagazine.deleteTable().execute();
    persistId = expected.persist().execute();
    assertThat(persistId).isNotEqualTo(-1);
    assertThat(insertId).isNotEqualTo(persistId);
    retrievedObject = Select
        .from(BUILDER_MAGAZINE)
        .where(BUILDER_MAGAZINE.ID.is(persistId))
        .queryDeep()
        .takeFirst()
        .execute();
    assertThat(persistId).isEqualTo(retrievedObject.id());
    assertThat(expected.equalsWithoutId(retrievedObject)).isTrue();
  }

  @Test
  public void complexWithCreator() {
    CreatorMagazine.deleteTable().execute();

    final CreatorMagazine object = CreatorMagazine.newRandom();
    final long insertId = object.insert().execute();
    assertThat(insertId).isNotEqualTo(-1);

    final CompiledFirstSelect<CreatorMagazine, SelectN> compiledFirstSelect = Select
        .from(CREATOR_MAGAZINE)
        .where(CREATOR_MAGAZINE.ID.is(insertId))
        .queryDeep()
        .takeFirst();

    CreatorMagazine retrievedObject = compiledFirstSelect.execute();
    assertThat(insertId).isEqualTo(retrievedObject.id());
    assertThat(object.equalsWithoutId(retrievedObject)).isTrue();

    CreatorMagazine expected = CreatorMagazine.create(
        retrievedObject.id(),
        Utils.randomTableName(),
        retrievedObject.author(),
        retrievedObject.simpleValueWithBuilder(),
        retrievedObject.simpleValueWithCreator()
    );
    assertThat(expected.update().execute()).isTrue();
    assertThat(expected.equalsWithoutId(compiledFirstSelect.execute())).isTrue();

    expected = CreatorMagazine.create(
        retrievedObject.id(),
        Utils.randomTableName(),
        retrievedObject.author(),
        retrievedObject.simpleValueWithBuilder(),
        retrievedObject.simpleValueWithCreator()
    );
    long persistId = expected.persist().execute();
    assertThat(persistId).isNotEqualTo(-1);
    assertThat(insertId).isEqualTo(persistId);
    assertThat(expected.equalsWithoutId(compiledFirstSelect.execute())).isTrue();

    CreatorMagazine.deleteTable().execute();
    persistId = expected.persist().execute();
    assertThat(persistId).isNotEqualTo(-1);
    assertThat(insertId).isNotEqualTo(persistId);
    retrievedObject = Select
        .from(CREATOR_MAGAZINE)
        .where(CREATOR_MAGAZINE.ID.is(persistId))
        .queryDeep()
        .takeFirst()
        .execute();
    assertThat(persistId).isEqualTo(retrievedObject.id());
    assertThat(expected.equalsWithoutId(retrievedObject)).isTrue();
  }

  @Test
  public void complexBulkWithBuilder() {
    final CompiledSelect<BuilderMagazine, SelectN> compiledSelectAll = Select
        .from(BUILDER_MAGAZINE)
        .queryDeep()
        .compile();

    BuilderMagazine.deleteTable().execute();
    final int count = 10;
    final List<BuilderMagazine> randomValues = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      randomValues.add(BuilderMagazine.newRandom().build());
    }

    assertThat(BuilderMagazine.insert(randomValues).execute()).isTrue();
    List<BuilderMagazine> values = compiledSelectAll.execute();
    for (int i = 0; i < values.size(); i++) {
      assertThat(values.get(i).equalsWithoutId(randomValues.get(i))).isTrue();
    }

    final ArrayList<BuilderMagazine> newValues = new ArrayList<>(count);
    for (BuilderMagazine m : values) {
      Author author = Author.newRandom();
      author.id = m.author().id;
      SimpleValueWithBuilder sb = SimpleValueWithBuilder.newRandom()
          .id(m.simpleValueWithBuilder().id())
          .build();
      SimpleValueWithCreator sc = SimpleValueWithCreator.newRandom(m.simpleValueWithCreator().id());
      newValues.add(BuilderMagazine.builder()
          .id(m.id())
          .name(Utils.randomTableName())
          .author(author)
          .simpleValueWithBuilder(sb)
          .simpleValueWithCreator(sc)
          .build());
    }
    assertThat(BuilderMagazine.update(newValues).execute()).isTrue();
    assertThat(newValues).containsExactlyElementsIn(compiledSelectAll.execute());

    newValues.clear();
    for (BuilderMagazine m : values) {
      Author author = Author.newRandom();
      author.id = m.author().id;
      SimpleValueWithBuilder sb = SimpleValueWithBuilder.newRandom()
          .id(m.simpleValueWithBuilder().id())
          .build();
      SimpleValueWithCreator sc = SimpleValueWithCreator.newRandom(m.simpleValueWithCreator().id());
      newValues.add(BuilderMagazine.builder()
          .id(m.id())
          .name(Utils.randomTableName())
          .author(author)
          .simpleValueWithBuilder(sb)
          .simpleValueWithCreator(sc)
          .build());
    }
    assertThat(BuilderMagazine.persist(newValues).execute()).isTrue();
    assertThat(newValues).containsExactlyElementsIn(compiledSelectAll.execute());

    BuilderMagazine.deleteTable().execute();
    assertThat(BuilderMagazine.persist(newValues).execute()).isTrue();
    values = compiledSelectAll.execute();
    for (int i = 0; i < values.size(); i++) {
      assertThat(values.get(i).equalsWithoutId(newValues.get(i))).isTrue();
    }
  }

  @Test
  public void complexBulkPersistIgnoringNullWithBuilder() {
    final CompiledSelect<BuilderMagazine, SelectN> compiledSelectAll = Select
        .from(BUILDER_MAGAZINE)
        .queryDeep()
        .compile();
    BuilderMagazine.deleteTable().execute();
    final int count = 10;
    final List<BuilderMagazine> randomValues = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      randomValues.add(BuilderMagazine.newRandom().build());
    }
    assertThat(BuilderMagazine.persist(randomValues).execute()).isTrue();
    final List<BuilderMagazine> values = compiledSelectAll.execute();
    for (int i = 0; i < values.size(); i++) {
      assertThat(values.get(i).equalsWithoutId(randomValues.get(i))).isTrue();
    }

    final List<BuilderMagazine> newMagazines = new ArrayList<>(count);
    for (BuilderMagazine value : values) {
      BuilderMagazine m = BuilderMagazine.builder()
          .id(value.id())
          .build();
      newMagazines.add(m);
    }
    assertThat(BuilderMagazine.persist(newMagazines).ignoreNullValues().execute()).isTrue();
    assertThat(values).containsExactlyElementsIn(compiledSelectAll.execute());

    newMagazines.clear();
    for (BuilderMagazine value : values) {
      final Author a = new Author();
      a.id = value.author().id;
      a.primitiveBoolean = value.author().primitiveBoolean;
      BuilderMagazine m = BuilderMagazine.builder()
          .id(value.id())
          .name("asd")
          .author(a)
          .build();
      newMagazines.add(m);
    }
    assertThat(BuilderMagazine.persist(newMagazines).ignoreNullValues().execute()).isTrue();
    final ArrayList<BuilderMagazine> expected = new ArrayList<>(count);
    for (BuilderMagazine value : values) {
      expected.add(value.copy()
          .name("asd")
          .build());
    }
    assertThat(compiledSelectAll.execute()).containsExactlyElementsIn(expected);

    newMagazines.clear();
    for (BuilderMagazine value : values) {
      final Author a = new Author();
      a.id = value.author().id;
      a.primitiveBoolean = value.author().primitiveBoolean;
      a.name = "dsa";
      BuilderMagazine m = BuilderMagazine.builder()
          .id(value.id())
          .name("dsa")
          .author(a)
          .build();
      newMagazines.add(m);
    }
    assertThat(BuilderMagazine.persist(newMagazines).ignoreNullValues().execute()).isTrue();
    expected.clear();
    for (BuilderMagazine value : values) {
      value.author().name = "dsa";
      expected.add(value.copy()
          .name("dsa")
          .build());
    }
    assertThat(compiledSelectAll.execute()).containsExactlyElementsIn(expected);
  }

  @Test
  public void complexBulkWithCreator() {
    final CompiledSelect<CreatorMagazine, SelectN> compiledSelectAll = Select
        .from(CREATOR_MAGAZINE)
        .queryDeep()
        .compile();

    CreatorMagazine.deleteTable().execute();
    final int count = 10;
    final List<CreatorMagazine> randomValues = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      randomValues.add(CreatorMagazine.newRandom());
    }

    assertThat(CreatorMagazine.insert(randomValues).execute()).isTrue();
    List<CreatorMagazine> values = compiledSelectAll.execute();
    for (int i = 0; i < values.size(); i++) {
      assertThat(values.get(i).equalsWithoutId(randomValues.get(i))).isTrue();
    }

    final ArrayList<CreatorMagazine> newValues = new ArrayList<>(count);
    for (CreatorMagazine m : values) {
      Author author = Author.newRandom();
      author.id = m.author().id;
      SimpleValueWithBuilder sb = SimpleValueWithBuilder.newRandom()
          .id(m.simpleValueWithBuilder().id())
          .build();
      SimpleValueWithCreator sc = SimpleValueWithCreator.newRandom(m.simpleValueWithCreator().id());
      newValues.add(CreatorMagazine.create(
          m.id(),
          Utils.randomTableName(),
          author,
          sb,
          sc));
    }
    assertThat(CreatorMagazine.update(newValues).execute()).isTrue();
    assertThat(newValues).containsExactlyElementsIn(compiledSelectAll.execute());

    newValues.clear();
    for (CreatorMagazine m : values) {
      Author author = Author.newRandom();
      author.id = m.author().id;
      SimpleValueWithBuilder sb = SimpleValueWithBuilder.newRandom()
          .id(m.simpleValueWithBuilder().id())
          .build();
      SimpleValueWithCreator sc = SimpleValueWithCreator.newRandom(m.simpleValueWithCreator().id());
      newValues.add(CreatorMagazine.create(
          m.id(),
          Utils.randomTableName(),
          author,
          sb,
          sc));
    }
    assertThat(CreatorMagazine.persist(newValues).execute()).isTrue();
    assertThat(newValues).containsExactlyElementsIn(compiledSelectAll.execute());

    CreatorMagazine.deleteTable().execute();
    assertThat(CreatorMagazine.persist(newValues).execute()).isTrue();
    values = compiledSelectAll.execute();
    for (int i = 0; i < values.size(); i++) {
      assertThat(values.get(i).equalsWithoutId(newValues.get(i))).isTrue();
    }
  }

  @Test
  public void complexBulkPersistIgnoringNullWithCreator() {
    final CompiledSelect<CreatorMagazine, SelectN> compiledSelectAll = Select
        .from(CREATOR_MAGAZINE)
        .queryDeep()
        .compile();
    CreatorMagazine.deleteTable().execute();
    final int count = 10;
    final List<CreatorMagazine> randomValues = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      randomValues.add(CreatorMagazine.newRandom());
    }
    assertThat(CreatorMagazine.persist(randomValues).execute()).isTrue();
    final List<CreatorMagazine> values = compiledSelectAll.execute();
    for (int i = 0; i < values.size(); i++) {
      assertThat(values.get(i).equalsWithoutId(randomValues.get(i))).isTrue();
    }

    final List<CreatorMagazine> newMagazines = new ArrayList<>(count);
    for (CreatorMagazine value : values) {
      CreatorMagazine m = CreatorMagazine.create(
          value.id(),
          null,
          null,
          null,
          null);
      newMagazines.add(m);
    }
    assertThat(CreatorMagazine.persist(newMagazines).ignoreNullValues().execute()).isTrue();
    assertThat(values).containsExactlyElementsIn(compiledSelectAll.execute());

    newMagazines.clear();
    for (CreatorMagazine value : values) {
      final Author a = new Author();
      a.id = value.author().id;
      a.primitiveBoolean = value.author().primitiveBoolean;
      CreatorMagazine m = CreatorMagazine.create(
          value.id(),
          "asd",
          a,
          null,
          null);
      newMagazines.add(m);
    }
    assertThat(CreatorMagazine.persist(newMagazines).ignoreNullValues().execute()).isTrue();
    final ArrayList<CreatorMagazine> expected = new ArrayList<>(count);
    for (CreatorMagazine value : values) {
      expected.add(CreatorMagazine.create(
          value.id(),
          "asd",
          value.author(),
          value.simpleValueWithBuilder(),
          value.simpleValueWithCreator()));
    }
    assertThat(compiledSelectAll.execute()).containsExactlyElementsIn(expected);

    newMagazines.clear();
    for (CreatorMagazine value : values) {
      final Author a = new Author();
      a.id = value.author().id;
      a.primitiveBoolean = value.author().primitiveBoolean;
      a.name = "dsa";
      CreatorMagazine m = CreatorMagazine.create(
          value.id(),
          "dsa",
          a,
          null,
          null);
      newMagazines.add(m);
    }
    assertThat(CreatorMagazine.persist(newMagazines).ignoreNullValues().execute()).isTrue();
    expected.clear();
    for (CreatorMagazine value : values) {
      value.author().name = "dsa";
      expected.add(CreatorMagazine.create(
          value.id(),
          "dsa",
          value.author(),
          value.simpleValueWithBuilder(),
          value.simpleValueWithCreator()));
    }
    assertThat(compiledSelectAll.execute()).containsExactlyElementsIn(expected);
  }

  @Test
  public void complexPersistIgnoringNullWithBuilder() {
    BuilderMagazine.deleteTable().execute();
    BuilderMagazine random = BuilderMagazine.newRandom().build();
    final long id = random.persist().execute();
    assertThat(id).isNotEqualTo(-1);
    final CompiledFirstSelect<BuilderMagazine, SelectN> firstSelect = Select
        .from(BUILDER_MAGAZINE)
        .where(BUILDER_MAGAZINE.ID.is(id))
        .queryDeep()
        .takeFirst();
    BuilderMagazine magazine = firstSelect.execute();
    assertThat(magazine.equalsWithoutId(random)).isTrue();

    BuilderMagazine newMagazine = BuilderMagazine.builder()
        .id(magazine.id())
        .build();
    assertThat(newMagazine.persist().ignoreNullValues().execute()).isEqualTo(id);
    assertThat(firstSelect.execute())
        .isEqualTo(magazine);

    Author author = new Author();
    author.id = magazine.author().id;
    author.primitiveBoolean = magazine.author().primitiveBoolean;
    newMagazine = BuilderMagazine.builder()
        .id(magazine.id())
        .author(author)
        .build();
    assertThat(newMagazine.persist().ignoreNullValues().execute()).isEqualTo(id);
    assertThat(firstSelect.execute())
        .isEqualTo(magazine);

    author.name = "asd";
    newMagazine = BuilderMagazine.builder()
        .id(magazine.id())
        .name("asd")
        .author(author)
        .build();
    assertThat(newMagazine.persist().ignoreNullValues().execute()).isEqualTo(id);
    author = magazine.author();
    author.name = "asd";
    assertThat(firstSelect.execute())
        .isEqualTo(magazine.copy()
            .name("asd")
            .author(author)
            .build());
  }

  @Test
  public void complexPersistIgnoringNullWithCreator() {
    CreatorMagazine.deleteTable().execute();
    CreatorMagazine random = CreatorMagazine.newRandom();
    final long id = random.persist().execute();
    assertThat(id).isNotEqualTo(-1);
    final CompiledFirstSelect<CreatorMagazine, SelectN> firstSelect = Select
        .from(CREATOR_MAGAZINE)
        .where(CREATOR_MAGAZINE.ID.is(id))
        .queryDeep()
        .takeFirst();
    CreatorMagazine magazine = firstSelect.execute();
    assertThat(magazine.equalsWithoutId(random)).isTrue();

    CreatorMagazine newMagazine = CreatorMagazine.create(
        magazine.id(),
        null,
        null,
        null,
        null
    );
    assertThat(newMagazine.persist().ignoreNullValues().execute()).isEqualTo(id);
    assertThat(firstSelect.execute())
        .isEqualTo(magazine);

    Author author = new Author();
    author.id = magazine.author().id;
    author.primitiveBoolean = magazine.author().primitiveBoolean;
    newMagazine = CreatorMagazine.create(
        magazine.id(),
        null,
        author,
        null,
        null
    );
    assertThat(newMagazine.persist().ignoreNullValues().execute()).isEqualTo(id);
    assertThat(firstSelect.execute())
        .isEqualTo(magazine);

    author.name = "asd";
    newMagazine = CreatorMagazine.create(
        magazine.id(),
        "asd",
        author,
        null,
        null
    );
    assertThat(newMagazine.persist().ignoreNullValues().execute()).isEqualTo(id);
    author = magazine.author();
    author.name = "asd";
    assertThat(firstSelect.execute())
        .isEqualTo(magazine.create(
            magazine.id(),
            "asd",
            author,
            magazine.simpleValueWithBuilder(),
            magazine.simpleValueWithCreator()
        ));
  }

  @Test
  public void complexAllValuesWithBuilder() {
    ComplexValueWithBuilder.deleteTable().execute();

    ComplexValueWithBuilder object = ComplexValueWithBuilder.newRandom().build();
    final long insertId = object.insert().execute();
    assertThat(insertId).isNotEqualTo(-1);

    final CompiledFirstSelect<ComplexValueWithBuilder, SelectN> compiledFirstSelect = Select
        .from(COMPLEX_VALUE_WITH_BUILDER)
        .where(COMPLEX_VALUE_WITH_BUILDER.ID.is(insertId))
        .queryDeep()
        .takeFirst();

    ComplexValueWithBuilder retrievedObject = compiledFirstSelect.execute();
    assertThat(insertId).isEqualTo(retrievedObject.id());
    assertThat(object.equalsWithoutNotPersistedImmutableObjects(retrievedObject)).isTrue();

    Author author = retrievedObject.author();
    author.name = Utils.randomTableName();
    ComplexObjectWithSameLeafs complexObjectWithSameLeafs = retrievedObject.complexObjectWithSameLeafs();
    complexObjectWithSameLeafs.name = Utils.randomTableName();
    ComplexValueWithBuilder expected = retrievedObject.copy()
        .string(Utils.randomTableName())
        .author(author)
        .complexObjectWithSameLeafs(complexObjectWithSameLeafs)
        .builderSimpleValue(retrievedObject.builderSimpleValue().copy().stringValue(Utils.randomTableName()).build())
        .build();

    assertThat(expected.update().execute()).isTrue();
    assertThat(expected.equalsWithoutId(compiledFirstSelect.execute())).isTrue();

    author = retrievedObject.author();
    author.name = Utils.randomTableName();
    complexObjectWithSameLeafs = retrievedObject.complexObjectWithSameLeafs();
    complexObjectWithSameLeafs.name = Utils.randomTableName();
    expected = retrievedObject.copy()
        .string(Utils.randomTableName())
        .author(author)
        .complexObjectWithSameLeafs(complexObjectWithSameLeafs)
        .builderSimpleValue(retrievedObject.builderSimpleValue().copy().stringValue(Utils.randomTableName()).build())
        .build();

    long persistId = expected.persist().execute();
    assertThat(persistId).isNotEqualTo(-1);
    assertThat(persistId).isEqualTo(insertId);
    assertThat(expected.equalsWithoutId(compiledFirstSelect.execute())).isTrue();

    ComplexValueWithBuilder.deleteTable().execute();
    object = ComplexValueWithBuilder.newRandom().build();
    persistId = object.persist().execute();
    assertThat(persistId).isNotEqualTo(-1);
    assertThat(persistId).isNotEqualTo(insertId);
    retrievedObject = Select
        .from(COMPLEX_VALUE_WITH_BUILDER)
        .where(COMPLEX_VALUE_WITH_BUILDER.ID.is(persistId))
        .queryDeep()
        .takeFirst()
        .execute();
    assertThat(object.equalsWithoutNotPersistedImmutableObjects(retrievedObject)).isTrue();
  }

  @Test
  public void complexAllValuesWithCreator() {
    ComplexValueWithCreator.deleteTable().execute();

    ComplexValueWithCreator object = ComplexValueWithCreator.newRandom();
    final long insertId = object.insert().execute();
    assertThat(insertId).isNotEqualTo(-1);

    final CompiledFirstSelect<ComplexValueWithCreator, SelectN> compiledFirstSelect = Select
        .from(COMPLEX_VALUE_WITH_CREATOR)
        .where(COMPLEX_VALUE_WITH_CREATOR.ID.is(insertId))
        .queryDeep()
        .takeFirst();

    ComplexValueWithCreator retrievedObject = compiledFirstSelect.execute();
    assertThat(insertId).isEqualTo(retrievedObject.id());
    assertThat(object.equalsWithoutNotPersistedImmutableObjects(retrievedObject)).isTrue();

    Author author = retrievedObject.author();
    author.name = Utils.randomTableName();
    ComplexObjectWithSameLeafs complexObjectWithSameLeafs = retrievedObject.complexObjectWithSameLeafs();
    complexObjectWithSameLeafs.name = Utils.randomTableName();
    ComplexValueWithCreator expected = ComplexValueWithCreator.create(
        retrievedObject.id(),
        Utils.randomTableName(),
        null,
        author,
        retrievedObject.notPersistedAuthor(),
        null,
        complexObjectWithSameLeafs,
        retrievedObject.notPersistedComplexObjectWithSameLeafs(),
        retrievedObject.builderSimpleValue().copy().stringValue(Utils.randomTableName()).build(),
        retrievedObject.notPersistedBuilderSimpleValue(),
        null,
        retrievedObject.creatorSimpleValue(),
        retrievedObject.notPersistedCreatorSimpleValue(),
        null
    );

    assertThat(expected.update().execute()).isTrue();
    assertThat(expected.equalsWithoutId(compiledFirstSelect.execute())).isTrue();

    author = retrievedObject.author();
    author.name = Utils.randomTableName();
    complexObjectWithSameLeafs = retrievedObject.complexObjectWithSameLeafs();
    complexObjectWithSameLeafs.name = Utils.randomTableName();
    expected = ComplexValueWithCreator.create(
        retrievedObject.id(),
        Utils.randomTableName(),
        null,
        author,
        retrievedObject.notPersistedAuthor(),
        null,
        complexObjectWithSameLeafs,
        retrievedObject.notPersistedComplexObjectWithSameLeafs(),
        retrievedObject.builderSimpleValue().copy().stringValue(Utils.randomTableName()).build(),
        retrievedObject.notPersistedBuilderSimpleValue(),
        null,
        retrievedObject.creatorSimpleValue(),
        retrievedObject.notPersistedCreatorSimpleValue(),
        null
    );

    long persistId = expected.persist().execute();
    assertThat(persistId).isNotEqualTo(-1);
    assertThat(persistId).isEqualTo(insertId);
    assertThat(expected.equalsWithoutId(compiledFirstSelect.execute())).isTrue();

    ComplexValueWithCreator.deleteTable().execute();
    object = ComplexValueWithCreator.newRandom();
    persistId = object.persist().execute();
    assertThat(persistId).isNotEqualTo(-1);
    assertThat(persistId).isNotEqualTo(insertId);
    retrievedObject = Select
        .from(COMPLEX_VALUE_WITH_CREATOR)
        .where(COMPLEX_VALUE_WITH_CREATOR.ID.is(persistId))
        .queryDeep()
        .takeFirst()
        .execute();
    assertThat(object.equalsWithoutNotPersistedImmutableObjects(retrievedObject)).isTrue();
  }

  @Test
  public void ignoreNullPersistWithBuilder() {
    ComplexValueWithBuilder object = ComplexValueWithBuilder.newRandom().build();
    final long persistId = object.persist().execute();
    assertThat(persistId).isNotEqualTo(-1);
    final CompiledFirstSelect<ComplexValueWithBuilder, SelectN> compiledFirstSelect = Select
        .from(COMPLEX_VALUE_WITH_BUILDER)
        .where(COMPLEX_VALUE_WITH_BUILDER.ID.is(persistId))
        .queryDeep()
        .takeFirst();
    ComplexValueWithBuilder retrievedObject = compiledFirstSelect.execute();

    assertThat(persistId).isEqualTo(retrievedObject.id());
    assertThat(object.equalsWithoutNotPersistedImmutableObjects(retrievedObject)).isTrue();

    object = retrievedObject.copy()
        .nullableString(null)
        .nullableAuthor(null)
        .nullableBuilderSimpleValue(null)
        .nullableCreatorSimpleValue(null)
        .build();
    final long persistUpdateId = object.persist().ignoreNullValues().execute();
    assertThat(persistUpdateId).isNotEqualTo(-1);
    assertThat(persistUpdateId).isEqualTo(persistId);
    assertThat(retrievedObject).isEqualTo(compiledFirstSelect.execute());
  }

  @Test
  public void ignoreNullPersistWithCreator() {
    ComplexValueWithCreator object = ComplexValueWithCreator.newRandom();
    final long persistId = object.persist().execute();
    assertThat(persistId).isNotEqualTo(-1);
    final CompiledFirstSelect<ComplexValueWithCreator, SelectN> compiledFirstSelect = Select
        .from(COMPLEX_VALUE_WITH_CREATOR)
        .where(COMPLEX_VALUE_WITH_CREATOR.ID.is(persistId))
        .queryDeep()
        .takeFirst();
    ComplexValueWithCreator retrievedObject = compiledFirstSelect.execute();

    assertThat(persistId).isEqualTo(retrievedObject.id());
    assertThat(object.equalsWithoutNotPersistedImmutableObjects(retrievedObject)).isTrue();

    object = ComplexValueWithCreator.create(
        retrievedObject.id(),
        retrievedObject.string(),
        null,
        retrievedObject.author(),
        retrievedObject.notPersistedAuthor(),
        null,
        retrievedObject.complexObjectWithSameLeafs(),
        retrievedObject.notPersistedComplexObjectWithSameLeafs(),
        retrievedObject.builderSimpleValue(),
        retrievedObject.notPersistedBuilderSimpleValue(),
        null,
        retrievedObject.creatorSimpleValue(),
        retrievedObject.notPersistedCreatorSimpleValue(),
        null
    );
    final long persistUpdateId = object.persist().ignoreNullValues().execute();
    assertThat(persistUpdateId).isNotEqualTo(-1);
    assertThat(persistUpdateId).isEqualTo(persistId);
    assertThat(retrievedObject).isEqualTo(compiledFirstSelect.execute());
  }

  @Test
  public void complexBuilderWithSelection() {
    final ComplexValueWithBuilder object = ComplexValueWithBuilder.newRandom().build();
    final long id = object.persist().execute();
    assertThat(id).isNotEqualTo(-1);

    final Expr idIsInsertedId = COMPLEX_VALUE_WITH_BUILDER.ID.is(id);

    final ComplexValueWithBuilder retrievedObject = Select
        .from(COMPLEX_VALUE_WITH_BUILDER)
        .where(idIsInsertedId)
        .queryDeep()
        .takeFirst()
        .execute();
    assertThat(object.equalsWithoutNotPersistedImmutableObjects(retrievedObject)).isTrue();

    Author author = new Author();
    author.id = retrievedObject.author().id;
    ComplexObjectWithSameLeafs complexObjectWithSameLeafs = new ComplexObjectWithSameLeafs();
    complexObjectWithSameLeafs.id = retrievedObject.complexObjectWithSameLeafs().id;
    ComplexValueWithBuilder expected = retrievedObject.copy()
        .author(author)
        .complexObjectWithSameLeafs(complexObjectWithSameLeafs)
        .nullableString(null)
        .nullableAuthor(null)
        .nullableBuilderSimpleValue(null)
        .nullableCreatorSimpleValue(null)
        .build();
    assertThat(expected).isEqualTo(Select
        .columns(
            COMPLEX_VALUE_WITH_BUILDER.STRING,
            COMPLEX_VALUE_WITH_BUILDER.ID,
            COMPLEX_VALUE_WITH_BUILDER.AUTHOR,
            COMPLEX_VALUE_WITH_BUILDER.COMPLEX_OBJECT_WITH_SAME_LEAFS,
            COMPLEX_VALUE_WITH_BUILDER.NOT_PERSISTED_AUTHOR,
            COMPLEX_VALUE_WITH_BUILDER.NOT_PERSISTED_COMPLEX_OBJECT_WITH_SAME_LEAFS,
            COMPLEX_VALUE_WITH_BUILDER.NOT_PERSISTED_CREATOR_SIMPLE_VALUE,
            COMPLEX_VALUE_WITH_BUILDER.NOT_PERSISTED_BUILDER_SIMPLE_VALUE,
            SIMPLE_VALUE_WITH_BUILDER.all(),
            SIMPLE_VALUE_WITH_CREATOR.all()
        )
        .from(COMPLEX_VALUE_WITH_BUILDER)
        .where(idIsInsertedId)
        .takeFirst()
        .execute()
    );

    expected = retrievedObject.copy()
        .nullableString(null)
        .nullableBuilderSimpleValue(null)
        .nullableCreatorSimpleValue(null)
        .build();
    assertThat(expected).isEqualTo(Select
        .columns(
            COMPLEX_VALUE_WITH_BUILDER.STRING,
            COMPLEX_VALUE_WITH_BUILDER.ID,
            COMPLEX_VALUE_WITH_BUILDER.NOT_PERSISTED_AUTHOR,
            COMPLEX_VALUE_WITH_BUILDER.NOT_PERSISTED_COMPLEX_OBJECT_WITH_SAME_LEAFS,
            COMPLEX_VALUE_WITH_BUILDER.NOT_PERSISTED_CREATOR_SIMPLE_VALUE,
            COMPLEX_VALUE_WITH_BUILDER.NOT_PERSISTED_BUILDER_SIMPLE_VALUE,
            AUTHOR.all(),
            COMPLEX_OBJECT_WITH_SAME_LEAFS.all(),
            SIMPLE_VALUE_WITH_BUILDER.all(),
            SIMPLE_VALUE_WITH_CREATOR.all(),
            BOOK.all(),
            MAGAZINE.all()
        )
        .from(COMPLEX_VALUE_WITH_BUILDER)
        .where(idIsInsertedId)
        .queryDeep()
        .takeFirst()
        .execute()
    );

    complexObjectWithSameLeafs = new ComplexObjectWithSameLeafs();
    final ComplexObjectWithSameLeafs c = retrievedObject.complexObjectWithSameLeafs();
    complexObjectWithSameLeafs.name = c.name;
    complexObjectWithSameLeafs.id = c.id;
    complexObjectWithSameLeafs.simpleValueWithBuilder = c.simpleValueWithBuilder;
    complexObjectWithSameLeafs.simpleValueWithBuilderDuplicate = c.simpleValueWithBuilderDuplicate;
    expected = retrievedObject.copy()
        .complexObjectWithSameLeafs(complexObjectWithSameLeafs)
        .nullableString(null)
        .nullableAuthor(null)
        .nullableBuilderSimpleValue(null)
        .nullableCreatorSimpleValue(null)
        .build();
    final AuthorTable auth = AUTHOR.as("auth");
    assertThat(expected).isEqualTo(Select
        .columns(
            COMPLEX_VALUE_WITH_BUILDER.STRING,
            COMPLEX_VALUE_WITH_BUILDER.ID,
            COMPLEX_VALUE_WITH_BUILDER.NOT_PERSISTED_AUTHOR,
            COMPLEX_VALUE_WITH_BUILDER.NOT_PERSISTED_COMPLEX_OBJECT_WITH_SAME_LEAFS,
            COMPLEX_VALUE_WITH_BUILDER.NOT_PERSISTED_CREATOR_SIMPLE_VALUE,
            COMPLEX_VALUE_WITH_BUILDER.NOT_PERSISTED_BUILDER_SIMPLE_VALUE,
            auth.all(),
            COMPLEX_OBJECT_WITH_SAME_LEAFS.all(),
            SIMPLE_VALUE_WITH_BUILDER.all(),
            SIMPLE_VALUE_WITH_CREATOR.all()
        )
        .from(COMPLEX_VALUE_WITH_BUILDER)
        .leftJoin(auth.on(COMPLEX_VALUE_WITH_BUILDER.AUTHOR.is(auth.ID)))
        .where(idIsInsertedId)
        .queryDeep()
        .takeFirst()
        .execute()
    );
  }

  @Test
  public void complexCreatorWithSelection() {
    final ComplexValueWithCreator object = ComplexValueWithCreator.newRandom();
    final long id = object.persist().execute();
    assertThat(id).isNotEqualTo(-1);

    final Expr idIsInsertedId = COMPLEX_VALUE_WITH_CREATOR.ID.is(id);

    final ComplexValueWithCreator retrievedObject = Select
        .from(COMPLEX_VALUE_WITH_CREATOR)
        .where(idIsInsertedId)
        .queryDeep()
        .takeFirst()
        .execute();
    assertThat(object.equalsWithoutNotPersistedImmutableObjects(retrievedObject)).isTrue();

    Author author = new Author();
    author.id = retrievedObject.author().id;
    ComplexObjectWithSameLeafs complexObjectWithSameLeafs = new ComplexObjectWithSameLeafs();
    complexObjectWithSameLeafs.id = retrievedObject.complexObjectWithSameLeafs().id;
    ComplexValueWithCreator expected = ComplexValueWithCreator.create(
        retrievedObject.id(),
        retrievedObject.string(),
        null,
        author,
        retrievedObject.notPersistedAuthor(),
        null,
        complexObjectWithSameLeafs,
        retrievedObject.notPersistedComplexObjectWithSameLeafs(),
        retrievedObject.builderSimpleValue(),
        retrievedObject.notPersistedBuilderSimpleValue(),
        null,
        retrievedObject.creatorSimpleValue(),
        retrievedObject.notPersistedCreatorSimpleValue(),
        null);
    assertThat(expected).isEqualTo(Select
        .columns(
            COMPLEX_VALUE_WITH_CREATOR.STRING,
            COMPLEX_VALUE_WITH_CREATOR.ID,
            COMPLEX_VALUE_WITH_CREATOR.AUTHOR,
            COMPLEX_VALUE_WITH_CREATOR.COMPLEX_OBJECT_WITH_SAME_LEAFS,
            COMPLEX_VALUE_WITH_CREATOR.NOT_PERSISTED_AUTHOR,
            COMPLEX_VALUE_WITH_CREATOR.NOT_PERSISTED_COMPLEX_OBJECT_WITH_SAME_LEAFS,
            COMPLEX_VALUE_WITH_CREATOR.NOT_PERSISTED_CREATOR_SIMPLE_VALUE,
            COMPLEX_VALUE_WITH_CREATOR.NOT_PERSISTED_BUILDER_SIMPLE_VALUE,
            SIMPLE_VALUE_WITH_BUILDER.all(),
            SIMPLE_VALUE_WITH_CREATOR.all()
        )
        .from(COMPLEX_VALUE_WITH_CREATOR)
        .where(idIsInsertedId)
        .takeFirst()
        .execute()
    );

    expected = ComplexValueWithCreator.create(
        retrievedObject.id(),
        retrievedObject.string(),
        null,
        retrievedObject.author(),
        retrievedObject.notPersistedAuthor(),
        retrievedObject.nullableAuthor(),
        retrievedObject.complexObjectWithSameLeafs(),
        retrievedObject.notPersistedComplexObjectWithSameLeafs(),
        retrievedObject.builderSimpleValue(),
        retrievedObject.notPersistedBuilderSimpleValue(),
        null,
        retrievedObject.creatorSimpleValue(),
        retrievedObject.notPersistedCreatorSimpleValue(),
        null);
    assertThat(expected).isEqualTo(Select
        .columns(
            COMPLEX_VALUE_WITH_CREATOR.STRING,
            COMPLEX_VALUE_WITH_CREATOR.ID,
            COMPLEX_VALUE_WITH_CREATOR.NOT_PERSISTED_AUTHOR,
            COMPLEX_VALUE_WITH_CREATOR.NOT_PERSISTED_COMPLEX_OBJECT_WITH_SAME_LEAFS,
            COMPLEX_VALUE_WITH_CREATOR.NOT_PERSISTED_CREATOR_SIMPLE_VALUE,
            COMPLEX_VALUE_WITH_CREATOR.NOT_PERSISTED_BUILDER_SIMPLE_VALUE,
            AUTHOR.all(),
            COMPLEX_OBJECT_WITH_SAME_LEAFS.all(),
            SIMPLE_VALUE_WITH_BUILDER.all(),
            SIMPLE_VALUE_WITH_CREATOR.all(),
            BOOK.all(),
            MAGAZINE.all()
        )
        .from(COMPLEX_VALUE_WITH_CREATOR)
        .where(idIsInsertedId)
        .queryDeep()
        .takeFirst()
        .execute()
    );

    complexObjectWithSameLeafs = new ComplexObjectWithSameLeafs();
    final ComplexObjectWithSameLeafs c = retrievedObject.complexObjectWithSameLeafs();
    complexObjectWithSameLeafs.name = c.name;
    complexObjectWithSameLeafs.id = c.id;
    complexObjectWithSameLeafs.simpleValueWithBuilder = c.simpleValueWithBuilder;
    complexObjectWithSameLeafs.simpleValueWithBuilderDuplicate = c.simpleValueWithBuilderDuplicate;
    expected = ComplexValueWithCreator.create(
        retrievedObject.id(),
        retrievedObject.string(),
        null,
        retrievedObject.author(),
        retrievedObject.notPersistedAuthor(),
        null,
        complexObjectWithSameLeafs,
        retrievedObject.notPersistedComplexObjectWithSameLeafs(),
        retrievedObject.builderSimpleValue(),
        retrievedObject.notPersistedBuilderSimpleValue(),
        null,
        retrievedObject.creatorSimpleValue(),
        retrievedObject.notPersistedCreatorSimpleValue(),
        null);
    final AuthorTable auth = AUTHOR.as("auth");
    assertThat(expected).isEqualTo(Select
        .columns(
            COMPLEX_VALUE_WITH_CREATOR.STRING,
            COMPLEX_VALUE_WITH_CREATOR.ID,
            COMPLEX_VALUE_WITH_CREATOR.NOT_PERSISTED_AUTHOR,
            COMPLEX_VALUE_WITH_CREATOR.NOT_PERSISTED_COMPLEX_OBJECT_WITH_SAME_LEAFS,
            COMPLEX_VALUE_WITH_CREATOR.NOT_PERSISTED_CREATOR_SIMPLE_VALUE,
            COMPLEX_VALUE_WITH_CREATOR.NOT_PERSISTED_BUILDER_SIMPLE_VALUE,
            auth.all(),
            COMPLEX_OBJECT_WITH_SAME_LEAFS.all(),
            SIMPLE_VALUE_WITH_BUILDER.all(),
            SIMPLE_VALUE_WITH_CREATOR.all()
        )
        .from(COMPLEX_VALUE_WITH_CREATOR)
        .leftJoin(auth.on(COMPLEX_VALUE_WITH_CREATOR.AUTHOR.is(auth.ID)))
        .where(idIsInsertedId)
        .queryDeep()
        .takeFirst()
        .execute()
    );
  }

  @Test
  public void cursorWithBuilder() {
    SimpleValueWithBuilder.deleteTable().execute();
    final int testSize = 10;
    final List<SimpleValueWithBuilder> values = new ArrayList<>(testSize);
    for (int i = 0; i < testSize; i++) {
      final SimpleValueWithBuilder v = SimpleValueWithBuilder.newRandom().build();
      final long persistId = v.persist().execute();
      assertThat(persistId).isNotEqualTo(-1);
      assertThat(persistId).isNotEqualTo(v.id());
      values.add(SqliteMagic_SimpleValueWithBuilder_Dao.setId(v, persistId));
    }
    final CompiledCursorSelect<SimpleValueWithBuilder, SelectN> cursorSelect = Select
        .from(SIMPLE_VALUE_WITH_BUILDER)
        .queryDeep()
        .toCursor();
    final List<SimpleValueWithBuilder> queriedMagazines = new ArrayList<>(testSize);
    @Cleanup final Cursor cursor = cursorSelect.execute();
    while (cursor.moveToNext()) {
      queriedMagazines.add(cursorSelect.getFromCurrentPosition(cursor));
    }
    assertThat(values).containsExactlyElementsIn(queriedMagazines);
  }

  @Test
  public void cursorWithCreator() {
    SimpleValueWithCreator.deleteTable().execute();
    final int testSize = 10;
    final List<SimpleValueWithCreator> values = new ArrayList<>(testSize);
    for (int i = 0; i < testSize; i++) {
      final SimpleValueWithCreator v = SimpleValueWithCreator.newRandom();
      final long persistId = v.persist().execute();
      assertThat(persistId).isNotEqualTo(-1);
      assertThat(persistId).isNotEqualTo(v.id());
      values.add(SqliteMagic_SimpleValueWithCreator_Dao.setId(v, persistId));
    }
    final CompiledCursorSelect<SimpleValueWithCreator, SelectN> cursorSelect = Select
        .from(SIMPLE_VALUE_WITH_CREATOR)
        .queryDeep()
        .toCursor();
    final List<SimpleValueWithCreator> queriedMagazines = new ArrayList<>(testSize);
    @Cleanup final Cursor cursor = cursorSelect.execute();
    while (cursor.moveToNext()) {
      queriedMagazines.add(cursorSelect.getFromCurrentPosition(cursor));
    }
    assertThat(values).containsExactlyElementsIn(queriedMagazines);
  }

  @Test
  public void builderWithColumnOptionsGetsCorrectValues() {
    BuilderWithColumnOptions.deleteTable().execute();
    final BuilderWithColumnOptions inserted = BuilderWithColumnOptions.newRandom();
    inserted.persist().execute();

    final BuilderWithColumnOptions val = Select.from(BUILDER_WITH_COLUMN_OPTIONS).takeFirst().execute();

    final Author author = new Author();
    author.id = inserted.notPersistedAuthor().id;
    final BuilderWithColumnOptions expected = inserted.copy()
        .notPersistedAuthor(author)
        .build();
    assertThat(val).isEqualTo(expected);
  }

  @Test
  public void creatorWithColumnOptionsGetsCorrectValues() {
    CreatorWithColumnOptions.deleteTable().execute();
    final CreatorWithColumnOptions inserted = CreatorWithColumnOptions.newRandom();
    inserted.persist().execute();

    final CreatorWithColumnOptions val = Select.from(CREATOR_WITH_COLUMN_OPTIONS).takeFirst().execute();

    final CreatorWithColumnOptions expected = inserted.minimalCopy();
    assertThat(val).isEqualTo(expected);
  }
}
