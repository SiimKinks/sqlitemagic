package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.BaseProcessor.PUBLIC_EXTENSIONS
import com.siimkinks.sqlitemagic.entity.*
import com.siimkinks.sqlitemagic.util.NameConst.PACKAGE_ROOT
import com.siimkinks.sqlitemagic.writer.EntityEnvironment
import com.squareup.kotlinpoet.*
import java.io.File
import javax.lang.model.element.TypeElement
import com.squareup.javapoet.ClassName as JavaClassName
import com.squareup.javapoet.TypeName as JavaTypeName

val SUPPRESS = Suppress::class.asClassName()
val ITERABLE = Iterable::class.asClassName()
val COLLECTION = Collection::class.asClassName()

val ENTITY_INSERT_BUILDER = EntityInsertBuilder::class.asClassName()
val ENTITY_UPDATE_BUILDER = EntityUpdateBuilder::class.asClassName()
val ENTITY_PERSIST_BUILDER = EntityPersistBuilder::class.asClassName()
val ENTITY_DELETE_BUILDER = EntityDeleteBuilder::class.asClassName()
val ENTITY_DELETE_TABLE_BUILDER = EntityDeleteTableBuilder::class.asClassName()
val ENTITY_BULK_INSERT_BUILDER = EntityBulkInsertBuilder::class.asClassName()
val ENTITY_BULK_UPDATE_BUILDER = EntityBulkUpdateBuilder::class.asClassName()
val ENTITY_BULK_PERSIST_BUILDER = EntityBulkPersistBuilder::class.asClassName()
val ENTITY_BULK_DELETE_BUILDER = EntityBulkDeleteBuilder::class.asClassName()

val NOTHING_TO_INLINE = AnnotationSpec
    .builder(SUPPRESS)
    .addMember("value", "%S", "NOTHING_TO_INLINE")
    .build()

val EXTENSION_FUN_MODIFIERS = if (PUBLIC_EXTENSIONS) listOf(KModifier.INLINE) else listOf(KModifier.INLINE, KModifier.INTERNAL)

fun File.writeSource(packageName: String = PACKAGE_ROOT, fileName: String, fileBuilder: (fileBuilder: KotlinFile.Builder) -> Unit) {
  val builder = KotlinFile.builder(packageName = packageName, fileName = fileName)
  fileBuilder(builder)
  builder.addFileComment(Const.GENERATION_COMMENT)
      .build()
      .writeTo(this)
}

fun getHandlerInnerClassName(entityEnvironment: EntityEnvironment, className: String): ClassName =
    ClassName(PACKAGE_ROOT, entityEnvironment.handlerClassNameString, className)

fun TypeElement.asTypeName(): TypeName = asType().asTypeName()