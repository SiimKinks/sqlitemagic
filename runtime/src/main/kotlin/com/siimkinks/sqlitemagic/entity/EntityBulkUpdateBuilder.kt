package com.siimkinks.sqlitemagic.entity

import android.database.SQLException

/**
 * Builder for bulk update operation.
 */
interface EntityBulkUpdateBuilder :
  EntityOperationBuilder<EntityBulkUpdateBuilder>,
  EntityOperationByColumnBuilder<EntityBulkUpdateBuilder> {
  /**
   * Execute this configured bulk update operation against a database.
   * Non-recursive operations and recursive operations without `CONFLICT_IGNORE` execute inside one transaction.
   * Recursive operations using `CONFLICT_IGNORE` execute each entity graph in its own transaction.
   *
   * @return `true` if at least one entity graph committed; `false` when no entity graph committed.
   *     With `CONFLICT_IGNORE`, ignored recursive graphs are rolled back independently and successful graphs remain
   *     committed.
   * @throws SQLException when SQLite rejects a write, for example on a constraint violation; the operation rolls
   *     back as above
   */
  fun execute(): Boolean
}
