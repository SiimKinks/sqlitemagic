package com.siimkinks.sqlitemagic.sample.ui.helper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.View;

import com.siimkinks.sqlitemagic.sample.ui.BaseListAdapter;

public class SimpleItemTouchHelperCallback extends ItemTouchHelper.Callback {
	private static final float ALPHA_FULL = 1.0f;

	@Override
	public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
		return makeMovementFlags(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
	}

	@Override
	public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
		return false;
	}

	@Override
	public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
		((BaseListAdapter.ViewHolder) viewHolder).deleteItem();
	}

	@Override
	public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
		// We only want the active item to change
		if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
			final View itemView = viewHolder.itemView;
			itemView.setBackgroundColor(Color.LTGRAY);
		}

		super.onSelectedChanged(viewHolder, actionState);
	}

	@Override
	public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
		if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
			// Fade out the view as it is swiped out of the parent's bounds
			final float alpha = ALPHA_FULL - Math.abs(dX) / (float) viewHolder.itemView.getWidth();
			final View itemView = viewHolder.itemView;
			itemView.setAlpha(alpha);
			itemView.setTranslationX(dX);
		} else {
			super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
		}
	}

	@Override
	public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
		super.clearView(recyclerView, viewHolder);

		final View itemView = viewHolder.itemView;
		itemView.setAlpha(ALPHA_FULL);
		itemView.setBackgroundColor(0);
	}
}
