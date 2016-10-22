package com.siimkinks.sqlitemagic.sample.ui;

import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;

public abstract class BaseListAdapter<T> extends RecyclerView.Adapter<BaseListAdapter.ViewHolder<T>> {
  private List<T> items = new ArrayList<>();

  @MainThread
  public final void setData(@NonNull List<T> itemLists) {
    this.items = itemLists;
    notifyDataSetChanged();
  }

  @Override
  public final void onBindViewHolder(ViewHolder<T> holder, int position) {
    holder.setItem(items.get(position));
  }

  @Override
  public final int getItemCount() {
    return items.size();
  }

  public abstract static class ViewHolder<T> extends RecyclerView.ViewHolder {
    public ViewHolder(View itemView) {
      super(itemView);
      ButterKnife.bind(this, itemView);
    }

    abstract void setItem(@NonNull T t);

    public abstract void deleteItem();
  }
}
