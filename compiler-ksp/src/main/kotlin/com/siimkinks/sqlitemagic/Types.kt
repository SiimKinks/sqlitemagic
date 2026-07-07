package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Database
import com.siimkinks.sqlitemagic.annotation.Index
import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

object Types {
  val DATABASE_ANNOTATION = Database::class.qualifiedName!!
  val SUBMODULE_DATABASE_ANNOTATION = SubmoduleDatabase::class.qualifiedName!!
  val TABLE_ANNOTATION = Table::class.qualifiedName!!
  val COLUMN_ANNOTATION = Column::class.qualifiedName!!
  val OBJECT_TO_DB_VALUE_ANNOTATION = ObjectToDbValue::class.qualifiedName!!
  val DB_VALUE_TO_OBJECT_ANNOTATION = DbValueToObject::class.qualifiedName!!
  val VIEW_ANNOTATION = View::class.qualifiedName!!
  val INDEX_ANNOTATION = Index::class.qualifiedName!!
}
