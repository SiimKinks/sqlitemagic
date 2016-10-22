package com.siimkinks.sqlitemagic.sample.ui;

import android.support.annotation.NonNull;
import android.widget.EditText;

import com.jakewharton.rxbinding.widget.RxTextView;
import com.siimkinks.sqlitemagic.sample.R;
import com.siimkinks.sqlitemagic.sample.model.ItemList;

import rx.Observable;
import rx.schedulers.Schedulers;

public final class NewListFragment extends CreateNewFragment {
  public static NewListFragment create() {
    return new NewListFragment();
  }

  @Override
  void observeCreateAction(@NonNull EditText inputView, @NonNull Observable<String> createClicked) {
    Observable.combineLatest(
        createClicked,
        RxTextView.textChanges(inputView),
        (__, text) -> text.toString())
        .observeOn(Schedulers.io())
        .flatMap(name -> ItemList.builder()
            .name(name)
            .archived(false)
            .build()
            .persist()
            .observe()
            .toObservable())
        .first()
        .subscribe();
  }

  @Override
  int layoutResId() {
    return R.layout.new_list;
  }
}

