package com.siimkinks.sqlitemagic.sample.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.DialogFragment;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import com.jakewharton.rxbinding.widget.RxTextView;
import com.siimkinks.sqlitemagic.sample.R;

import rx.Observable;
import rx.subjects.PublishSubject;

import static android.R.id.input;
import static butterknife.ButterKnife.findById;

public abstract class CreateNewFragment extends DialogFragment {
  private final PublishSubject<String> createClicked = PublishSubject.create();

  protected abstract void observeValidCreate(@NonNull EditText inputView, @NonNull Observable<String> createStream);

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    final Context context = getActivity();
    final View view = LayoutInflater.from(context).inflate(layoutResId(), null);
    final EditText inputView = findById(view, input);
    final TextInputLayout inputLayout = findById(view, R.id.input_layout);
    final Observable<String> createStream = RxTextView
        .textChanges(inputView)
        .map(rawIn -> {
          final String input = rawIn.toString().trim();
          return Pair.create(input, isInputValid(input));
        })
        .doOnNext(input -> {
          if (input.second) inputLayout.setErrorEnabled(false);
        })
        .sample(createClicked)
        .doOnNext(input -> {
          if (!input.second) inputLayout.setError(getString(R.string.error_input_invalid));
        })
        .skipWhile(input -> !input.second)
        .take(1)
        .map(input -> {
          final Dialog dialog = getDialog();
          if (dialog != null) {
            dialog.dismiss();
          }
          return input.first;
        });

    observeValidCreate(inputView, createStream);
    setCancelable(false);

    return new AlertDialog.Builder(context)
        .setTitle(titleStringResId())
        .setView(view)
        .setCancelable(false)
        .setPositiveButton(actionStringResId(), (d, which) -> {})
        .setNegativeButton(R.string.cancel, (d, which) -> {})
        .setOnKeyListener((arg0, keyCode, event) -> {
          if (keyCode == KeyEvent.KEYCODE_BACK) {
            dismiss();
            return true;
          }
          return false;
        })
        .create();
  }

  @Override
  public void onStart() {
    super.onStart();
    final AlertDialog dialog = (AlertDialog) getDialog();
    if (dialog != null) {
      final Button positiveButton = dialog.getButton(Dialog.BUTTON_POSITIVE);
      if (positiveButton != null) {
        positiveButton.setOnClickListener(view -> {
          // ignore system click logic -- also skips automatic dismisses
          createClicked.onNext("clicked");
        });
      }
    }
  }

  static boolean isInputValid(@NonNull String input) {
    return !input.isEmpty();
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
