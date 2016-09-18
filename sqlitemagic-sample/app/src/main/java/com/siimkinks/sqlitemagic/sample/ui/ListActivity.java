package com.siimkinks.sqlitemagic.sample.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.Menu;
import android.view.MenuItem;

import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.sample.R;
import com.siimkinks.sqlitemagic.sample.model.Item;
import com.siimkinks.sqlitemagic.sample.model.ItemList;
import com.siimkinks.sqlitemagic.sample.ui.helper.SimpleItemTouchHelperCallback;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;

import static com.siimkinks.sqlitemagic.ItemListTable.ITEM_LIST;
import static com.siimkinks.sqlitemagic.ItemTable.ITEM;
import static com.siimkinks.sqlitemagic.Select.OrderingTerm.by;

public class ListActivity extends AppCompatActivity {
	public static final String EXTRA_LIST = "list";

	@BindView(R.id.toolbar)
	Toolbar toolbar;
	@BindView(R.id.lists_view)
	RecyclerView recyclerView;

	private ItemsAdapter adapter;
	private CompositeSubscription subscriptions;

	private ItemList itemList;

	public static void launch(@NonNull Context context, @NonNull ItemList list) {
		final Intent intent = new Intent(context, ListActivity.class);
		intent.putExtra(EXTRA_LIST, list);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_list);
		ButterKnife.bind(this);
		this.itemList = getIntent().getParcelableExtra(EXTRA_LIST);
		setSupportActionBar(toolbar);
		setTitle(itemList.name());
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		setupRecyclerView();
		wireData();
	}

	private void setupRecyclerView() {
		final RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
		recyclerView.setLayoutManager(layoutManager);
		recyclerView.setHasFixedSize(true);
		adapter = new ItemsAdapter(getSupportFragmentManager());
		recyclerView.setAdapter(adapter);
		final SimpleItemTouchHelperCallback callback = new SimpleItemTouchHelperCallback();
		ItemTouchHelper itemTouchHelper = new ItemTouchHelper(callback);
		itemTouchHelper.attachToRecyclerView(recyclerView);
	}

	private void wireData() {
		subscriptions = new CompositeSubscription();
		subscriptions.add(Select
				.from(ITEM)
				.where(ITEM.LIST.is(itemList))
				.order(by(ITEM.COMPLETE, ITEM.DESCRIPTION))
				.observe()
				.runQuery()
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(adapter::setData));
		subscriptions.add(Observable.combineLatest(Select
						.from(ITEM_LIST)
						.where(ITEM_LIST.ID.is(itemList.id()))
						.takeFirst()
						.observe()
						.runQuery(),
				Item.countItemsFor(itemList)
						.runQuery(),
				(itemList, count) -> String.format("%s (%s)", itemList.name(), count))
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(this::setTitle));
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		subscriptions.unsubscribe();
	}

	@OnClick(R.id.fab)
	void onFabClick() {
		NewItemFragment
				.create(itemList)
				.show(getSupportFragmentManager(), "new-item");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_list, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_edit:
				editListName();
				return true;
			case R.id.action_delete:
				deleteList();
				return true;
			case android.R.id.home:
				finish();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void editListName() {
		final Long listId = itemList.id();
		if (listId != null) {
			EditListNameFragment
					.create(listId)
					.show(getSupportFragmentManager(), "edit-list");
		}
	}

	private void deleteList() {
		new AlertDialog.Builder(this)
				.setTitle(getString(R.string.delete_confirm_title, itemList.name()))
				.setPositiveButton(R.string.delete_action, (dialog, which) -> {
					itemList.delete()
							.observe()
							.subscribe(__ -> finish());
				})
				.setNegativeButton(R.string.cancel, null)
				.show();
	}
}
