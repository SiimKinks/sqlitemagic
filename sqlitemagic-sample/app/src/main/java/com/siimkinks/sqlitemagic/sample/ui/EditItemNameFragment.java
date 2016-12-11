package com.siimkinks.sqlitemagic.sample.ui;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.widget.EditText;

import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.Update;
import com.siimkinks.sqlitemagic.sample.R;

import rx.Observable;
import rx.schedulers.Schedulers;

import static com.siimkinks.sqlitemagic.ItemTable.ITEM;

public final class EditItemNameFragment extends CreateNewFragment {
  private static final String EXTRA_ITEM_ID = "item_id";

  public static EditItemNameFragment create(long itemId) {
    final EditItemNameFragment fragment = new EditItemNameFragment();
    final Bundle extras = new Bundle();
    extras.putLong(EXTRA_ITEM_ID, itemId);
    fragment.setArguments(extras);
    return fragment;
  }

  @Override
  protected void observeValidCreate(@NonNull EditText inputView, @NonNull Observable<String> createStream) {
    final long itemId = getArguments().getLong(EXTRA_ITEM_ID);
    final String currentItemName = Select
        .column(ITEM.DESCRIPTION)
        .from(ITEM)
        .where(ITEM.ID.is(itemId))
        .takeFirst()
        .execute();
    inputView.setText(currentItemName);
    inputView.setSelection(currentItemName.length());
    createStream.observeOn(Schedulers.io())
        .flatMap(name -> Update
            .table(ITEM)
            .set(ITEM.DESCRIPTION, name)
            .where(ITEM.ID.is(itemId))
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
    return R.string.edit_item;
  }

  @Override
  int layoutResId() {
    return R.layout.new_list;
  }
}

