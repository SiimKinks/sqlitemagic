package com.siimkinks.sqlitemagic.sample.model;

import com.siimkinks.sqlitemagic.CompiledSelect;
import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.annotation.View;
import com.siimkinks.sqlitemagic.annotation.ViewColumn;
import com.siimkinks.sqlitemagic.annotation.ViewQuery;

import static com.siimkinks.sqlitemagic.ItemListTable.ITEM_LIST;
import static com.siimkinks.sqlitemagic.ItemTable.ITEM;
import static com.siimkinks.sqlitemagic.Select.asColumn;
import static com.siimkinks.sqlitemagic.Select.count;

@View
public interface ItemListSummary {
  @ViewQuery
  CompiledSelect QUERY = Select
      .columns(
          ITEM_LIST.all().as("item_list"),
          Select.column(count())
              .from(ITEM)
              .where(ITEM.COMPLETE.is(asColumn(false))
                  .and(ITEM.LIST.is(ITEM_LIST.ID)))
              .toColumn("count"))
      .from(ITEM_LIST)
      .orderBy(ITEM_LIST.NAME.asc())
      .compile();

  @ViewColumn("item_list")
  ItemList itemList();

  @ViewColumn("count")
  long itemsCount();
}
