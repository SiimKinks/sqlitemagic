package com.siimkinks.sqlitemagic.model.view

import com.siimkinks.sqlitemagic.AS
import com.siimkinks.sqlitemagic.AuthorTable.AUTHOR
import com.siimkinks.sqlitemagic.COLUMNS
import com.siimkinks.sqlitemagic.FROM
import com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewQuery
import com.siimkinks.sqlitemagic.model.Author

@View
data class DataClassView(
    @ViewColumn("an")
    val authorName: String,
    @ViewColumn("magazine.name")
    val magazineName: String,
    @ViewColumn("author")
    val author: Author
) {
  companion object {
    @JvmField
    @ViewQuery
    val QUERY = (SELECT
        COLUMNS
        arrayOf(
            AUTHOR.NAME AS "an",
            MAGAZINE.NAME,
            AUTHOR.all())
        FROM MAGAZINE)
        .queryDeep()
        .compile()
  }
}
