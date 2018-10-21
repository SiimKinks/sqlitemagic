package com.siimkinks.sqlitemagic.sample.ui;

import android.support.annotation.NonNull;
import android.widget.EditText;

import com.siimkinks.sqlitemagic.sample.R;
import com.siimkinks.sqlitemagic.sample.model.ItemList;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;

public final class NewListFragment extends CreateNewFragment {
  public static NewListFragment create() {
    return new NewListFragment();
  }

  @Override
  protected void observeValidCreate(@NonNull EditText inputView, @NonNull Observable<String> createStream) {
    createStream.observeOn(Schedulers.io())
        .flatMap(name -> ItemList.builder()
            .name(name)
            .archived(false)
            .build()
            .persist()
            .observe()
            .toObservable())
        .firstOrError()
        .subscribe();
  }

  @Override
  int layoutResId() {
    return R.layout.new_list;
  }
}

