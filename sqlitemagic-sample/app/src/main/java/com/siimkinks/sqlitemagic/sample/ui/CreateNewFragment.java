package com.siimkinks.sqlitemagic.sample.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import com.siimkinks.sqlitemagic.sample.R;

import rx.Observable;
import rx.subjects.PublishSubject;

import static butterknife.ButterKnife.findById;

public abstract class CreateNewFragment extends DialogFragment {
  private final PublishSubject<String> createClicked = PublishSubject.create();

  abstract void observeCreateAction(@NonNull EditText inputView, @NonNull Observable<String> createClicked);

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    final Context context = getActivity();
    final View view = LayoutInflater.from(context).inflate(layoutResId(), null);
    final EditText inputView = findById(view, android.R.id.input);
    observeCreateAction(inputView, createClicked);
    setCancelable(false);

    return new AlertDialog.Builder(context)
        .setTitle(titleStringResId())
        .setView(view)
        .setCancelable(false)
        .setPositiveButton(actionStringResId(), (d, which) -> {
          createClicked.onNext("clicked");
        })
        .setNegativeButton(R.string.cancel, (d, which) -> {
        })
        .setOnKeyListener((arg0, keyCode, event) -> {
          if (keyCode == KeyEvent.KEYCODE_BACK) {
            dismiss();
            return true;
          }
          return false;
        })
        .create();
  }

  protected int actionStringResId() {
    return R.string.create;
  }

  protected int titleStringResId() {
    return R.string.new_item;
  }

  @LayoutRes
  abstract int layoutResId();

  @Override
  public void onResume() {
    super.onResume();
    getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
  }
}
