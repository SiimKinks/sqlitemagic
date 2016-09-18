package com.siimkinks.sqlitemagic.model;

import android.database.Cursor;
import android.support.test.runner.AndroidJUnit4;

import com.siimkinks.sqlitemagic.CompiledCursorSelect;
import com.siimkinks.sqlitemagic.CompiledFirstSelect;
import com.siimkinks.sqlitemagic.CompiledSelect;
import com.siimkinks.sqlitemagic.Expr;
import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.Select.SelectN;
import com.siimkinks.sqlitemagic.SimpleValueWithBuilderTable;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithBuilder;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;

import lombok.Cleanup;
import rx.functions.Func1;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.BookTable.BOOK;
import static com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.COMPLEX_OBJECT_WITH_SAME_LEAFS;
import static com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE;
import static com.siimkinks.sqlitemagic.SimpleAllValuesMutableTable.SIMPLE_ALL_VALUES_MUTABLE;
import static com.siimkinks.sqlitemagic.SimpleMutableTable.SIMPLE_MUTABLE;
import static com.siimkinks.sqlitemagic.SimpleValueWithBuilderTable.SIMPLE_VALUE_WITH_BUILDER;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertComplexValuesWithSameLeafs;
import static com.siimkinks.sqlitemagic.model.TestUtil.testMutableObjectPersistAndRetrieve;
import static com.siimkinks.sqlitemagic.model.TestUtil.testMutableObjectWithDefinedIdPersistAndRetrieve;
import static rx.Observable.from;

@RunWith(AndroidJUnit4.class)
public final class SynchronousMutableQueryTest {

	@Test
	public void count() {
		Author.deleteTable().execute();
		final int testSize = 10;
		for (int i = 0; i < testSize; i++) {
			final Author m = Author.newRandom();
			m.name = "asd";
			final long persistId = m.persist().execute();
			assertThat(persistId).isNotEqualTo(-1);
			assertThat(persistId).isEqualTo(m.id);
		}
		assertThat(Author.newRandom().persist().execute()).isNotEqualTo(-1);
		assertThat(Author.newRandom().persist().execute()).isNotEqualTo(-1);
		assertThat(Author.newRandom().persist().execute()).isNotEqualTo(-1);

		assertThat(testSize).isNotEqualTo(Select
				.from(AUTHOR)
				.count()
				.execute());
		assertThat(testSize).isEqualTo(Select
				.from(AUTHOR)
				.where(AUTHOR.NAME.is("asd"))
				.count()
				.execute());
	}

	@Test
	public void simpleEmptyTable() {
		final CompiledSelect<Author, SelectN> compiledSelect = Select.from(AUTHOR).compile();

		Author.deleteTable().execute();
		final List<Author> allItems = compiledSelect.execute();
		assertThat(allItems).isNotNull();
		assertThat(allItems).isEmpty();
		assertThat(compiledSelect.takeFirst().execute()).isNull();
	}

	@Test
	public void simple() {
		final Author author = Author.newRandom();
		testMutableObjectPersistAndRetrieve(
				false,
				Author.class,
				AUTHOR,
				author,
				author.insert(),
				author.update(),
				author.persist(),
				new TestUtil.DeleteCallback() {
					@Override
					public void deleteTable() {
						Author.deleteTable().execute();
					}
				},
				new TestUtil.UpdateObjectCallback<Author>() {
					@Override
					public void update(long insertedId, Author object) {
						Author.fillWithRandomValues(object);
						object.id = insertedId;
					}
				}
		);
	}

	@Test
	public void simplePersistIgnoringNull() {
		Author.deleteTable().execute();
		Author author = Author.newRandom();
		final long id = author.persist().execute();
		assertThat(id).isNotEqualTo(-1);
		final CompiledFirstSelect<Author, SelectN> firstSelect = Select
				.from(AUTHOR)
				.where(AUTHOR.ID.is(id))
				.takeFirst();
		assertThat(firstSelect.execute())
				.isEqualTo(author);

		Author newAuthor = new Author();
		newAuthor.id = author.id;
		newAuthor.primitiveBoolean = author.primitiveBoolean;
		assertThat(newAuthor.persist().ignoreNullValues().execute()).isEqualTo(id);
		assertThat(firstSelect.execute())
				.isEqualTo(author);

		newAuthor.name = "asd";
		assertThat(newAuthor.persist().ignoreNullValues().execute()).isEqualTo(id);
		author.name = "asd";
		assertThat(firstSelect.execute())
				.isEqualTo(author);
	}

	@Test
	public void simpleBulk() {
		final CompiledSelect<Author, SelectN> compiledSelectAll = Select.from(AUTHOR).compile();

		Author.deleteTable().execute();
		final int count = 10;
		final List<Author> authors = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			authors.add(Author.newRandom());
		}

		assertThat(Author.insert(authors).execute()).isTrue();
		assertThat(authors).containsExactlyElementsIn(compiledSelectAll.execute());

		for (int i = 0; i < count; i++) {
			final Author a = authors.get(i);
			final Long id = a.id;
			Author.fillWithRandomValues(a);
			a.id = id;
		}
		assertThat(Author.update(authors).execute()).isTrue();
		assertThat(authors).containsExactlyElementsIn(compiledSelectAll.execute());

		for (int i = 0; i < count; i++) {
			final Author a = authors.get(i);
			final Long id = a.id;
			Author.fillWithRandomValues(a);
			a.id = id;
		}
		assertThat(Author.persist(authors).execute()).isTrue();
		assertThat(authors).containsExactlyElementsIn(compiledSelectAll.execute());

		Author.deleteTable().execute();
		assertThat(Author.persist(authors).execute()).isTrue();
		assertThat(authors).containsExactlyElementsIn(compiledSelectAll.execute());
	}

	@Test
	public void simpleBulkPersistIgnoringNull() {
		final CompiledSelect<Author, SelectN> compiledSelectAll = Select.from(AUTHOR).compile();
		Author.deleteTable().execute();
		final int count = 10;
		final List<Author> authors = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			authors.add(Author.newRandom());
		}
		assertThat(Author.persist(authors).execute()).isTrue();
		assertThat(authors).containsExactlyElementsIn(compiledSelectAll.execute());

		final List<Author> newAuthors = new ArrayList<>(count);
		for (Author author : authors) {
			Author a = new Author();
			a.id = author.id;
			a.primitiveBoolean = author.primitiveBoolean;
			newAuthors.add(a);
		}
		assertThat(Author.persist(newAuthors).ignoreNullValues().execute()).isTrue();
		assertThat(authors).containsExactlyElementsIn(compiledSelectAll.execute());

		for (Author newAuthor : newAuthors) {
			newAuthor.name = "asd";
		}
		assertThat(Author.persist(newAuthors).ignoreNullValues().execute()).isTrue();
		for (Author author : authors) {
			author.name = "asd";
		}
		assertThat(authors).containsExactlyElementsIn(compiledSelectAll.execute());
	}

	@Test
	public void simpleWithDefinedId() {
		final SimpleMutable object = SimpleMutable.newRandom();
		testMutableObjectWithDefinedIdPersistAndRetrieve(
				false,
				SimpleMutable.class,
				SIMPLE_MUTABLE,
				object,
				object.insert(),
				object.update(),
				object.persist(),
				new TestUtil.DeleteCallback() {
					@Override
					public void deleteTable() {
						SimpleMutable.deleteTable().execute();
					}
				}, new TestUtil.UpdateObjectCallback<SimpleMutable>() {
					@Override
					public void update(long insertedId, SimpleMutable object) {
						SimpleMutable.fillWithRandomValues(object);
						object.id = insertedId;
					}
				}
		);
	}

	@Test
	public void simpleAllValues() {
		final SimpleAllValuesMutable allValuesSet = new SimpleAllValuesMutable();
		allValuesSet.string = "asd";
		allValuesSet.primitiveShort = 16;
		allValuesSet.boxedShort = 32;
		allValuesSet.primitiveLong = 6666666;
		allValuesSet.boxedLong = 9999999L;
		allValuesSet.primitiveInt = 123;
		allValuesSet.boxedInteger = 321;
		allValuesSet.primitiveFloat = 1.5f;
		allValuesSet.boxedFloat = 5.5f;
		allValuesSet.primitiveDouble = 10.5;
		allValuesSet.boxedDouble = 44.44444;
		allValuesSet.primitiveByte = 0x4;
		allValuesSet.boxedByte = 0x4;
		allValuesSet.primitiveByteArray = new byte[]{0x55, 0x66, 0x14};
		allValuesSet.boxedByteArray = new Byte[]{0x2, 0x5, 0x8, 0xB};
		allValuesSet.primitiveBoolean = true;
		allValuesSet.boxedBoolean = Boolean.TRUE;
		allValuesSet.calendar = Calendar.getInstance();
		allValuesSet.utilDate = new Date();

		final Random r = new Random();
		testMutableObjectPersistAndRetrieve(
				false,
				SimpleAllValuesMutable.class,
				SIMPLE_ALL_VALUES_MUTABLE,
				allValuesSet,
				allValuesSet.insert(),
				allValuesSet.update(),
				allValuesSet.persist(),
				new TestUtil.DeleteCallback() {
					@Override
					public void deleteTable() {
						SimpleAllValuesMutable.deleteTable().execute();
					}
				}, new TestUtil.UpdateObjectCallback<SimpleAllValuesMutable>() {
					@Override
					public void update(long insertedId, SimpleAllValuesMutable object) {
						object.string = Utils.randomTableName();
						object.primitiveShort = (short) r.nextInt(Short.MAX_VALUE + 1);
						object.boxedShort = (short) r.nextInt(Short.MAX_VALUE + 1);
						object.primitiveLong = r.nextLong();
						object.boxedLong = r.nextLong();
						object.primitiveInt = r.nextInt();
						object.boxedInteger = r.nextInt();
						object.primitiveFloat = r.nextFloat();
						object.boxedFloat = r.nextFloat();
						object.primitiveDouble = r.nextDouble();
						object.boxedDouble = r.nextDouble();
						object.primitiveByte = 0x5;
						object.boxedByte = 0x5;
						final byte[] byteArray = new byte[4];
						r.nextBytes(byteArray);
						object.primitiveByteArray = byteArray;
						r.nextBytes(byteArray);
						object.boxedByteArray = Utils.toByteArray(byteArray);
						object.primitiveBoolean = r.nextBoolean();
						object.boxedBoolean = r.nextBoolean();
						object.calendar = Calendar.getInstance();
						object.utilDate = new Date(Math.abs(r.nextLong()));
					}
				});
	}

	@Test
	public void simpleAllPrimitiveValues() {
		final SimpleAllValuesMutable allPrimitiveValues = new SimpleAllValuesMutable();
		allPrimitiveValues.primitiveShort = 61;
		allPrimitiveValues.primitiveLong = 666666;
		allPrimitiveValues.primitiveInt = 456;
		allPrimitiveValues.primitiveFloat = 4.5f;
		allPrimitiveValues.primitiveDouble = 15.5;
		allPrimitiveValues.primitiveByte = 0x15;
		allPrimitiveValues.primitiveByteArray = new byte[]{0x65, 0x76, 0x24};
		allPrimitiveValues.primitiveBoolean = true;

		final Random r = new Random();
		testMutableObjectPersistAndRetrieve(
				false,
				SimpleAllValuesMutable.class,
				SIMPLE_ALL_VALUES_MUTABLE,
				allPrimitiveValues,
				allPrimitiveValues.insert(),
				allPrimitiveValues.update(),
				allPrimitiveValues.persist(),
				new TestUtil.DeleteCallback() {
					@Override
					public void deleteTable() {
						SimpleAllValuesMutable.deleteTable().execute();
					}
				}, new TestUtil.UpdateObjectCallback<SimpleAllValuesMutable>() {
					@Override
					public void update(long insertedId, SimpleAllValuesMutable object) {
						object.primitiveShort = (short) r.nextInt(Short.MAX_VALUE + 1);
						object.primitiveLong = r.nextLong();
						object.primitiveInt = r.nextInt();
						object.primitiveFloat = r.nextFloat();
						object.primitiveDouble = r.nextDouble();
						object.primitiveByte = 0x5;
						final byte[] byteArray = new byte[4];
						r.nextBytes(byteArray);
						object.primitiveByteArray = byteArray;
						object.primitiveBoolean = r.nextBoolean();
					}
				});
	}

	@Test
	public void simpleWithSelection() {
		SimpleAllValuesMutable.deleteTable().execute();

		final SimpleAllValuesMutable object = new SimpleAllValuesMutable();
		object.string = "asd";
		object.primitiveShort = 16;
		object.boxedShort = 32;
		object.primitiveLong = 6666666;
		object.boxedLong = 9999999L;
		object.primitiveInt = 123;
		object.boxedInteger = 321;
		object.primitiveFloat = 1.5f;
		object.boxedFloat = 5.5f;
		object.primitiveDouble = 10.5;
		object.boxedDouble = 44.44444;
		object.primitiveByte = 0xF;
		object.boxedByte = 0x4;
		object.primitiveByteArray = new byte[]{0x55, 0x66, 0x14};
		object.boxedByteArray = new Byte[]{0x2, 0x5, 0x8, 0xB};
		object.primitiveBoolean = true;
		object.boxedBoolean = Boolean.TRUE;
		object.calendar = Calendar.getInstance();
		object.utilDate = new Date();

		final long id = object.persist().execute();
		assertThat(id).isNotEqualTo(-1);

		final Expr idIsInsertedId = SIMPLE_ALL_VALUES_MUTABLE.ID.is(id);

		SimpleAllValuesMutable queryObject = Select
				.columns(SIMPLE_ALL_VALUES_MUTABLE.ID, SIMPLE_ALL_VALUES_MUTABLE.STRING,
						SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_SHORT)
				.from(SIMPLE_ALL_VALUES_MUTABLE)
				.where(idIsInsertedId)
				.takeFirst()
				.execute();

		SimpleAllValuesMutable expected = new SimpleAllValuesMutable();
		expected.id = id;
		expected.string = object.string;
		expected.primitiveShort = object.primitiveShort;
		assertThat(expected).isEqualTo(queryObject);

		queryObject = Select
				.columns(SIMPLE_ALL_VALUES_MUTABLE.all())
				.from(SIMPLE_ALL_VALUES_MUTABLE)
				.where(idIsInsertedId)
				.takeFirst()
				.execute();
		assertThat(object).isEqualTo(queryObject);

		queryObject = Select
				.columns(SIMPLE_ALL_VALUES_MUTABLE.BOXED_BYTE_ARRAY)
				.from(SIMPLE_ALL_VALUES_MUTABLE)
				.where(idIsInsertedId)
				.takeFirst()
				.execute();

		expected = new SimpleAllValuesMutable();
		expected.boxedByteArray = object.boxedByteArray;
		assertThat(expected).isEqualTo(queryObject);
	}

	@Test
	public void complex() {
		final Magazine magazine = Magazine.newRandom();
		testMutableObjectPersistAndRetrieve(
				true,
				Magazine.class,
				MAGAZINE,
				magazine,
				magazine.insert(),
				magazine.update(),
				magazine.persist(),
				new TestUtil.DeleteCallback() {
					@Override
					public void deleteTable() {
						Magazine.deleteTable().execute();
					}
				}, new TestUtil.UpdateObjectCallback<Magazine>() {
					@Override
					public void update(long insertedId, Magazine object) {
						final long authorId = object.author.id;
						final long magazineId = object.id;
						Magazine.fillWithRandomValues(object);
						object.id = magazineId;
						object.author.id = authorId;
					}
				}

		);
	}

	@Test
	public void complexPersistIgnoringNull() {
		Magazine.deleteTable().execute();
		Magazine magazine = Magazine.newRandom();
		final long id = magazine.persist().execute();
		assertThat(id).isNotEqualTo(-1);
		final CompiledFirstSelect<Magazine, SelectN> firstSelect = Select
				.from(MAGAZINE)
				.where(MAGAZINE._ID.is(id))
				.queryDeep()
				.takeFirst();
		assertThat(firstSelect.execute())
				.isEqualTo(magazine);

		Magazine newMagazine = new Magazine();
		newMagazine.id = magazine.id;
		newMagazine.nrOfReleases = magazine.nrOfReleases;
		assertThat(newMagazine.persist().ignoreNullValues().execute()).isEqualTo(id);
		assertThat(firstSelect.execute())
				.isEqualTo(magazine);

		Author author = new Author();
		author.id = magazine.author.id;
		author.primitiveBoolean = magazine.author.primitiveBoolean;
		newMagazine.author = author;
		assertThat(newMagazine.persist().ignoreNullValues().execute()).isEqualTo(id);
		assertThat(firstSelect.execute())
				.isEqualTo(magazine);

		newMagazine.name = "asd";
		newMagazine.author.name = "asd";
		assertThat(newMagazine.persist().ignoreNullValues().execute()).isEqualTo(id);
		magazine.name = "asd";
		magazine.author.name = "asd";
		assertThat(firstSelect.execute())
				.isEqualTo(magazine);
	}

	@Test
	public void complexWithSameLeafs() {
		ComplexObjectWithSameLeafs.deleteTable().execute();
		final CompiledFirstSelect<ComplexObjectWithSameLeafs, SelectN> compiledFirstSelect = Select
				.from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
				.queryDeep()
				.takeFirst();

		ComplexObjectWithSameLeafs complex = ComplexObjectWithSameLeafs.newRandom();
		final long givenId = complex.id;

		final long insertId = complex.insert().execute();
		assertThat(insertId).isNotEqualTo(-1);
		assertThat(complex.id).isNotEqualTo(givenId);
		assertThat(complex.id).isEqualTo(insertId);
		final ComplexObjectWithSameLeafs returnedComplex = compiledFirstSelect.execute();
		assertThat(complex.equalsWithoutId(returnedComplex)).isTrue();

		complex = returnedComplex;
		complex.name = "asdasd";
		complex.book.nrOfReleases = 1232132;
		complex.book.author.name = "new author";
		complex.magazine.name = "dsadsa";
		complex.magazine.author.name = "new magazine author";

		assertThat(complex.update().execute()).isTrue();
		final CompiledFirstSelect<ComplexObjectWithSameLeafs, SelectN> selectWithId = Select
				.from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
				.where(COMPLEX_OBJECT_WITH_SAME_LEAFS.ID.is(insertId))
				.queryDeep()
				.takeFirst();

		assertThat(complex.equalsWithoutId(selectWithId.execute())).isTrue();

		complex.name = "ffff";
		complex.book.nrOfReleases = 1232132;
		complex.book.author.name = "new author2";
		complex.magazine.name = "dddd";
		complex.magazine.author.name = "new magazine author2";

		final long persistUpdatedId = complex.persist().execute();
		assertThat(insertId).isEqualTo(persistUpdatedId);
		assertThat(complex.id).isEqualTo(persistUpdatedId);
		assertThat(complex.equalsWithoutId(selectWithId.execute())).isTrue();

		ComplexObjectWithSameLeafs.deleteTable().execute();

		final long persistInsertedId = complex.persist().execute();
		assertThat(persistInsertedId).isNotEqualTo(-1);
		assertThat(complex.id).isEqualTo(persistInsertedId);
		assertThat(complex.equalsWithoutId(compiledFirstSelect.execute())).isTrue();
	}

	@Test
	public void complexWithSelection() {
		ComplexObjectWithSameLeafs.deleteTable().execute();
		final ComplexObjectWithSameLeafs object = ComplexObjectWithSameLeafs.newRandom();

		final long id = object.persist().execute();
		assertThat(id).isNotEqualTo(-1);

		final Expr idIsInsertedId = COMPLEX_OBJECT_WITH_SAME_LEAFS.ID.is(id);

		ComplexObjectWithSameLeafs expected = new ComplexObjectWithSameLeafs();
		expected.id = object.id;
		expected.name = object.name;
		assertThat(expected).isEqualTo(Select
				.columns(COMPLEX_OBJECT_WITH_SAME_LEAFS.ID, COMPLEX_OBJECT_WITH_SAME_LEAFS.NAME)
				.from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
				.where(idIsInsertedId)
				.queryDeep()
				.takeFirst()
				.execute());
		assertThat(expected).isEqualTo(Select
				.columns(COMPLEX_OBJECT_WITH_SAME_LEAFS.ID, COMPLEX_OBJECT_WITH_SAME_LEAFS.NAME)
				.from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
				.where(idIsInsertedId)
				.takeFirst()
				.execute());

		expected = new ComplexObjectWithSameLeafs();
		expected.id = object.id;
		expected.name = object.name;
		expected.book = new Book();
		expected.book.setBaseId(object.book.getBaseId());
		expected.magazine = new Magazine();
		expected.magazine.id = object.magazine.id;
		assertThat(expected).isEqualTo(Select
				.columns(COMPLEX_OBJECT_WITH_SAME_LEAFS.all())
				.from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
				.where(idIsInsertedId)
				.takeFirst()
				.execute());

		expected = new ComplexObjectWithSameLeafs();
		expected.book = new Book();
		expected.book.setBaseId(object.book.getBaseId());
		expected.magazine = new Magazine();
		expected.magazine.id = object.magazine.id;
		assertThat(expected).isEqualTo(Select
				.columns(COMPLEX_OBJECT_WITH_SAME_LEAFS.BOOK, COMPLEX_OBJECT_WITH_SAME_LEAFS.MAGAZINE)
				.from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
				.where(idIsInsertedId)
				.takeFirst()
				.execute());
		assertThat(expected).isEqualTo(Select
				.columns(COMPLEX_OBJECT_WITH_SAME_LEAFS.BOOK, COMPLEX_OBJECT_WITH_SAME_LEAFS.MAGAZINE)
				.from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
				.where(idIsInsertedId)
				.queryDeep()
				.takeFirst()
				.execute());

		expected = new ComplexObjectWithSameLeafs();
		expected.book = object.book;
		expected.magazine = object.magazine;
		assertThat(expected).isEqualTo(Select
				.columns(MAGAZINE.all(),
						BOOK.all(),
						AUTHOR.all())
				.from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
				.where(idIsInsertedId)
				.queryDeep()
				.takeFirst()
				.execute());

		expected.book.author = null;
		expected.magazine.author = null;
		assertThat(expected).isEqualTo(Select
				.columns(MAGAZINE.all(),
						BOOK.all())
				.from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
				.where(idIsInsertedId)
				.queryDeep()
				.takeFirst()
				.execute());

		expected = new ComplexObjectWithSameLeafs();
		expected.simpleValueWithBuilder = object.simpleValueWithBuilder;
		expected.simpleValueWithBuilderDuplicate = object.simpleValueWithBuilderDuplicate;
		assertThat(expected.equalsWithoutId(Select
				.columns(SIMPLE_VALUE_WITH_BUILDER.all())
				.from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
				.where(idIsInsertedId)
				.queryDeep()
				.takeFirst()
				.execute()))
				.isTrue();

		expected = new ComplexObjectWithSameLeafs();
		expected.simpleValueWithBuilderDuplicate = object.simpleValueWithBuilderDuplicate;
		final SimpleValueWithBuilderTable duplicate = SIMPLE_VALUE_WITH_BUILDER.as("duplicate");
		assertThat(expected.equalsWithoutId(Select
				.columns(duplicate.all())
				.from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
				.leftJoin(duplicate.on(COMPLEX_OBJECT_WITH_SAME_LEAFS.SIMPLE_VALUE_WITH_BUILDER_DUPLICATE.is(duplicate.ID)))
				.where(idIsInsertedId)
				.queryDeep()
				.takeFirst()
				.execute()))
				.isTrue();
	}

	@Test
	public void complexBulk() {
		final CompiledSelect<Magazine, SelectN> compiledSelectAll = Select
				.from(MAGAZINE)
				.queryDeep()
				.compile();

		Magazine.deleteTable().execute();
		final int count = 10;
		final List<Magazine> magazines = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			magazines.add(Magazine.newRandom());
		}

		assertThat(Magazine.insert(magazines).execute()).isTrue();
		assertThat(magazines).containsExactlyElementsIn(compiledSelectAll.execute());

		for (int i = 0; i < count; i++) {
			final Magazine m = magazines.get(i);
			final Long id = m.id;
			final Long aId = m.author.id;
			Magazine.fillWithRandomValues(m);
			m.id = id;
			m.author.id = aId;
		}
		assertThat(Magazine.update(magazines).execute()).isTrue();
		assertThat(magazines).containsExactlyElementsIn(compiledSelectAll.execute());

		for (int i = 0; i < count; i++) {
			final Magazine m = magazines.get(i);
			final Long id = m.id;
			final Long aId = m.author.id;
			Magazine.fillWithRandomValues(m);
			m.id = id;
			m.author.id = aId;
		}
		assertThat(Magazine.persist(magazines).execute()).isTrue();
		assertThat(magazines).containsExactlyElementsIn(compiledSelectAll.execute());

		Magazine.deleteTable().execute();
		assertThat(Magazine.persist(magazines).execute()).isTrue();
		assertThat(magazines).containsExactlyElementsIn(compiledSelectAll.execute());
	}

	@Test
	public void complexBulkPersistIgnoringNull() {
		final CompiledSelect<Magazine, SelectN> compiledSelectAll = Select
				.from(MAGAZINE)
				.queryDeep()
				.compile();
		Magazine.deleteTable().execute();
		final int count = 10;
		final List<Magazine> magazines = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			magazines.add(Magazine.newRandom());
		}
		assertThat(Magazine.persist(magazines).execute()).isTrue();
		assertThat(magazines).containsExactlyElementsIn(compiledSelectAll.execute());

		final List<Magazine> newMagazines = new ArrayList<>(count);
		for (Magazine magazine : magazines) {
			Magazine m = new Magazine();
			m.id = magazine.id;
			m.nrOfReleases = magazine.nrOfReleases;
			newMagazines.add(m);
		}
		assertThat(Magazine.persist(newMagazines).ignoreNullValues().execute()).isTrue();
		assertThat(magazines).containsExactlyElementsIn(compiledSelectAll.execute());

		for (int i = 0, newMagazinesSize = newMagazines.size(); i < newMagazinesSize; i++) {
			Magazine newMagazine = newMagazines.get(i);
			newMagazine.name = "asd";
			Author author = new Author();
			final Author a = magazines.get(i).author;
			author.id = a.id;
			author.primitiveBoolean = a.primitiveBoolean;
			newMagazine.author = author;
		}
		assertThat(Magazine.persist(newMagazines).ignoreNullValues().execute()).isTrue();
		for (Magazine magazine : magazines) {
			magazine.name = "asd";
		}
		assertThat(magazines).containsExactlyElementsIn(compiledSelectAll.execute());

		for (int i = 0, newMagazinesSize = newMagazines.size(); i < newMagazinesSize; i++) {
			Magazine newMagazine = newMagazines.get(i);
			newMagazine.name = "dsa";
			Author author = new Author();
			final Author a = magazines.get(i).author;
			author.id = a.id;
			author.name = "dsa";
			author.primitiveBoolean = a.primitiveBoolean;
			newMagazine.author = author;
		}
		assertThat(Magazine.persist(newMagazines).ignoreNullValues().execute()).isTrue();
		for (Magazine magazine : magazines) {
			magazine.name = "dsa";
			magazine.author.name = "dsa";
		}
		assertThat(magazines).containsExactlyElementsIn(compiledSelectAll.execute());
	}

	@Test
	public void complexDeepObjectSelectionFromAllReturnsCorrectObjects() {
		ComplexObjectWithSameLeafs.deleteTable().execute();
		SimpleValueWithBuilder.deleteTable().execute();
		Book.deleteTable().execute();
		Magazine.deleteTable().execute();
		Author.deleteTable().execute();

		final List<ComplexObjectWithSameLeafs> expected = from(insertComplexValuesWithSameLeafs(5))
				.map(new Func1<ComplexObjectWithSameLeafs, ComplexObjectWithSameLeafs>() {
					@Override
					public ComplexObjectWithSameLeafs call(ComplexObjectWithSameLeafs o) {
						o.simpleValueWithBuilder = null;
						o.simpleValueWithBuilderDuplicate = null;
						o.magazine = null;
						o.book.author = null;
						return o;
					}
				})
				.toList()
				.toBlocking()
				.first();

		final List<ComplexObjectWithSameLeafs> vals = Select
				.columns(COMPLEX_OBJECT_WITH_SAME_LEAFS.NAME,
						BOOK.all(),
						COMPLEX_OBJECT_WITH_SAME_LEAFS.ID)
				.from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
				.queryDeep()
				.execute();

		assertThat(vals).containsExactlyElementsIn(expected);
	}

	@Test
	public void cursor() {
		Magazine.deleteTable().execute();
		final int testSize = 10;
		final List<Magazine> magazines = new ArrayList<>(testSize);
		for (int i = 0; i < testSize; i++) {
			final Magazine m = Magazine.newRandom();
			final long persistId = m.persist().execute();
			assertThat(persistId).isNotEqualTo(-1);
			assertThat(persistId).isEqualTo(m.id);
			magazines.add(m);
		}
		final CompiledCursorSelect<Magazine, SelectN> cursorSelect = Select
				.from(MAGAZINE)
				.queryDeep()
				.toCursor();
		final List<Magazine> queriedMagazines = new ArrayList<>(testSize);
		@Cleanup final Cursor cursor = cursorSelect.execute();
		while (cursor.moveToNext()) {
			queriedMagazines.add(cursorSelect.getFromCurrentPosition(cursor));
		}
		assertThat(magazines).containsExactlyElementsIn(queriedMagazines);
	}

	@Test
	public void rawSelect() {
		Author.deleteTable().execute();
		final int testSize = 10;
		final List<Author> authors = new ArrayList<>(testSize);
		for (int i = 0; i < testSize; i++) {
			final Author m = Author.newRandom();
			final long persistId = m.persist().execute();
			assertThat(persistId).isNotEqualTo(-1);
			assertThat(persistId).isEqualTo(m.id);
			authors.add(m);
		}
		final Cursor cursor = Select.raw("SELECT * FROM author")
				.from(AUTHOR)
				.execute();
		int i = 0;
		while (cursor.moveToNext()) {
			final Author author = authors.get(i);
			assertThat(author).isEqualTo(Author.getFromCursorPosition(cursor));
			i++;
		}
	}

	@Test
	public void rawSelectWithArgs() {
		Author.deleteTable().execute();
		final int testSize = 10;
		final List<Author> authors = new ArrayList<>(testSize);
		for (int i = 0; i < testSize; i++) {
			final Author m = Author.newRandom();
			m.name = "asd";
			final long persistId = m.persist().execute();
			assertThat(persistId).isNotEqualTo(-1);
			assertThat(persistId).isEqualTo(m.id);
			authors.add(m);
		}
		assertThat(Author.newRandom().persist().execute()).isNotEqualTo(-1);

		final Cursor cursor = Select.raw("SELECT * FROM author WHERE name=?")
				.from(AUTHOR)
				.withArgs("asd")
				.execute();
		int i = 0;
		while (cursor.moveToNext()) {
			final Author author = authors.get(i);
			final Author cursorObject = Author.getFromCursorPosition(cursor);
			assertThat(cursorObject.name).isEqualTo("asd");
			assertThat(author).isEqualTo(cursorObject);
			i++;
		}
	}

	@Test
	public void queryRenamedComplexTable() {
		ComplexObjectWithSameLeafs.deleteTable().execute();
		Book.deleteTable().execute();
		Magazine.deleteTable().execute();
		Author.deleteTable().execute();
		SimpleValueWithBuilder.deleteTable().execute();

		final List<ComplexObjectWithSameLeafs> expected = insertComplexValuesWithSameLeafs(8);

		final List<ComplexObjectWithSameLeafs> result = Select
				.from(COMPLEX_OBJECT_WITH_SAME_LEAFS.as("cowsl"))
				.queryDeep()
				.execute();

		for (int i = 0; i < result.size(); i++) {
			assertThat(result.get(i).equalsWithoutId(expected.get(i))).isTrue();
		}
	}
}
