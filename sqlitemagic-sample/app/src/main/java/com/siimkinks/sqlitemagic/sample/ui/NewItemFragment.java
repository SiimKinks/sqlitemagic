package com.siimkinks.sqlitemagic.sample.ui;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.widget.EditText;

import com.jakewharton.rxbinding.widget.RxTextView;
import com.siimkinks.sqlitemagic.sample.R;
import com.siimkinks.sqlitemagic.sample.model.Item;
import com.siimkinks.sqlitemagic.sample.model.ItemList;

import rx.Observable;
import rx.schedulers.Schedulers;

import static com.siimkinks.sqlitemagic.sample.ui.ListActivity.EXTRA_LIST;

public final class NewItemFragment extends CreateNewFragment {
  private ItemList itemList;

  public static NewItemFragment create(@NonNull ItemList itemList) {
    final NewItemFragment fragment = new NewItemFragment();
    final Bundle args = new Bundle();
    args.putParcelable(EXTRA_LIST, itemList);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.itemList = getArguments().getParcelable(EXTRA_LIST);
  }

  @Override
  void observeCreateAction(@NonNull EditText inputView, @NonNull Observable<String> createClicked) {
    Observable.combineLatest(
        createClicked,
        RxTextView.textChanges(inputView),
        (__, text) -> text.toString())
        .observeOn(Schedulers.io())
        .flatMap(description -> Item.builder()
            .list(itemList)
            .description(description)
            .complete(false)
            .build()
            .persist()
            .observe()
            .toObservable())
        .first()
        .subscribe();
  }

  @Override
  int layoutResId() {
    return R.layout.new_item;
  }
}
