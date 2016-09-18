package com.siimkinks.sqlitemagic.sample.ui;

import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentManager;
import android.text.SpannableString;
import android.text.style.StrikethroughSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;

import com.siimkinks.sqlitemagic.Update;
import com.siimkinks.sqlitemagic.sample.R;
import com.siimkinks.sqlitemagic.sample.model.Item;

import static com.siimkinks.sqlitemagic.ItemTable.ITEM;

public final class ItemsAdapter extends BaseListAdapter<Item> {
	private final FragmentManager fragmentManager;

	public ItemsAdapter(FragmentManager fragmentManager) {
		this.fragmentManager = fragmentManager;
	}

	@Override
	public ItemsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		final View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_multiple_choice, parent, false);
		return new ViewHolder(v, fragmentManager);
	}

	public static final class ViewHolder extends BaseListAdapter.ViewHolder<Item> {
		Item item;

		public ViewHolder(View v, FragmentManager fragmentManager) {
			super(v);
			v.setOnClickListener(view -> {
				boolean newValue = !item.complete();
				Update.table(ITEM)
						.set(ITEM.COMPLETE, newValue)
						.where(ITEM.ID.is(item.id()))
						.observe()
						.subscribe();
			});
			v.setOnLongClickListener(view -> {
				EditItemNameFragment
						.create(item.id())
						.show(fragmentManager, "edit-item");
				return true;
			});
		}

		@Override
		void setItem(@NonNull Item item) {
			this.item = item;
			final CheckedTextView textView = (CheckedTextView) itemView;
			final boolean complete = item.complete();
			textView.setChecked(complete);
			CharSequence description = item.description();
			if (complete) {
				SpannableString spannable = new SpannableString(description);
				spannable.setSpan(new StrikethroughSpan(), 0, description.length(), 0);
				description = spannable;
			}
			textView.setText(description);
		}

		@Override
		public void deleteItem() {
			final Item deletedItem = this.item;
			deletedItem.delete()
					.observe()
					.subscribe(deleteCount -> {
						Snackbar.make(itemView, R.string.delete_success, Snackbar.LENGTH_LONG)
								.setAction(R.string.delete_undo, v -> {
									deletedItem.persist()
											.observe()
											.subscribe();
								})
								.show();
					});
		}
	}
}
