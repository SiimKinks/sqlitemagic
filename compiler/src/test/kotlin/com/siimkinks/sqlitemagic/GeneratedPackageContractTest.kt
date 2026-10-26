package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.isOk
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class GeneratedPackageContractTest {
  @Test
  fun `every generated file lives in the root package whatever the models' package`() {
    val result = SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "FeatureModels.kt",
          contents = """
            package fixtures.feature

            import com.siimkinks.sqlitemagic.AuthorTable.Companion.AUTHOR
            import com.siimkinks.sqlitemagic.SELECT
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery
            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

            data class Email(val value: String)

            object EmailTransformer {
              @ObjectToDbValue
              fun serialize(value: Email): String = value.value

              @DbValueToObject
              fun deserialize(value: String): Email = Email(value)
            }

            @Table
            data class Author(
              @Id val id: Long? = null,
              @Index val name: String,
              val email: Email
            )

            @Table
            data class Book(
              @Id val id: Long? = null,
              val author: Author
            )

            @View("author_name")
            data class AuthorName(
              @ViewColumn("author.name") val name: String
            ) {
              companion object {
                @ViewQuery
                val QUERY = SELECT FROM AUTHOR
              }
            }
          """
        ),
        processingStepsFactory = null
      )
      .isOk()

    val packages = result.generatedSourceNames().associateWith { fileName ->
      result
        .generatedSource(fileName)
        .lineSequence()
        .first { it.startsWith("package ") }
    }
    assertThat(packages).containsExactly(
      "AuthorNameTable.kt", "package com.siimkinks.sqlitemagic",
      "AuthorTable.kt", "package com.siimkinks.sqlitemagic",
      "BookTable.kt", "package com.siimkinks.sqlitemagic",
      "EmailSqlType.kt", "package com.siimkinks.sqlitemagic",
      "SqliteMagicDatabase.kt", "package com.siimkinks.sqlitemagic",
      "SqliteMagic_AuthorName_Dao.kt", "package com.siimkinks.sqlitemagic",
      "SqliteMagic_Author_Adapter.kt", "package com.siimkinks.sqlitemagic",
      "SqliteMagic_Author_Dao.kt", "package com.siimkinks.sqlitemagic",
      "SqliteMagic_Book_Adapter.kt", "package com.siimkinks.sqlitemagic",
      "SqliteMagic_Book_Dao.kt", "package com.siimkinks.sqlitemagic",
      "_Author.kt", "package com.siimkinks.sqlitemagic",
      "_Book.kt", "package com.siimkinks.sqlitemagic"
    )
  }
}
