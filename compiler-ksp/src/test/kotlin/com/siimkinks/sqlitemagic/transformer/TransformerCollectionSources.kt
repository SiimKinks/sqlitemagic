package com.siimkinks.sqlitemagic.transformer

import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.tschuchort.compiletesting.SourceFile
import org.intellij.lang.annotations.Language

object TransformerCollectionSources {
  const val FIXTURE_PACKAGE = "$PACKAGE.transformers"

  fun emailValueType() = SourceFile.kotlin(
    name = "Email.kt",
    contents = """
      package $FIXTURE_PACKAGE

      data class Email(val value: String)
      """
  )

  fun objectTransformer(
    className: String = "EmailTransformer"
  ) = kotlinTransformerSource(
    name = className,
    contents = """
      object $className {
        @ObjectToDbValue
        fun emailToString(email: Email): String = email.value

        @DbValueToObject
        fun stringToEmail(value: String): Email = Email(value)
      }
      """
  )

  fun nullableObjectTransformer(
    className: String = "NullableEmailTransformer"
  ) = kotlinTransformerSource(
    name = className,
    contents = """
      object $className {
        @ObjectToDbValue
        fun emailToString(email: Email?): String? = email?.value

        @DbValueToObject
        fun stringToEmail(value: String?): Email? = value?.let(::Email)
      }
      """
  )

  fun databaseWithExternalTransformer() = SourceFile.kotlin(
    name = "TestDatabase.kt",
    contents = """
      package $FIXTURE_PACKAGE

      import com.siimkinks.sqlitemagic.annotation.Database

      @Database(externalTransformers = [ExternalEmailTransformer::class])
      class TestDatabase
      """
  )

  fun submoduleDatabaseWithExternalTransformer() = SourceFile.kotlin(
    name = "TestSubmoduleDatabase.kt",
    contents = """
      package $FIXTURE_PACKAGE

      import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase

      @SubmoduleDatabase(
        value = "feature",
        externalTransformers = [ExternalEmailTransformer::class]
      )
      class TestSubmoduleDatabase
      """
  )

  fun kotlinTransformerSource(
    name: String,
    @Language("kotlin") contents: String
  ) = SourceFile.kotlin(
    name = "$name.kt",
    contents = """
      package $FIXTURE_PACKAGE

      import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
      import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

      $contents
      """
  )

  fun javaTransformerSource(
    name: String,
    @Language("java") contents: String
  ) = SourceFile.java(
    name = "$name.java",
    contents = """
      package $FIXTURE_PACKAGE;

      import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject;
      import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue;

      $contents
      """
  )
}
