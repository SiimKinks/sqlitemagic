package com.siimkinks.sqlitemagic

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Database
import com.siimkinks.sqlitemagic.annotation.Index
import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

class SqliteMagicSymbolProcessor(
  private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {
  override fun process(resolver: Resolver): List<KSAnnotated> {
    if (environment.options[OPTION_DEBUG] == "true") {
      environment.logger.warn("SqliteMagic KSP debug mode enabled")
    }
    SUPPORTED_ANNOTATIONS.forEach { annotationName ->
      resolver.getSymbolsWithAnnotation(annotationName)
    }
    return emptyList()
  }

  companion object {
    const val OPTION_DEBUG = "sqlitemagic.ksp.debug"

    val SUPPORTED_ANNOTATIONS = setOf(
        Database::class.java.canonicalName,
        SubmoduleDatabase::class.java.canonicalName,
        Table::class.java.canonicalName,
        Column::class.java.canonicalName,
        ObjectToDbValue::class.java.canonicalName,
        DbValueToObject::class.java.canonicalName,
        View::class.java.canonicalName,
        Index::class.java.canonicalName
    )
  }
}
