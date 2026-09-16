package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.schema.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.schema.SqliteSchema.TEMPORARY
import org.junit.jupiter.api.Test

internal class SchemaIdentityValidationTest {
  @Test
  fun `reports conflicts in traversal order and keeps the first owner`() {
    val conflicts = findSchemaIdentityConflicts(
      structures = listOf(
        "main" to DatabaseStructure(
          tables = linkedMapOf(
            "Books" to TableStructure(name = "Books")
          ),
          indices = linkedMapOf(
            "books" to IndexStructure(name = "books")
          ),
          temporaryTables = linkedMapOf(
            "Cache" to TableStructure(name = "Cache")
          )
        ),
        "feature" to DatabaseStructure(
          tables = linkedMapOf(
            "BOOKS" to TableStructure(name = "BOOKS")
          ),
          indices = linkedMapOf(
            "BOOKS" to IndexStructure(name = "BOOKS")
          ),
          temporaryTables = linkedMapOf(
            "CACHE" to TableStructure(name = "CACHE")
          ),
          temporaryIndices = linkedMapOf(
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
          tables = linkedMapOf(
            "Users" to TableStructure(name = "Users")
          ),
          indices = linkedMapOf(
            "user_index" to IndexStructure(name = "user_index")
          ),
          views = linkedMapOf(
            "USERS" to ViewStructure(name = "USERS"),
            "Cache" to ViewStructure(name = "Cache")
          ),
          temporaryTables = linkedMapOf(
            "Cache" to TableStructure(name = "Cache")
          ),
          temporaryIndices = linkedMapOf(
            "cache_index" to IndexStructure(name = "cache_index")
          ),
          temporaryViews = linkedMapOf(
            "CACHE_INDEX" to ViewStructure(name = "CACHE_INDEX")
          )
        ),
        "feature" to DatabaseStructure(
          tables = linkedMapOf(
            "users" to TableStructure(name = "users")
          ),
          indices = linkedMapOf(
            "USERS" to IndexStructure(name = "USERS")
          ),
          views = linkedMapOf(
            "USER_INDEX" to ViewStructure(name = "USER_INDEX")
          ),
          temporaryViews = linkedMapOf(
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
