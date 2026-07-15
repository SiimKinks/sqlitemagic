package com.siimkinks.sqlitemagic.transformer

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.dbconfig.DatabaseConfigurationCollectionStep
import com.siimkinks.sqlitemagic.transformer.TransformerCallableKind.CLASS_MEMBER
import com.siimkinks.sqlitemagic.transformer.TransformerCallableKind.TOP_LEVEL
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.FIXTURE_PACKAGE
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.databaseWithExternalTransformer
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.emailValueType
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.javaTransformerSource
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.kotlinTransformerSource
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.nullableObjectTransformer
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.objectTransformer
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.submoduleDatabaseWithExternalTransformer
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.tags.TypeAliasTag
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class TransformerCollectionTest : ProcessingStepsTest {
  private val emailTypeName = ClassName(FIXTURE_PACKAGE, "Email")
  private val listTypeName = ClassName("kotlin.collections", "List")

  override val processingSteps
    get() = { env: Environment ->
      listOf(
        DefaultTransformerCollectionStep(env),
        DatabaseConfigurationCollectionStep(env),
        TransformerCollectionStep(env)
      )
    }

  @Test
  fun `continues when transformer annotations are absent`() {
    SqliteMagicCompilation
      .compile(emailValueType())
      .isOk()
      .assertEmptyTransformers()
  }

  @Test
  fun `continues when database external transformer configuration is empty`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        SourceFile.kotlin(
          name = "TestDatabase.kt",
          contents = """
            package $FIXTURE_PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Database

            @Database
            class TestDatabase
            """.trimIndent()
        )
      )
      .isOk()
      .assertEmptyTransformers()
  }

  @Test
  fun `collects transformer methods from top-level functions`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        kotlinTransformerSource(
          name = "EmailTransformer",
          contents = """
            @ObjectToDbValue
            fun emailToString(email: Email): String = email.value

            @DbValueToObject
            fun stringToEmail(value: String): Email = Email(value)
            """.trimIndent()
        )
      )
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          objectToDbValueOwnerQualifiedName = null,
          dbValueToObjectOwnerQualifiedName = null,
          objectToDbValueCallableKind = TOP_LEVEL,
          dbValueToObjectCallableKind = TOP_LEVEL
        )
      )
  }

  @Test
  fun `collects transformer methods from Kotlin objects`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        objectTransformer()
      )
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.EmailTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.EmailTransformer"
        )
      )
  }

  @Test
  fun `collects transformer methods from companion objects`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        kotlinTransformerSource(
          name = "EmailTransformer",
          contents = """
            class EmailTransformer {
              companion object {
                @ObjectToDbValue
                fun emailToString(email: Email): String = email.value

                @DbValueToObject
                fun stringToEmail(value: String): Email = Email(value)
              }
            }
            """.trimIndent()
        )
      )
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.EmailTransformer.Companion",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.EmailTransformer.Companion"
        )
      )
  }

  @Test
  fun `collects transformer methods from Java static methods`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        javaStaticTransformer()
      )
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.EmailTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.EmailTransformer",
          serializedTypeCanBeNull = true
        )
      )
  }

  @Test
  fun `pairs transformer methods declared in different owners`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        kotlinTransformerSource(
          name = "SplitOwnerTransformer",
          contents = """
            object EmailSerializer {
              @ObjectToDbValue
              fun emailToString(email: Email): String = email.value
            }

            object EmailDeserializer {
              @DbValueToObject
              fun stringToEmail(value: String): Email = Email(value)
            }
            """.trimIndent()
        )
      )
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.EmailSerializer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.EmailDeserializer"
        )
      )
  }

  @Test
  fun `collects external transformer methods from a database declaration`() {
    externalEmailTransformerCompilation()
      .compile(databaseWithExternalTransformer())
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.ExternalEmailTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.ExternalEmailTransformer"
        )
      )
  }

  @Test
  fun `collects external transformer methods from a submodule database declaration`() {
    externalEmailTransformerCompilation()
      .compile(submoduleDatabaseWithExternalTransformer())
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.ExternalEmailTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.ExternalEmailTransformer"
        )
      )
  }

  @Test
  fun `collects external Java static transformer methods from a database declaration`() {
    externalJavaStaticTransformerCompilation()
      .compile(databaseWithExternalTransformer())
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.ExternalEmailTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.ExternalEmailTransformer",
          serializedTypeCanBeNull = true
        )
      )
  }

  @Test
  fun `collects external Java static transformer methods from a submodule database declaration`() {
    externalJavaStaticTransformerCompilation()
      .compile(submoduleDatabaseWithExternalTransformer())
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.ExternalEmailTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.ExternalEmailTransformer",
          serializedTypeCanBeNull = true
        )
      )
  }

  @Test
  fun `collects external superclass inherited Java static transformer methods from a database declaration`() {
    externalInheritedJavaStaticTransformerCompilation()
      .compile(databaseWithExternalTransformer())
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.BaseEmailTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.BaseEmailTransformer",
          serializedTypeCanBeNull = true
        )
      )
  }

  @Test
  fun `collects external superclass inherited Java static transformer methods from a submodule database declaration`() {
    externalInheritedJavaStaticTransformerCompilation()
      .compile(submoduleDatabaseWithExternalTransformer())
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.BaseEmailTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.BaseEmailTransformer",
          serializedTypeCanBeNull = true
        )
      )
  }

  @Test
  fun `collects transformer methods for generic types`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        kotlinTransformerSource(
          name = "ListEmailTransformer",
          contents = """
            object ListEmailTransformer {
              @ObjectToDbValue
              fun emailsToString(emails: List<Email>): String = emails.joinToString()

              @DbValueToObject
              fun stringToEmails(value: String): List<Email> = listOf(Email(value))
            }
            """.trimIndent()
        )
      )
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          deserializedType = TransformerTypeElementImpl(
            typeKey = "kotlin.collections.List<$FIXTURE_PACKAGE.Email>",
            typeName = listTypeName.parameterizedBy(emailTypeName),
            qualifiedName = "kotlin.collections.List",
            transformerName = "List_Email",
            sqlStorageType = null
          ),
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.ListEmailTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.ListEmailTransformer",
          objectToDbValueMethodName = "emailsToString",
          dbValueToObjectMethodName = "stringToEmails"
        )
      )
  }

  @Test
  fun `collects transformer methods for nested generic types`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        kotlinTransformerSource(
          name = "NestedListEmailTransformer",
          contents = """
            object NestedListEmailTransformer {
              @ObjectToDbValue
              fun emailsToString(emails: List<List<Email>>): String = emails.toString()

              @DbValueToObject
              fun stringToEmails(value: String): List<List<Email>> = listOf(listOf(Email(value)))
            }
            """.trimIndent()
        )
      )
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          deserializedType = TransformerTypeElementImpl(
            typeKey = "kotlin.collections.List<kotlin.collections.List<$FIXTURE_PACKAGE.Email>>",
            typeName = listTypeName.parameterizedBy(
              listTypeName.parameterizedBy(emailTypeName)
            ),
            qualifiedName = "kotlin.collections.List",
            transformerName = "List_List_Email",
            sqlStorageType = null
          ),
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.NestedListEmailTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.NestedListEmailTransformer",
          objectToDbValueMethodName = "emailsToString",
          dbValueToObjectMethodName = "stringToEmails"
        )
      )
  }

  @Test
  fun `preserves enclosing names for nested transformed types with equal simple names`() {
    val transformerOwnerQualifiedName = "$FIXTURE_PACKAGE.NestedValueTransformers"

    SqliteMagicCompilation
      .compile(
        kotlinTransformerSource(
          name = "NestedValueTransformers",
          contents = """
            class A {
              data class Value(val value: String)
            }

            class B {
              data class Value(val value: String)
            }

            object NestedValueTransformers {
              @ObjectToDbValue
              fun aValueToString(value: A.Value): String = value.value

              @DbValueToObject
              fun stringToAValue(value: String): A.Value = A.Value(value)

              @ObjectToDbValue
              fun bValueToString(value: B.Value): String = value.value

              @DbValueToObject
              fun stringToBValue(value: String): B.Value = B.Value(value)
            }
            """.trimIndent()
        )
      )
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          deserializedType = TransformerTypeElementImpl(
            typeKey = "$FIXTURE_PACKAGE.A.Value",
            typeName = ClassName(FIXTURE_PACKAGE, "A", "Value"),
            qualifiedName = "$FIXTURE_PACKAGE.A.Value",
            transformerName = "A_Value",
            sqlStorageType = null
          ),
          objectToDbValueOwnerQualifiedName = transformerOwnerQualifiedName,
          dbValueToObjectOwnerQualifiedName = transformerOwnerQualifiedName,
          objectToDbValueMethodName = "aValueToString",
          dbValueToObjectMethodName = "stringToAValue"
        ),
        emailTransformerElement(
          deserializedType = TransformerTypeElementImpl(
            typeKey = "$FIXTURE_PACKAGE.B.Value",
            typeName = ClassName(FIXTURE_PACKAGE, "B", "Value"),
            qualifiedName = "$FIXTURE_PACKAGE.B.Value",
            transformerName = "B_Value",
            sqlStorageType = null
          ),
          objectToDbValueOwnerQualifiedName = transformerOwnerQualifiedName,
          dbValueToObjectOwnerQualifiedName = transformerOwnerQualifiedName,
          objectToDbValueMethodName = "bValueToString",
          dbValueToObjectMethodName = "stringToBValue"
        )
      )
  }

  @Test
  fun `preserves all enclosing names for deeply nested transformed types`() {
    val transformerOwnerQualifiedName = "$FIXTURE_PACKAGE.DeeplyNestedValueTransformer"

    SqliteMagicCompilation
      .compile(
        kotlinTransformerSource(
          name = "DeeplyNestedValueTransformer",
          contents = """
            class Outer {
              class Inner {
                data class Value(val value: String)
              }
            }

            object DeeplyNestedValueTransformer {
              @ObjectToDbValue
              fun valueToString(value: Outer.Inner.Value): String = value.value

              @DbValueToObject
              fun stringToValue(value: String): Outer.Inner.Value = Outer.Inner.Value(value)
            }
            """.trimIndent()
        )
      )
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          deserializedType = TransformerTypeElementImpl(
            typeKey = "$FIXTURE_PACKAGE.Outer.Inner.Value",
            typeName = ClassName(FIXTURE_PACKAGE, "Outer", "Inner", "Value"),
            qualifiedName = "$FIXTURE_PACKAGE.Outer.Inner.Value",
            transformerName = "Outer_Inner_Value",
            sqlStorageType = null
          ),
          objectToDbValueOwnerQualifiedName = transformerOwnerQualifiedName,
          dbValueToObjectOwnerQualifiedName = transformerOwnerQualifiedName,
          objectToDbValueMethodName = "valueToString",
          dbValueToObjectMethodName = "stringToValue"
        )
      )
  }

  @Test
  fun `preserves enclosing names for nested generic type arguments`() {
    val transformerOwnerQualifiedName = "$FIXTURE_PACKAGE.NestedGenericValueTransformer"
    val nestedValueTypeName = ClassName(FIXTURE_PACKAGE, "Container", "Value")

    SqliteMagicCompilation
      .compile(
        kotlinTransformerSource(
          name = "NestedGenericValueTransformer",
          contents = """
            class Container {
              data class Value(val value: String)
            }

            object NestedGenericValueTransformer {
              @ObjectToDbValue
              fun valuesToString(values: List<Container.Value>): String = values.toString()

              @DbValueToObject
              fun stringToValues(value: String): List<Container.Value> =
                listOf(Container.Value(value))
            }
            """.trimIndent()
        )
      )
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          deserializedType = TransformerTypeElementImpl(
            typeKey = "kotlin.collections.List<$FIXTURE_PACKAGE.Container.Value>",
            typeName = listTypeName.parameterizedBy(nestedValueTypeName),
            qualifiedName = "kotlin.collections.List",
            transformerName = "List_Container_Value",
            sqlStorageType = null
          ),
          objectToDbValueOwnerQualifiedName = transformerOwnerQualifiedName,
          dbValueToObjectOwnerQualifiedName = transformerOwnerQualifiedName,
          objectToDbValueMethodName = "valuesToString",
          dbValueToObjectMethodName = "stringToValues"
        )
      )
  }

  @Test
  fun `excludes package name from fully qualified nested transformed types`() {
    val transformerOwnerQualifiedName = "$FIXTURE_PACKAGE.FullyQualifiedValueTransformer"

    SqliteMagicCompilation
      .compile(
        kotlinTransformerSource(
          name = "FullyQualifiedValueTransformer",
          contents = """
            class QualifiedContainer {
              data class Value(val value: String)
            }

            object FullyQualifiedValueTransformer {
              @ObjectToDbValue
              fun valueToString(value: $FIXTURE_PACKAGE.QualifiedContainer.Value): String = value.value

              @DbValueToObject
              fun stringToValue(value: String): $FIXTURE_PACKAGE.QualifiedContainer.Value =
                $FIXTURE_PACKAGE.QualifiedContainer.Value(value)
            }
            """.trimIndent()
        )
      )
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          deserializedType = TransformerTypeElementImpl(
            typeKey = "$FIXTURE_PACKAGE.QualifiedContainer.Value",
            typeName = ClassName(FIXTURE_PACKAGE, "QualifiedContainer", "Value"),
            qualifiedName = "$FIXTURE_PACKAGE.QualifiedContainer.Value",
            transformerName = "QualifiedContainer_Value",
            sqlStorageType = null
          ),
          objectToDbValueOwnerQualifiedName = transformerOwnerQualifiedName,
          dbValueToObjectOwnerQualifiedName = transformerOwnerQualifiedName,
          objectToDbValueMethodName = "valueToString",
          dbValueToObjectMethodName = "stringToValue"
        )
      )
  }

  @Test
  fun `collects transformer methods for nullable types`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        nullableObjectTransformer()
      )
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          deserializedType = TransformerTypeElementImpl(
            typeKey = "$FIXTURE_PACKAGE.Email",
            typeName = emailTypeName.copy(nullable = true),
            qualifiedName = "$FIXTURE_PACKAGE.Email",
            transformerName = "Email",
            sqlStorageType = null
          ),
          serializedType = TransformerTypeElementImpl(
            typeKey = "kotlin.String",
            typeName = STRING.copy(nullable = true),
            qualifiedName = "kotlin.String",
            transformerName = "String",
            sqlStorageType = SqlStorageType.STRING
          ),
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.NullableEmailTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.NullableEmailTransformer"
        )
      )
  }

  @Test
  fun `collects transformer methods for type aliases`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        kotlinTransformerSource(
          name = "AliasedEmailTransformer",
          contents = """
            typealias EmailAlias = Email
            typealias StringAlias = String

            object AliasedEmailTransformer {
              @ObjectToDbValue
              fun emailToString(email: EmailAlias): StringAlias = email.value

              @DbValueToObject
              fun stringToEmail(value: StringAlias): EmailAlias = Email(value)
            }
            """.trimIndent()
        )
      )
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          deserializedType = TransformerTypeElementImpl(
            typeKey = "$FIXTURE_PACKAGE.Email",
            typeName = aliasTypeName(
              simpleName = "EmailAlias",
              abbreviatedType = emailTypeName
            ),
            qualifiedName = "$FIXTURE_PACKAGE.Email",
            transformerName = "EmailAlias",
            sqlStorageType = null
          ),
          serializedType = TransformerTypeElementImpl(
            typeKey = "kotlin.String",
            typeName = aliasTypeName(
              simpleName = "StringAlias",
              abbreviatedType = STRING
            ),
            qualifiedName = "kotlin.String",
            transformerName = "StringAlias",
            sqlStorageType = SqlStorageType.STRING
          ),
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.AliasedEmailTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.AliasedEmailTransformer"
        )
      )
  }

  @Test
  fun `collects transformer methods for star-projected generic types`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        kotlinTransformerSource(
          name = "StarProjectedListTransformer",
          contents = """
            object StarProjectedListTransformer {
              @ObjectToDbValue
              fun valuesToString(values: List<*>): String = values.joinToString()

              @DbValueToObject
              fun stringToValues(value: String): List<*> = listOf(value)
            }
            """.trimIndent()
        )
      )
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          deserializedType = TransformerTypeElementImpl(
            typeKey = "kotlin.collections.List<*>",
            typeName = listTypeName.parameterizedBy(STAR),
            qualifiedName = "kotlin.collections.List",
            transformerName = "List",
            sqlStorageType = null
          ),
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.StarProjectedListTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.StarProjectedListTransformer",
          objectToDbValueMethodName = "valuesToString",
          dbValueToObjectMethodName = "stringToValues"
        )
      )
  }

  //region helpers
  private fun aliasTypeName(
    simpleName: String,
    abbreviatedType: TypeName
  ) = ClassName(FIXTURE_PACKAGE, simpleName).copy(
    tags = mapOf(TypeAliasTag::class to TypeAliasTag(abbreviatedType))
  )

  private fun javaStaticTransformer(
    className: String = "EmailTransformer"
  ) = javaTransformerSource(
    name = className,
    contents = """
      public final class $className {
        @ObjectToDbValue
        public static String emailToString(Email email) {
          return email.getValue();
        }

        @DbValueToObject
        public static Email stringToEmail(String value) {
          return new Email(value);
        }
      }
      """.trimIndent()
  )

  private fun externalEmailTransformerCompilation() = SqliteMagicCompilation
    .compile(
      emailValueType(),
      objectTransformer(className = "ExternalEmailTransformer"),
      processingStepsFactory = { emptyList() }
    )
    .isOk()

  private fun externalJavaStaticTransformerCompilation() = SqliteMagicCompilation
    .compile(
      emailValueType(),
      javaStaticTransformer(className = "ExternalEmailTransformer"),
      processingStepsFactory = { emptyList() }
    )
    .isOk()

  private fun externalInheritedJavaStaticTransformerCompilation() = SqliteMagicCompilation
    .compile(
      emailValueType(),
      javaTransformerSource(
        name = "ExternalEmailTransformer",
        contents = """
          class BaseEmailTransformer {
            @ObjectToDbValue
            public static String emailToString(Email email) {
              return email.getValue();
            }

            @DbValueToObject
            public static Email stringToEmail(String value) {
              return new Email(value);
            }
          }

          public final class ExternalEmailTransformer extends BaseEmailTransformer {
          }
          """.trimIndent()
      ),
      processingStepsFactory = { emptyList() }
    )
    .isOk()

  private fun emailTransformerElement(
    deserializedType: TransformerTypeElement = TransformerTypeElementImpl(
      typeKey = "$FIXTURE_PACKAGE.Email",
      typeName = emailTypeName,
      qualifiedName = "$FIXTURE_PACKAGE.Email",
      transformerName = "Email",
      sqlStorageType = null
    ),
    serializedType: TransformerTypeElement = TransformerTypeElementImpl(
      typeKey = "kotlin.String",
      typeName = STRING,
      qualifiedName = "kotlin.String",
      transformerName = "String",
      sqlStorageType = SqlStorageType.STRING
    ),
    objectToDbValueOwnerQualifiedName: String?,
    dbValueToObjectOwnerQualifiedName: String?,
    objectToDbValueCallableKind: TransformerCallableKind = CLASS_MEMBER,
    dbValueToObjectCallableKind: TransformerCallableKind = CLASS_MEMBER,
    objectToDbValueMethodName: String = "emailToString",
    dbValueToObjectMethodName: String = "stringToEmail",
    serializedTypeCanBeNull: Boolean = serializedType.typeName.isNullable
  ) = TransformerElement(
    deserializedType = deserializedType,
    serializedType = serializedType,
    objectToDbValueMethod = TransformerMethodElementImpl(
      methodName = objectToDbValueMethodName,
      packageName = FIXTURE_PACKAGE,
      ownerClassName = objectToDbValueOwnerQualifiedName?.let(ClassName::bestGuess),
      callableKind = objectToDbValueCallableKind
    ),
    dbValueToObjectMethod = TransformerMethodElementImpl(
      methodName = dbValueToObjectMethodName,
      packageName = FIXTURE_PACKAGE,
      ownerClassName = dbValueToObjectOwnerQualifiedName?.let(ClassName::bestGuess),
      callableKind = dbValueToObjectCallableKind
    ),
    serializedTypeCanBeNull = serializedTypeCanBeNull
  )
  //endregion
}
