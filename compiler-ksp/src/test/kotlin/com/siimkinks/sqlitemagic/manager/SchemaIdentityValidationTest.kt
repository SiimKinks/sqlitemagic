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
}
