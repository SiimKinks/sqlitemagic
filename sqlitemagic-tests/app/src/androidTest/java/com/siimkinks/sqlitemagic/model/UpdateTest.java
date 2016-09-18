package com.siimkinks.sqlitemagic.model;

import android.support.test.runner.AndroidJUnit4;

import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.Update;
import com.siimkinks.sqlitemagic.model.immutable.BuilderMagazine;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.BuilderMagazineTable.BUILDER_MAGAZINE;

@RunWith(AndroidJUnit4.class)
public final class UpdateTest {
	@Test
	public void updateSingle() {
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
				.execute())
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
	public void updateMultiple() {
		BuilderMagazine.deleteTable().execute();
		final int testCount = 10;
		for (int i = 0; i < testCount; i++) {
			final BuilderMagazine magazine = BuilderMagazine
					.newRandom()
					.build();
			assertThat(magazine.persist().execute()).isNotEqualTo(-1);
		}
		assertThat(Update
				.table(BUILDER_MAGAZINE)
				.set(BUILDER_MAGAZINE.NAME, "asd")
				.execute())
				.isEqualTo(testCount);
		for (BuilderMagazine magazine : Select.from(BUILDER_MAGAZINE).execute()) {
			assertThat(magazine.name()).isEqualTo("asd");
		}
	}

	@Test
	public void updateWhere() {
		BuilderMagazine magazine = BuilderMagazine.newRandom().build();
		final long id = magazine.persist().execute();
		assertThat(id).isNotEqualTo(-1);
		magazine = Select
				.from(BUILDER_MAGAZINE)
				.where(BUILDER_MAGAZINE.ID.is(id))
				.queryDeep()
				.takeFirst()
				.execute();
		assertThat(BuilderMagazine
				.newRandom()
				.build()
				.persist()
				.execute()).isNotEqualTo(-1);
		assertThat(Select.from(BUILDER_MAGAZINE).count().execute()).isGreaterThan(1L);

		BuilderMagazine expected = magazine.copy()
				.name("asdasd")
				.build();
		assertThat(Update
				.table(BUILDER_MAGAZINE)
				.set(BUILDER_MAGAZINE.NAME, "asdasd")
				.where(BUILDER_MAGAZINE.ID.is(id))
				.execute())
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
	public void updateMultipleWhere() {
		BuilderMagazine.deleteTable().execute();
		final int testCount = 10;
		for (int i = 0; i < testCount; i++) {
			final BuilderMagazine magazine = BuilderMagazine
					.newRandom()
					.name("asd")
					.build();
			assertThat(magazine.persist().execute()).isNotEqualTo(-1);
		}
		for (int i = 0; i < 4; i++) {
			assertThat(BuilderMagazine
					.newRandom()
					.build()
					.persist()
					.execute())
					.isNotEqualTo(-1);
		}
		assertThat(Select.from(BUILDER_MAGAZINE).count().execute()).isEqualTo(testCount + 4);
		assertThat(Update
				.table(BUILDER_MAGAZINE)
				.set(BUILDER_MAGAZINE.NAME, "dsa")
				.where(BUILDER_MAGAZINE.NAME.is("asd"))
				.execute())
				.isEqualTo(testCount);
		final List<BuilderMagazine> magazines = Select
				.from(BUILDER_MAGAZINE)
				.where(BUILDER_MAGAZINE.NAME.is("dsa"))
				.execute();
		assertThat(magazines.size()).isEqualTo(testCount);
		for (BuilderMagazine magazine : magazines) {
			assertThat(magazine.name()).isEqualTo("dsa");
		}
	}

	@Test
	public void nothingUpdated() {
		BuilderMagazine.deleteTable().execute();
		assertThat(Update
				.table(BUILDER_MAGAZINE)
				.set(BUILDER_MAGAZINE.NAME, "asd")
				.execute())
				.isEqualTo(0);
		assertThat(Update
				.table(BUILDER_MAGAZINE)
				.set(BUILDER_MAGAZINE.NAME, "asd")
				.where(BUILDER_MAGAZINE.NAME.is("dsa"))
				.execute())
				.isEqualTo(0);
	}
}