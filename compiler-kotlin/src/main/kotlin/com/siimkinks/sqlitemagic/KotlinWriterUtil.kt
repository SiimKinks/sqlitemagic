package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.entity.*
import com.siimkinks.sqlitemagic.util.NameConst.PACKAGE_ROOT
import com.siimkinks.sqlitemagic.writer.EntityEnvironment
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KotlinFile
import com.squareup.kotlinpoet.TypeName
import java.io.File
import javax.lang.model.element.TypeElement
import kotlin.reflect.KClass
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

fun File.writeSource(packageName: String = PACKAGE_ROOT, fileName: String, fileBuilder: (fileBuilder: KotlinFile.Builder) -> Unit) {
  val builder = KotlinFile.builder(packageName = packageName, fileName = fileName)
  fileBuilder(builder)
  builder.addFileComment(Const.GENERATION_COMMENT)
      .build()
      .writeTo(this)
}

fun getHandlerInnerClassName(entityEnvironment: EntityEnvironment, className: String): ClassName =
    ClassName.invoke(PACKAGE_ROOT, entityEnvironment.handlerClassNameString, className)

// FIXME remove when kotlinpoet correctly implements extension functions
fun <T : Any> KClass<T>.asClassName(): ClassName {
  qualifiedName?.let { return ClassName.bestGuess(it) }
  throw IllegalArgumentException("$this cannot be represented as a TypeName")
}

// FIXME remove when kotlinpoet correctly implements extension functions
fun TypeElement.asTypeName(): TypeName = KotlinWriterUtil.get(this.asType())