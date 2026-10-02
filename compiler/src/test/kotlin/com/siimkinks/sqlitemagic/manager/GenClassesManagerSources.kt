package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.tschuchort.compiletesting.SourceFile

internal fun schemaTables() = SourceFile.kotlin(
  name = "SchemaTables.kt",
  contents = """
    package $PACKAGE

    import com.siimkinks.sqlitemagic.annotation.Column
    import com.siimkinks.sqlitemagic.annotation.Id
    import com.siimkinks.sqlitemagic.annotation.Table
    import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY

    @Table("children")
    data class Child(
      @Id(autoIncrement = false) val id: String,
      @Column(handleRecursively = true, onDeleteCascade = true) val parent: Parent
    )

    @Table("parents")
    data class Parent(
      @Id(autoIncrement = false) val id: String
    )

    @Table(value = "session_cache", options = [TEMPORARY])
    data class SessionCache(
      @Id(autoIncrement = false) val key: String,
      @Column(handleRecursively = true) val owner: SessionOwner,
      val value: String
    )

    @Table(value = "session_owners", options = [TEMPORARY])
    data class SessionOwner(
      @Id(autoIncrement = false) val key: String
    )
  """
)

internal fun submoduleDatabase() = SourceFile.kotlin(
  name = "FeatureDatabase.kt",
  contents = """
    package $PACKAGE

    import com.siimkinks.sqlitemagic.annotation.Id
    import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase
    import com.siimkinks.sqlitemagic.annotation.Table

    @SubmoduleDatabase("feature")
    class FeatureDatabase

    @Table("feature_items")
    data class FeatureItem(
      @Id val id: Long,
      val name: String
    )
  """
)
