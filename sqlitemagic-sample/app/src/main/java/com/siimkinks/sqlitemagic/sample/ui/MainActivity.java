package com.siimkinks.sqlitemagic.sample.ui;

import android.os.Bundle;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.sample.R;
import com.siimkinks.sqlitemagic.sample.model.ItemList;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;

import static com.siimkinks.sqlitemagic.ItemListSummaryTable.ITEM_LIST_SUMMARY;

public class MainActivity extends AppCompatActivity {
  @BindView(R.id.toolbar)
  Toolbar toolbar;
  @BindView(R.id.toolbar_layout)
  CollapsingToolbarLayout toolbarLayout;
  @BindView(R.id.lists_view)
  RecyclerView recyclerView;

  private ListsAdapter adapter;

  private CompositeSubscription subscriptions;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    ButterKnife.bind(this);
    setSupportActionBar(toolbar);
    setupRecyclerView();
    wireData();
  }

  private void setupRecyclerView() {
    final RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
    recyclerView.setLayoutManager(layoutManager);
    adapter = new ListsAdapter();
    recyclerView.setAdapter(adapter);
  }

  private void wireData() {
    subscriptions = new CompositeSubscription();
    subscriptions.add(ItemList.COUNT
        .observe()
        .runQuery()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(count -> toolbarLayout.setTitle(getString(R.string.item_lists_title, count))));
    subscriptions.add(Select
        .from(ITEM_LIST_SUMMARY)
        .observe()
        .runQuery()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(adapter::setData));
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    subscriptions.unsubscribe();
  }

  @OnClick(R.id.fab)
  void onFabClick() {
    NewListFragment
        .create()
        .show(getSupportFragmentManager(), "new-list");
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.action_add_from_network) {
      ItemList.persist(ItemList.createRandom(3))
          .observe()
          .subscribe();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }
}
