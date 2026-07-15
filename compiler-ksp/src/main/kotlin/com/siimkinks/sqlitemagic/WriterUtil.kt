package com.siimkinks.sqlitemagic

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.asClassName

object WriterUtil {
  const val VALUE_VARIABLE = "value"
  const val SQL_VALUE_VARIABLE = "sqlValue"
  const val DB_VALUE_VARIABLE = "dbValue"

  val UNCHECKED_CAST = AnnotationSpec
    .builder(Suppress::class)
    .addMember("%S", "UNCHECKED_CAST")
    .build()

  val TABLE = Table::class.asClassName()
  val COLUMN = Column::class.asClassName()
  val NUMERIC_COLUMN = NumericColumn::class.asClassName()
  val COMPLEX_COLUMN = ComplexColumn::class.asClassName()
  val UNIQUE = Unique::class.asClassName()
  val CURSOR = ClassName("android.database", "Cursor")
  val SUPPORT_SQLITE_STATEMENT = ClassName("androidx.sqlite.db", "SupportSQLiteStatement")
  val VALUE_PARSER = Utils::class
    .asClassName()
    .nestedClass("ValueParser")
    .parameterizedBy(STAR)
}
