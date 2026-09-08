package com.siimkinks.sqlitemagic.index

import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.tschuchort.compiletesting.SourceFile

internal fun validIndexSources() = SourceFile.kotlin(
  name = "ValidIndexes.kt",
  contents = """
    package $PACKAGE

    import com.siimkinks.sqlitemagic.annotation.Column
    import com.siimkinks.sqlitemagic.annotation.Embedded
    import com.siimkinks.sqlitemagic.annotation.Id
    import com.siimkinks.sqlitemagic.annotation.Index
    import com.siimkinks.sqlitemagic.annotation.Table
    import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY

    @Index(value = "book_lookup", unique = true)
    @Table("books")
    data class Book(
      @Id(autoIncrement = false) val id: Long,
      @Index val title: String,
      @Index(value = "book_isbn", unique = true) val isbn: String,
      @Index
      @Column(belongsToIndex = "book_lookup")
      val author: String,
      @Column(belongsToIndex = "book_lookup") val edition: Int
    )

    @Index
    @Table("unnamed_composites")
    data class UnnamedComposite(
      @Id(autoIncrement = false) val id: String,
      val firstPart: String,
      val secondPart: Long
    )

    @Index("selective_lookup")
    @Table(value = "selective_entries", persistAll = false)
    class SelectiveEntry {
      @Index(value = "selective_value", unique = true)
      @Column(belongsToIndex = "selective_lookup")
      var persistedValue: String = ""

      var excludedValue: String = ""
    }

    open class IndexedBase {
      @Index("inherited_value")
      var inheritedValue: String = ""
    }

    @Table("inherited_entries")
    class InheritedEntry : IndexedBase() {
      @Index("local_value")
      var localValue: Long = 0
    }

    data class AddressLeaf(
      @Index val city: String,
      val country: String
    )

    data class Address(
      @Embedded(prefix = "geo_") val geo: AddressLeaf
    )

    @Table("profiles")
    data class Profile(
      @Id(autoIncrement = false) val id: String,
      @Embedded(prefix = "home_") val home: Address,
      @Embedded(prefix = "work_") val work: Address
    )

    @Table(value = "temporary_entries", options = [TEMPORARY])
    data class TemporaryEntry(
      @Id(autoIncrement = false) val id: String,
      @Index("temporary_value") val value: String
    )

    @Table("no_id_entries")
    data class NoIdEntry(
      @Index("no_id_value") val value: String
    )

    @Table("string_id_entries")
    data class StringIdEntry(
      @Id(autoIncrement = false) val id: String,
      @Index(value = "string_value", unique = true) val value: String
    )
  """
)

internal fun invalidIndexSource(
  indexDeclaration: String,
  tableDeclaration: String = "@Table(\"invalid_indexes\")"
) = SourceFile.kotlin(
  name = "InvalidIndex.kt",
  contents = """
    package $PACKAGE

    import com.siimkinks.sqlitemagic.annotation.Column
    import com.siimkinks.sqlitemagic.annotation.Index
    import com.siimkinks.sqlitemagic.annotation.Table

    $indexDeclaration
    $tableDeclaration
    data class InvalidIndex(
      @Column(belongsToIndex = "declared_lookup") val value: String,
      val secondValue: String
    )
  """
)
