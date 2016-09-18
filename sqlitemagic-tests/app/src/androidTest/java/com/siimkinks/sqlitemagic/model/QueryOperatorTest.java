package com.siimkinks.sqlitemagic.model;

import android.support.test.runner.AndroidJUnit4;

import com.siimkinks.sqlitemagic.CompiledCountSelect;
import com.siimkinks.sqlitemagic.CompiledSelect;
import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.TestScheduler;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import rx.Subscription;
import rx.functions.Func1;
import rx.observers.TestSubscriber;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.InternalTester.assertTriggersHaveNoObservers;
import static com.siimkinks.sqlitemagic.model.TestUtil.awaitTerminalEvent;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertAuthors;

@RunWith(AndroidJUnit4.class)
public final class QueryOperatorTest {
	private final CompiledSelect<Author, Select.SelectN> selectAuthors = Select.from(AUTHOR).compile();
	private final CompiledCountSelect countAuthors = Select.from(AUTHOR).count();
	private final RecordingObserver o = new RecordingObserver();
	private TestScheduler scheduler;

	@Before
	public void setUp() {
		Author.deleteTable().execute();
		scheduler = new TestScheduler();
	}

	@After
	public void tearDown() {
		o.assertNoMoreEvents();
		assertTriggersHaveNoObservers();
	}

	@Test
	public void runQueryOnceOperatorPropagatesUnsubscription() {
		final ArrayList<Author> authors = insertAuthors(6);
		final TestSubscriber<List<Author>> ts = new TestSubscriber<>();
		final Subscription subscription = Select
				.from(AUTHOR)
				.observe()
				.runQueryOnce()
				.filter(new Func1<List<Author>, Boolean>() {
					@Override
					public Boolean call(List<Author> authors) {
						return !authors.isEmpty();
					}
				})
				.subscribe(ts);

		ts.awaitTerminalEvent();
		assertThat(subscription.isUnsubscribed()).isTrue();
		ts.assertValue(authors);
	}

	@Test
	public void runQueryOperatorPropagatesUnsubscription() {
		final TestSubscriber<List<Author>> ts = new TestSubscriber<>();
		final Subscription subscription = Select
				.from(AUTHOR)
				.observe()
				.runQuery()
				.first(new Func1<List<Author>, Boolean>() {
					@Override
					public Boolean call(List<Author> authors) {
						return !authors.isEmpty();
					}
				})
				.subscribe(ts);

		final ArrayList<Author> authors = insertAuthors(6);

		ts.awaitTerminalEvent();
		assertThat(subscription.isUnsubscribed()).isTrue();
		ts.assertValue(authors);
	}

	@Test
	public void isNotZeroOperatorPropagatesUnsubscription() {
		final TestSubscriber<Boolean> ts = new TestSubscriber<>();
		final Subscription subscription = Select
				.from(AUTHOR)
				.count()
				.observe()
				.isNotZero()
				.first(new Func1<Boolean, Boolean>() {
					@Override
					public Boolean call(Boolean aBoolean) {
						return aBoolean;
					}
				})
				.subscribe(ts);

		insertAuthors(6);

		ts.awaitTerminalEvent();
		assertThat(subscription.isUnsubscribed()).isTrue();
		ts.assertValue(true);
	}

	@Test
	public void isZeroOperatorPropagatesUnsubscription() {
		final TestSubscriber<Boolean> ts = new TestSubscriber<>();
		final Subscription subscription = Select
				.from(AUTHOR)
				.count()
				.observe()
				.isZero()
				.first(new Func1<Boolean, Boolean>() {
					@Override
					public Boolean call(Boolean aBoolean) {
						return !aBoolean;
					}
				})
				.subscribe(ts);

		insertAuthors(6);

		ts.awaitTerminalEvent();
		assertThat(subscription.isUnsubscribed()).isTrue();
		ts.assertValue(false);
	}

	@Test
	public void queryCountIsZero() {
		Boolean isZero = countAuthors.observe()
				.isNotZero()
				.toBlocking()
				.first();
		assertThat(isZero).isNotNull();
		assertThat(isZero).isFalse();

		isZero = countAuthors.observe()
				.isZero()
				.toBlocking()
				.first();
		assertThat(isZero).isNotNull();
		assertThat(isZero).isTrue();
	}

	@Test
	public void queryCountIsNotZero() {
		insertAuthors(3);

		Boolean isNotZero = countAuthors.observe()
				.isNotZero()
				.toBlocking()
				.first();
		assertThat(isNotZero).isNotNull();
		assertThat(isNotZero).isTrue();

		isNotZero = countAuthors.observe()
				.isZero()
				.toBlocking()
				.first();
		assertThat(isNotZero).isNotNull();
		assertThat(isNotZero).isFalse();
	}

	@Test
	public void runListQuery() {
		final List<Author> expected = insertAuthors(3);
		final List<Author> result = selectAuthors.observe()
				.runQuery()
				.toBlocking()
				.first();
		assertThat(result).containsExactlyElementsIn(expected);
	}

	@Test
	public void runListQueryObservesChanges() {
		final List<Author> expected = new ArrayList<>(6);
		final List<Author> expectedFirst = insertAuthors(3);
		expected.addAll(expectedFirst);
		final TestSubscriber<List<Author>> ts = new TestSubscriber<>();

		final Subscription subscription = selectAuthors.observe()
				.runQuery()
				.take(2)
				.subscribe(ts);

		expected.addAll(insertAuthors(3));

		awaitTerminalEvent(ts);
		assertThat(subscription.isUnsubscribed()).isTrue();
		ts.assertNoErrors();
		ts.assertValues(expectedFirst, expected);
	}

	@Test
	public void runListQueryOnce() {
		final List<Author> expected = insertAuthors(3);
		final TestSubscriber<List<Author>> ts = new TestSubscriber<>();

		final Subscription subscription = selectAuthors.observe()
				.runQueryOnce()
				.subscribe(ts);

		awaitTerminalEvent(ts);
		assertThat(subscription.isUnsubscribed()).isTrue();
		ts.assertNoErrors();
		ts.assertValue(expected);
	}

	@Test
	public void runListQueryOnceRunsOnlyOnce() {
		final List<Author> expected = insertAuthors(3);
		final TestSubscriber<List<Author>> ts = new TestSubscriber<>();

		final Subscription subscription = selectAuthors.observe()
				.runQueryOnce()
				.subscribe(ts);

		insertAuthors(3);

		awaitTerminalEvent(ts);
		assertThat(subscription.isUnsubscribed()).isTrue();
		ts.assertNoErrors();
		ts.assertValue(expected);
	}

	@Test
	public void runEmptyListQuery() {
		insertAuthors(3);
		final List<Author> result = Select.from(AUTHOR)
				.where(AUTHOR.NAME.is("asd"))
				.observe()
				.runQueryOnce()
				.toBlocking()
				.first();
		assertThat(result).isNotNull();
		assertThat(result).isEmpty();
	}

	@Test
	public void runFirstQuery() {
		final Author expected = insertAuthors(3).get(0);
		final Author result = selectAuthors
				.takeFirst()
				.observe()
				.runQuery()
				.toBlocking()
				.first();
		assertThat(result).isEqualTo(expected);
	}

	@Test
	public void runFirstQueryObservesChanges() {
		final Author expected = insertAuthors(3).get(0);
		final TestSubscriber<Author> ts = new TestSubscriber<>();

		final Subscription subscription = selectAuthors
				.takeFirst()
				.observe()
				.runQuery()
				.take(2)
				.subscribe(ts);

		insertAuthors(3);

		awaitTerminalEvent(ts);
		assertThat(subscription.isUnsubscribed()).isTrue();
		ts.assertNoErrors();
		ts.assertValues(expected, expected);
	}

	@Test
	public void runFirstQueryOnce() {
		final Author expected = insertAuthors(3).get(0);
		final TestSubscriber<Author> ts = new TestSubscriber<>();

		final Subscription subscription = selectAuthors
				.takeFirst()
				.observe()
				.runQueryOnce()
				.subscribe(ts);

		awaitTerminalEvent(ts);
		assertThat(subscription.isUnsubscribed()).isTrue();
		ts.assertNoErrors();
		ts.assertValue(expected);
	}

	@Test
	public void runFirstQueryOnceRunsOnlyOnce() {
		final Author expected = insertAuthors(3).get(0);
		final TestSubscriber<Author> ts = new TestSubscriber<>();

		final Subscription subscription = selectAuthors
				.takeFirst()
				.observe()
				.runQueryOnce()
				.subscribe(ts);

		insertAuthors(3);

		awaitTerminalEvent(ts);
		assertThat(subscription.isUnsubscribed()).isTrue();
		ts.assertNoErrors();
		ts.assertValue(expected);
	}

	@Test
	public void runEmptyFirstQuery() {
		insertAuthors(3);

		final TestSubscriber<Author> ts = new TestSubscriber<>();
		final Subscription subscription = Select.from(AUTHOR)
				.where(AUTHOR.NAME.is("asd"))
				.takeFirst()
				.observe()
				.runQueryOnce()
				.subscribe(ts);

		awaitTerminalEvent(ts);
		assertThat(subscription.isUnsubscribed()).isTrue();
		ts.assertNoErrors();
		ts.assertNoValues();
	}

	@Test
	public void runCountQuery() {
		final int expected = 6;
		insertAuthors(expected);
		final Long result = countAuthors.observe()
				.runQuery()
				.toBlocking()
				.first();
		assertThat(result).isNotNull();
		assertThat(result).isEqualTo(expected);
	}

	@Test
	public void runCountQueryObservesChanges() {
		final int expected = 6;
		insertAuthors(expected / 2);
		final TestSubscriber<Long> ts = new TestSubscriber<>();

		final Subscription subscription = countAuthors.observe()
				.runQuery()
				.take(2)
				.subscribe(ts);

		insertAuthors(expected / 2);

		awaitTerminalEvent(ts);
		assertThat(subscription.isUnsubscribed()).isTrue();
		ts.assertNoErrors();
		ts.assertValues((long) expected / 2, (long) expected);
	}

	@Test
	public void runCountQueryOnce() {
		final int expected = 6;
		insertAuthors(expected);
		final TestSubscriber<Long> ts = new TestSubscriber<>();

		final Subscription subscription = countAuthors.observe()
				.runQueryOnce()
				.subscribe(ts);

		awaitTerminalEvent(ts);
		assertThat(subscription.isUnsubscribed()).isTrue();
		ts.assertNoErrors();
		ts.assertValue((long) expected);
	}

	@Test
	public void runCountQueryOnceRunsOnlyOnce() {
		final int expected = 6;
		insertAuthors(expected);
		final TestSubscriber<Long> ts = new TestSubscriber<>();

		final Subscription subscription = countAuthors.observe()
				.runQueryOnce()
				.subscribe(ts);

		insertAuthors(expected);

		awaitTerminalEvent(ts);
		assertThat(subscription.isUnsubscribed()).isTrue();
		ts.assertNoErrors();
		ts.assertValue((long) expected);
	}
	
	@Test
	public void runQueryOperatorDropsItemButRequestsMore() {
		final TestSubscriber<Author> ts = new TestSubscriber<>();
		final Subscription subscription = selectAuthors
				.takeFirst()
				.observe()
				.runQuery()
				.take(1)
				.subscribe(ts);

		final Author author = Author.newRandom();
		author.insert().execute();

		awaitTerminalEvent(ts);
		assertThat(subscription.isUnsubscribed()).isTrue();
		ts.assertNoErrors();
		ts.assertCompleted();
		ts.assertValue(author);
	}

	@Test
	public void runQueryOrDefaultEmitsDefault() {
		final Author defaultVal = Author.newRandom();
		final TestSubscriber<Author> ts = new TestSubscriber<>();
		final Subscription subscription = selectAuthors
				.takeFirst()
				.observe()
				.runQueryOrDefault(defaultVal)
				.take(2)
				.subscribe(ts);

		final Author insertedVal = Author.newRandom();
		insertedVal.insert().execute();

		awaitTerminalEvent(ts);
		assertThat(subscription.isUnsubscribed()).isTrue();
		ts.assertNoErrors();
		ts.assertCompleted();
		ts.assertValues(defaultVal, insertedVal);
	}

	@Test
	public void runQueryOnceOrDefaultEmitsDefault() {
		final Author defaultVal = Author.newRandom();
		final TestSubscriber<Author> ts = new TestSubscriber<>();
		final Subscription subscription = selectAuthors
				.takeFirst()
				.observe()
				.runQueryOnceOrDefault(defaultVal)
				.subscribe(ts);

		final Author insertedVal = Author.newRandom();
		insertedVal.insert().execute();

		awaitTerminalEvent(ts);
		assertThat(subscription.isUnsubscribed()).isTrue();
		ts.assertNoErrors();
		ts.assertCompleted();
		ts.assertValues(defaultVal);
	}
}
