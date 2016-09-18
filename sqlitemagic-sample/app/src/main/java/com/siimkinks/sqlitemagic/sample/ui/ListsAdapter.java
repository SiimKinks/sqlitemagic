package com.siimkinks.sqlitemagic.sample.ui;

import android.app.AlertDialog;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.siimkinks.sqlitemagic.sample.R;
import com.siimkinks.sqlitemagic.sample.model.ItemList;
import com.siimkinks.sqlitemagic.sample.model.ItemListSummary;

import butterknife.BindView;

public final class ListsAdapter extends BaseListAdapter<ItemListSummary> {
	@Override
	public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		final View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_view, parent, false);
		return new ViewHolder(v);
	}

	public static final class ViewHolder extends BaseListAdapter.ViewHolder<ItemListSummary> {
		@BindView(R.id.list_view_text)
		TextView textView;
		ItemListSummary item;

		public ViewHolder(View v) {
			super(v);
			v.setOnClickListener(view -> ListActivity.launch(view.getContext(), item.itemList()));
			v.setOnLongClickListener(view -> {
				new AlertDialog.Builder(view.getContext())
						.setTitle(view.getResources().getString(R.string.delete_confirm_title, item.itemList().name()))
						.setPositiveButton(R.string.delete_action, (dialog, which) -> {
							deleteItem();
						})
						.setNegativeButton(R.string.cancel, null)
						.show();
				return true;
			});
		}

		@Override
		void setItem(@NonNull ItemListSummary item) {
			this.item = item;
			textView.setText(String.format("%s (%s)", item.itemList().name(), item.itemsCount()));
		}

		@Override
		public void deleteItem() {
			final ItemList deletedItem = this.item.itemList();
			deletedItem.delete()
					.observe()
					.subscribe(deleteCount -> {
						Snackbar.make(itemView, R.string.delete_success, Snackbar.LENGTH_LONG)
								.show();
					});
		}
	}
}
