package com.siimkinks.sqlitemagic.model;

import android.support.test.runner.AndroidJUnit4;

import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.model.immutable.BuilderMagazine;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithBuilder;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithCreator;
import com.siimkinks.sqlitemagic.model.immutable.SqliteMagic_BuilderMagazine_Dao;
import com.siimkinks.sqlitemagic.model.view.ComplexBuilderView;
import com.siimkinks.sqlitemagic.model.view.ComplexCreatorView;
import com.siimkinks.sqlitemagic.model.view.ComplexInterfaceView;
import com.siimkinks.sqlitemagic.model.view.ComplexView;
import com.siimkinks.sqlitemagic.model.view.InterfaceView;
import com.siimkinks.sqlitemagic.model.view.SimpleBuilderView;
import com.siimkinks.sqlitemagic.model.view.SimpleCreatorView;
import com.siimkinks.sqlitemagic.model.view.SimpleInterfaceView;
import com.siimkinks.sqlitemagic.model.view.ValueViewWithBuilder;
import com.siimkinks.sqlitemagic.model.view.ValueViewWithCreator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.ComplexBuilderViewTable.COMPLEX_BUILDER_VIEW;
import static com.siimkinks.sqlitemagic.ComplexCreatorViewTable.COMPLEX_CREATOR_VIEW;
import static com.siimkinks.sqlitemagic.ComplexInterfaceViewTable.COMPLEX_INTERFACE_VIEW;
import static com.siimkinks.sqlitemagic.ComplexViewTable.COMPLEX_VIEW;
import static com.siimkinks.sqlitemagic.InterfaceViewTable.INTERFACE_VIEW;
import static com.siimkinks.sqlitemagic.SimpleBuilderViewTable.SIMPLE_BUILDER_VIEW;
import static com.siimkinks.sqlitemagic.SimpleCreatorViewTable.SIMPLE_CREATOR_VIEW;
import static com.siimkinks.sqlitemagic.SimpleInterfaceViewTable.SIMPLE_INTERFACE_VIEW;
import static com.siimkinks.sqlitemagic.ValueViewWithBuilderTable.VALUE_VIEW_WITH_BUILDER;
import static com.siimkinks.sqlitemagic.ValueViewWithCreatorTable.VALUE_VIEW_WITH_CREATOR;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertBuilderSimpleValues;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertCreatorSimpleValues;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertMagazines;

@RunWith(AndroidJUnit4.class)
public final class ViewTest {

	@Before
	public void setUp() {
		Magazine.deleteTable().execute();
		Author.deleteTable().execute();
		Book.deleteTable().execute();
		SimpleValueWithBuilder.deleteTable().execute();
		SimpleValueWithCreator.deleteTable().execute();
		BuilderMagazine.deleteTable().execute();
	}

	@Test
	public void simple() {
		final int testCount = 4;
		final List<Magazine> magazines = insertMagazines(testCount);
		final List<SimpleInterfaceView> execute = Select
				.from(SIMPLE_INTERFACE_VIEW)
				.execute();
		final int executeSize = execute.size();
		assertThat(executeSize).isEqualTo(testCount);
		for (int i = 0; i < executeSize; i++) {
			SimpleInterfaceView view = execute.get(i);
			final Magazine magazine = magazines.get(i);
			assertThat(view.authorName()).isEqualTo(magazine.author.name);
			assertThat(view.magazineName()).isEqualTo(magazine.name);
		}
	}

	@Test
	public void simpleBuilderView() {
		final int testCount = 4;
		final List<Magazine> magazines = insertMagazines(testCount);
		final List<SimpleBuilderView> execute = Select.from(SIMPLE_BUILDER_VIEW).execute();
		final int executeSize = execute.size();
		assertThat(executeSize).isEqualTo(testCount);
		for (int i = 0; i < executeSize; i++) {
			SimpleBuilderView view = execute.get(i);
			final Magazine magazine = magazines.get(i);
			assertThat(view.authorName()).isEqualTo(magazine.author.name);
			assertThat(view.magazineName()).isEqualTo(magazine.name);
		}
	}

	@Test
	public void simpleCreatorView() {
		final int testCount = 4;
		final List<Magazine> magazines = insertMagazines(testCount);
		final List<SimpleCreatorView> execute = Select.from(SIMPLE_CREATOR_VIEW).execute();
		final int executeSize = execute.size();
		assertThat(executeSize).isEqualTo(testCount);
		for (int i = 0; i < executeSize; i++) {
			SimpleCreatorView view = execute.get(i);
			final Magazine magazine = magazines.get(i);
			assertThat(view.authorName()).isEqualTo(magazine.author.name);
			assertThat(view.magazineName()).isEqualTo(magazine.name);
		}
	}

	@Test
	public void complexAbstractClassWithBuilder() {
		final int testCount = 4;
		final List<Magazine> magazines = insertMagazines(testCount);
		final List<SimpleValueWithBuilder> simpleValueWithBuilders = insertBuilderSimpleValues(testCount);
		final List<SimpleValueWithCreator> simpleValueWithCreators = insertCreatorSimpleValues(testCount);
		final List<ValueViewWithBuilder> execute = Select.from(VALUE_VIEW_WITH_BUILDER).execute();
		final int executeSize = execute.size();
		assertThat(executeSize).isEqualTo((int) Math.pow(testCount, 3));
		int l = 0;
		for (int i = 0; i < testCount; i++) {
			final Magazine magazine = magazines.get(i);
			for (int j = 0; j < testCount; j++) {
				final SimpleValueWithBuilder builder = simpleValueWithBuilders.get(j);
				for (int k = 0; k < testCount; k++) {
					final ValueViewWithBuilder valueView = execute.get(l);
					assertThat(valueView.magazineName()).isEqualTo(magazine.name);
					assertThat(valueView.authorName()).isEqualTo(magazine.author.name);
					assertThat(valueView.simpleBuilder()).isEqualTo(builder);
					assertThat(valueView.simpleCreator()).isEqualTo(simpleValueWithCreators.get(k));
					l++;
				}
			}
		}
	}

	@Test
	public void complexAbstractClassWithCreator() {
		final int testCount = 4;
		final List<Magazine> magazines = insertMagazines(testCount);
		final List<SimpleValueWithBuilder> simpleValueWithBuilders = insertBuilderSimpleValues(testCount);
		final List<SimpleValueWithCreator> simpleValueWithCreators = insertCreatorSimpleValues(testCount);
		final List<ValueViewWithCreator> execute = Select
				.from(VALUE_VIEW_WITH_CREATOR)
				.execute();
		final int executeSize = execute.size();
		assertThat(executeSize).isEqualTo((int) Math.pow(testCount, 3));
		int l = 0;
		for (int i = 0; i < testCount; i++) {
			final Magazine magazine = magazines.get(i);
			for (int j = 0; j < testCount; j++) {
				final SimpleValueWithBuilder builder = simpleValueWithBuilders.get(j);
				for (int k = 0; k < testCount; k++) {
					final ValueViewWithCreator valueView = execute.get(l);
					assertThat(valueView.magazineName()).isEqualTo(magazine.name);
					assertThat(valueView.authorName()).isEqualTo(magazine.author.name);
					assertThat(valueView.simpleBuilder()).isEqualTo(builder);
					assertThat(valueView.simpleCreator()).isEqualTo(simpleValueWithCreators.get(k));
					l++;
				}
			}
		}
	}

	@Test
	public void complexView() {
		final int testCount = 4;
		final List<Magazine> magazines = insertMagazines(testCount);
		final List<Book> books = new ArrayList<>(testCount);
		for (int i = 0; i < testCount; i++) {
			final Book book = Book.newRandom();
			book.author = magazines.get(i).author;
			book.persist().execute();
			books.add(book);
		}

		final List<ComplexView> execute = Select.from(COMPLEX_VIEW).execute();
		final int executeSize = execute.size();
		assertThat(executeSize).isEqualTo(testCount);
		for (int i = 0; i < testCount; i++) {
			final ComplexView view = execute.get(i);
			final Magazine magazine = magazines.get(i);
			final Book book = books.get(i);
			assertThat(view.bookTitle()).isEqualTo(book.title);
			assertThat(view.bookNrOfReleases()).isEqualTo(book.nrOfReleases);
			assertThat(view.magazineName()).isEqualTo(magazine.name);
			assertThat(view.magazineNrOfReleases()).isEqualTo(magazine.nrOfReleases);
			assertThat(view.author()).isEqualTo(magazine.author);
		}
	}

	@Test
	public void complexInterface() {
		final int testCount = 4;
		final List<Magazine> magazines = insertMagazines(testCount);
		final List<SimpleValueWithBuilder> simpleValueWithBuilders = insertBuilderSimpleValues(testCount);
		final List<SimpleValueWithCreator> simpleValueWithCreators = insertCreatorSimpleValues(testCount);
		final List<InterfaceView> execute = Select.from(INTERFACE_VIEW).execute();
		final int executeSize = execute.size();
		assertThat(executeSize).isEqualTo((int) Math.pow(testCount, 3));
		int l = 0;
		for (int i = 0; i < testCount; i++) {
			final Magazine magazine = magazines.get(i);
			for (int j = 0; j < testCount; j++) {
				final SimpleValueWithBuilder builder = simpleValueWithBuilders.get(j);
				for (int k = 0; k < testCount; k++) {
					final InterfaceView valueView = execute.get(l);
					assertThat(valueView.magazineNameLen()).isEqualTo(magazine.name.length());
					assertThat(valueView.authorName()).isEqualTo(magazine.author.name);
					assertThat(valueView.simpleBuilder()).isEqualTo(builder);
					assertThat(valueView.simpleCreator()).isEqualTo(simpleValueWithCreators.get(k));
					l++;
				}
			}
		}
	}

	@Test
	public void complexDeepInterfaceView() {
		final int testCount = 4;
		final ArrayList<BuilderMagazine> builderMagazines = new ArrayList<>(testCount);
		for (int i = 0; i < testCount; i++) {
			final BuilderMagazine magazine = BuilderMagazine.newRandom().build();
			final long id = magazine.persist().execute();
			assertThat(id).isNotEqualTo(-1);
			builderMagazines.add(SqliteMagic_BuilderMagazine_Dao.setId(magazine, id));
		}
		final List<ComplexInterfaceView> execute = Select
				.from(COMPLEX_INTERFACE_VIEW)
				.queryDeep()
				.execute();
		assertThat(execute.size()).isEqualTo(testCount);
		for (int i = 0; i < testCount; i++) {
			final BuilderMagazine magazine = builderMagazines.get(i);
			final ComplexInterfaceView complexView = execute.get(i);
			assertThat(complexView.builderString()).isEqualTo(magazine.simpleValueWithBuilder().stringValue());
			assertThat(magazine.equalsWithoutId(complexView.builderMagazine())).isTrue();
			assertThat(complexView.authorName()).isEqualTo(magazine.author().name);
		}
	}

	@Test
	public void complexShallowInterfaceView() {
		final int testCount = 4;
		final ArrayList<BuilderMagazine> builderMagazines = new ArrayList<>(testCount);
		for (int i = 0; i < testCount; i++) {
			final BuilderMagazine magazine = BuilderMagazine.newRandom().build();
			final long id = magazine.persist().execute();
			assertThat(id).isNotEqualTo(-1);
			builderMagazines.add(SqliteMagic_BuilderMagazine_Dao.setId(magazine, id));
		}
		final List<ComplexInterfaceView> execute = Select
				.from(COMPLEX_INTERFACE_VIEW)
				.execute();
		assertThat(execute.size()).isEqualTo(testCount);
		for (int i = 0; i < testCount; i++) {
			final BuilderMagazine magazine = builderMagazines.get(i);
			final ComplexInterfaceView complexView = execute.get(i);
			assertThat(complexView.builderString()).isEqualTo(magazine.simpleValueWithBuilder().stringValue());
			assertThat(magazine.id()).isEqualTo(complexView.builderMagazine().id());
			assertThat(magazine.equalsWithoutId(complexView.builderMagazine())).isTrue();
			assertThat(complexView.authorName()).isEqualTo(magazine.author().name);
		}
	}

	@Test
	public void complexDeepBuilderView() {
		final int testCount = 4;
		final ArrayList<BuilderMagazine> builderMagazines = new ArrayList<>(testCount);
		for (int i = 0; i < testCount; i++) {
			final BuilderMagazine magazine = BuilderMagazine.newRandom().build();
			final long id = magazine.persist().execute();
			assertThat(id).isNotEqualTo(-1);
			builderMagazines.add(SqliteMagic_BuilderMagazine_Dao.setId(magazine, id));
		}
		final List<ComplexBuilderView> execute = Select
				.from(COMPLEX_BUILDER_VIEW)
				.queryDeep()
				.execute();
		assertThat(execute.size()).isEqualTo(testCount);
		for (int i = 0; i < testCount; i++) {
			final BuilderMagazine magazine = builderMagazines.get(i);
			final ComplexBuilderView complexView = execute.get(i);
			assertThat(complexView.builderString()).isEqualTo(magazine.simpleValueWithBuilder().stringValue());
			assertThat(magazine.equalsWithoutId(complexView.builderMagazine())).isTrue();
			assertThat(complexView.authorName()).isEqualTo(magazine.author().name);
		}
	}

	@Test
	public void complexShallowBuilderView() {
		final int testCount = 4;
		final ArrayList<BuilderMagazine> builderMagazines = new ArrayList<>(testCount);
		for (int i = 0; i < testCount; i++) {
			final BuilderMagazine magazine = BuilderMagazine.newRandom().build();
			final long id = magazine.persist().execute();
			assertThat(id).isNotEqualTo(-1);
			builderMagazines.add(SqliteMagic_BuilderMagazine_Dao.setId(magazine, id));
		}
		final List<ComplexBuilderView> execute = Select
				.from(COMPLEX_BUILDER_VIEW)
				.execute();
		assertThat(execute.size()).isEqualTo(testCount);
		for (int i = 0; i < testCount; i++) {
			final BuilderMagazine magazine = builderMagazines.get(i);
			final ComplexBuilderView complexView = execute.get(i);
			assertThat(complexView.builderString()).isEqualTo(magazine.simpleValueWithBuilder().stringValue());
			assertThat(magazine.id()).isEqualTo(complexView.builderMagazine().id());
			assertThat(magazine.equalsWithoutId(complexView.builderMagazine())).isTrue();
			assertThat(complexView.authorName()).isEqualTo(magazine.author().name);
		}
	}

	@Test
	public void complexDeepCreatorView() {
		final int testCount = 4;
		final ArrayList<BuilderMagazine> builderMagazines = new ArrayList<>(testCount);
		for (int i = 0; i < testCount; i++) {
			final BuilderMagazine magazine = BuilderMagazine.newRandom().build();
			final long id = magazine.persist().execute();
			assertThat(id).isNotEqualTo(-1);
			builderMagazines.add(SqliteMagic_BuilderMagazine_Dao.setId(magazine, id));
		}
		final List<ComplexCreatorView> execute = Select
				.from(COMPLEX_CREATOR_VIEW)
				.queryDeep()
				.execute();
		assertThat(execute.size()).isEqualTo(testCount);
		for (int i = 0; i < testCount; i++) {
			final BuilderMagazine magazine = builderMagazines.get(i);
			final ComplexCreatorView complexView = execute.get(i);
			assertThat(complexView.builderString()).isEqualTo(magazine.simpleValueWithBuilder().stringValue());
			assertThat(magazine.equalsWithoutId(complexView.builderMagazine())).isTrue();
			assertThat(complexView.authorName()).isEqualTo(magazine.author().name);
		}
	}

	@Test
	public void complexShallowCreatorView() {
		final int testCount = 4;
		final ArrayList<BuilderMagazine> builderMagazines = new ArrayList<>(testCount);
		for (int i = 0; i < testCount; i++) {
			final BuilderMagazine magazine = BuilderMagazine.newRandom().build();
			final long id = magazine.persist().execute();
			assertThat(id).isNotEqualTo(-1);
			builderMagazines.add(SqliteMagic_BuilderMagazine_Dao.setId(magazine, id));
		}
		final List<ComplexCreatorView> execute = Select
				.from(COMPLEX_CREATOR_VIEW)
				.execute();
		assertThat(execute.size()).isEqualTo(testCount);
		for (int i = 0; i < testCount; i++) {
			final BuilderMagazine magazine = builderMagazines.get(i);
			final ComplexCreatorView complexView = execute.get(i);
			assertThat(complexView.builderString()).isEqualTo(magazine.simpleValueWithBuilder().stringValue());
			assertThat(magazine.id()).isEqualTo(complexView.builderMagazine().id());
			assertThat(magazine.equalsWithoutId(complexView.builderMagazine())).isTrue();
			assertThat(complexView.authorName()).isEqualTo(magazine.author().name);
		}
	}

	@Test
	public void constrainedViewQuery() {
		final int testCount = 4;
		final List<Magazine> magazines = insertMagazines(testCount);
		final List<Book> books = new ArrayList<>(testCount);
		for (int i = 0; i < testCount; i++) {
			final Book book = Book.newRandom();
			book.author = magazines.get(i).author;
			book.persist().execute();
			books.add(book);
		}

		final Magazine magazine = magazines.get(0);
		final Book book = books.get(0);

		final List<ComplexView> result = Select
				.from(COMPLEX_VIEW)
				.where(COMPLEX_VIEW.BOOKTITLE.is(book.title)
						.and(COMPLEX_VIEW.BOOK_NR_OF_RELEASES.is(book.nrOfReleases))
						.and(COMPLEX_VIEW.MN.is(magazine.name))
						.and(COMPLEX_VIEW.MNR.is(magazine.nrOfReleases)))
				.execute();

		assertThat(result.size()).isEqualTo(1);
		final ComplexView view = result.get(0);
		assertThat(view.bookTitle()).isEqualTo(book.title);
		assertThat(view.bookNrOfReleases()).isEqualTo(book.nrOfReleases);
		assertThat(view.magazineName()).isEqualTo(magazine.name);
		assertThat(view.magazineNrOfReleases()).isEqualTo(magazine.nrOfReleases);
		assertThat(view.author()).isEqualTo(magazine.author);
	}
}
