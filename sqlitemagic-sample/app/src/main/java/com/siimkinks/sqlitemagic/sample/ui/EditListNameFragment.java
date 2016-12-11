package com.siimkinks.sqlitemagic.sample.ui;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.widget.EditText;

import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.Update;
import com.siimkinks.sqlitemagic.sample.R;

import rx.Observable;
import rx.schedulers.Schedulers;

import static com.siimkinks.sqlitemagic.ItemListTable.ITEM_LIST;

public final class EditListNameFragment extends CreateNewFragment {
  private static final String EXTRA_LIST_ID = "list_id";

  public static EditListNameFragment create(long listId) {
    final EditListNameFragment fragment = new EditListNameFragment();
    final Bundle extras = new Bundle();
    extras.putLong(EXTRA_LIST_ID, listId);
    fragment.setArguments(extras);
    return fragment;
  }

  @Override
  protected void observeValidCreate(@NonNull EditText inputView, @NonNull Observable<String> createStream) {
    final long listId = getArguments().getLong(EXTRA_LIST_ID);
    final String currentListName = Select.column(ITEM_LIST.NAME)
        .from(ITEM_LIST)
        .where(ITEM_LIST.ID.is(listId))
        .takeFirst()
        .execute();
    inputView.setText(currentListName);
    inputView.setSelection(currentListName.length());
    createStream.observeOn(Schedulers.io())
        .flatMap(name -> Update
            .table(ITEM_LIST)
            .set(ITEM_LIST.NAME, name)
            .where(ITEM_LIST.ID.is(listId))
            .observe()
            .toObservable())
        .first()
        .subscribe();
  }

  protected int actionStringResId() {
    return R.string.edit_action;
  }

  @Override
  protected int titleStringResId() {
    return R.string.edit_list_name;
  }

  @Override
  int layoutResId() {
    return R.layout.new_list;
  }
}

