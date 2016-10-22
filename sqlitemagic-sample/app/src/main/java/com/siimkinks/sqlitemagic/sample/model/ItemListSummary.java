package com.siimkinks.sqlitemagic.sample.model;

import com.siimkinks.sqlitemagic.CompiledSelect;
import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.annotation.View;
import com.siimkinks.sqlitemagic.annotation.ViewColumn;
import com.siimkinks.sqlitemagic.annotation.ViewQuery;

import static com.siimkinks.sqlitemagic.ItemListTable.ITEM_LIST;
import static com.siimkinks.sqlitemagic.ItemTable.ITEM;
import static com.siimkinks.sqlitemagic.Select.OrderingTerm.by;
import static com.siimkinks.sqlitemagic.Select.count;
import static com.siimkinks.sqlitemagic.Select.val;

@View
public interface ItemListSummary {
  @ViewQuery
  CompiledSelect QUERY = Select
      .columns(
          ITEM_LIST.all().as("item_list"),
          Select.column(count())
              .from(ITEM)
              .where(ITEM.COMPLETE.is(val(false))
                  .and(ITEM.LIST.is(ITEM_LIST.ID)))
              .asColumn("count"))
      .from(ITEM_LIST)
      .order(by(ITEM_LIST.NAME))
      .compile();

  @ViewColumn("item_list")
  ItemList itemList();

  @ViewColumn("count")
  long itemsCount();
}
