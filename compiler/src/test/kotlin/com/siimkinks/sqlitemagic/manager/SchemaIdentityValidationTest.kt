package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import org.junit.jupiter.api.Test

internal class SchemaIdentityValidationTest {
  @Test
  fun `reports conflicts in traversal order and keeps the first owner`() {
    val conflicts = findSchemaIdentityConflicts(
      structures = listOf(
        "main" to DatabaseStructure(
          tables = mapOf(
            "Books" to TableStructure(name = "Books")
          ),
          indices = mapOf(
            "books" to IndexStructure(name = "books")
          ),
          temporaryTables = mapOf(
            "Cache" to TableStructure(name = "Cache")
          )
        ),
        "feature" to DatabaseStructure(
          tables = mapOf(
            "BOOKS" to TableStructure(name = "BOOKS")
          ),
          indices = mapOf(
            "BOOKS" to IndexStructure(name = "BOOKS")
          ),
          temporaryTables = mapOf(
            "CACHE" to TableStructure(name = "CACHE")
          ),
          temporaryIndices = mapOf(
            "cache" to IndexStructure(name = "cache")
          )
        )
      )
    )

    assertThat(conflicts).containsExactly(
      SchemaIdentityConflict(
        schema = MAIN,
        source = "main",
        objectKind = SchemaObjectKind.INDEX,
        name = "books",
        previousOwner = SchemaIdentityOwner(
          source = "main",
          objectKind = SchemaObjectKind.TABLE,
          name = "Books"
        )
      ),
      SchemaIdentityConflict(
        schema = MAIN,
        source = "feature",
        objectKind = SchemaObjectKind.TABLE,
        name = "BOOKS",
        previousOwner = SchemaIdentityOwner(
          source = "main",
          objectKind = SchemaObjectKind.TABLE,
          name = "Books"
        )
      ),
      SchemaIdentityConflict(
        schema = MAIN,
        source = "feature",
        objectKind = SchemaObjectKind.INDEX,
        name = "BOOKS",
        previousOwner = SchemaIdentityOwner(
          source = "main",
          objectKind = SchemaObjectKind.TABLE,
          name = "Books"
        )
      ),
      SchemaIdentityConflict(
        schema = TEMPORARY,
        source = "feature",
        objectKind = SchemaObjectKind.TABLE,
        name = "CACHE",
        previousOwner = SchemaIdentityOwner(
          source = "main",
          objectKind = SchemaObjectKind.TABLE,
          name = "Cache"
        )
      ),
      SchemaIdentityConflict(
        schema = TEMPORARY,
        source = "feature",
        objectKind = SchemaObjectKind.INDEX,
        name = "cache",
        previousOwner = SchemaIdentityOwner(
          source = "main",
          objectKind = SchemaObjectKind.TABLE,
          name = "Cache"
        )
      )
    ).inOrder()
  }

  @Test
  fun `validates views with tables and indexes while keeping namespaces separate`() {
    val conflicts = findSchemaIdentityConflicts(
      structures = listOf(
        "main" to DatabaseStructure(
          tables = mapOf(
            "Users" to TableStructure(name = "Users")
          ),
          indices = mapOf(
            "user_index" to IndexStructure(name = "user_index")
          ),
          views = mapOf(
            "USERS" to ViewStructure(name = "USERS"),
            "Cache" to ViewStructure(name = "Cache")
          ),
          temporaryTables = mapOf(
            "Cache" to TableStructure(name = "Cache")
          ),
          temporaryIndices = mapOf(
            "cache_index" to IndexStructure(name = "cache_index")
          ),
          temporaryViews = mapOf(
            "CACHE_INDEX" to ViewStructure(name = "CACHE_INDEX")
          )
        ),
        "feature" to DatabaseStructure(
          tables = mapOf(
            "users" to TableStructure(name = "users")
          ),
          indices = mapOf(
            "USERS" to IndexStructure(name = "USERS")
          ),
          views = mapOf(
            "USER_INDEX" to ViewStructure(name = "USER_INDEX")
          ),
          temporaryViews = mapOf(
            "cache" to ViewStructure(name = "cache")
          )
        )
      )
    )

    assertThat(conflicts).containsExactly(
      SchemaIdentityConflict(
        schema = MAIN,
        source = "main",
        objectKind = SchemaObjectKind.VIEW,
        name = "USERS",
        previousOwner = SchemaIdentityOwner(
          source = "main",
          objectKind = SchemaObjectKind.TABLE,
          name = "Users"
        )
      ),
      SchemaIdentityConflict(
        schema = TEMPORARY,
        source = "main",
        objectKind = SchemaObjectKind.VIEW,
        name = "CACHE_INDEX",
        previousOwner = SchemaIdentityOwner(
          source = "main",
          objectKind = SchemaObjectKind.INDEX,
          name = "cache_index"
        )
      ),
      SchemaIdentityConflict(
        schema = MAIN,
        source = "feature",
        objectKind = SchemaObjectKind.TABLE,
        name = "users",
        previousOwner = SchemaIdentityOwner(
          source = "main",
          objectKind = SchemaObjectKind.TABLE,
          name = "Users"
        )
      ),
      SchemaIdentityConflict(
        schema = MAIN,
        source = "feature",
        objectKind = SchemaObjectKind.INDEX,
        name = "USERS",
        previousOwner = SchemaIdentityOwner(
          source = "main",
          objectKind = SchemaObjectKind.TABLE,
          name = "Users"
        )
      ),
      SchemaIdentityConflict(
        schema = MAIN,
        source = "feature",
        objectKind = SchemaObjectKind.VIEW,
        name = "USER_INDEX",
        previousOwner = SchemaIdentityOwner(
          source = "main",
          objectKind = SchemaObjectKind.INDEX,
          name = "user_index"
        )
      ),
      SchemaIdentityConflict(
        schema = TEMPORARY,
        source = "feature",
        objectKind = SchemaObjectKind.VIEW,
        name = "cache",
        previousOwner = SchemaIdentityOwner(
          source = "main",
          objectKind = SchemaObjectKind.TABLE,
          name = "Cache"
        )
      )
    ).inOrder()
  }
}
