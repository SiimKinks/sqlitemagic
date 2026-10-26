package com.siimkinks.sqlitemagic.transformer

import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.element.ParsedTypeImpl
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
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.isOk
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.tags.TypeAliasTag
import org.junit.jupiter.api.Test

internal class TransformerCollectionTest {
  private val emailTypeName = ClassName(FIXTURE_PACKAGE, "Email")

  private val compiler = SqliteMagicCompiler(::transformerCollectionProcessingSteps)

  @Test
  fun `collects transformer methods from top-level functions`() {
    compiler
      .compile(
        emailValueType(),
        kotlinTransformerSource(
          name = "EmailTransformer",
          contents = """
            @ObjectToDbValue
            fun emailToString(email: Email): String = email.value

            @DbValueToObject
            fun stringToEmail(value: String): Email = Email(value)
            """
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
    compiler
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
    compiler
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
            """
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
    compiler
      .compile(
        emailValueType(),
        javaStaticTransformer()
      )
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.EmailTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.EmailTransformer",
          encoderInputNullable = true,
          serializedTypeCanBeNull = true,
          decoderResultNullable = true,
          decoderInputNullable = true
        )
      )
  }

  @Test
  fun `pairs transformer methods declared in different owners`() {
    compiler
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
            """
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
    compiler
      .compile(
        databaseWithExternalTransformer(),
        classpaths = listOf(externalEmailTransformerCompilation().outputDirectory)
      )
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
    compiler
      .compile(
        submoduleDatabaseWithExternalTransformer(),
        classpaths = listOf(externalEmailTransformerCompilation().outputDirectory)
      )
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
    val external = SqliteMagicCompilation
      .compile(
        emailValueType(),
        javaStaticTransformer(className = "ExternalEmailTransformer"),
        processingStepsFactory = { emptyList() }
      )
      .isOk()
    compiler
      .compile(
        databaseWithExternalTransformer(),
        classpaths = listOf(external.outputDirectory)
      )
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.ExternalEmailTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.ExternalEmailTransformer",
          encoderInputNullable = true,
          serializedTypeCanBeNull = true,
          decoderResultNullable = true,
          decoderInputNullable = true
        )
      )
  }

  @Test
  fun `collects external superclass inherited Java static transformer methods from a database declaration`() {
    val external = SqliteMagicCompilation
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
            """
        ),
        processingStepsFactory = { emptyList() }
      )
      .isOk()
    compiler
      .compile(
        databaseWithExternalTransformer(),
        classpaths = listOf(external.outputDirectory)
      )
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.BaseEmailTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.BaseEmailTransformer",
          encoderInputNullable = true,
          serializedTypeCanBeNull = true,
          decoderResultNullable = true,
          decoderInputNullable = true
        )
      )
  }

  @Test
  fun `collects transformer methods for generic, nested, aliased and star-projected types`() {
    compiler
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
            """
        ),
        kotlinTransformerSource(
          name = "NestedListEmailTransformer",
          contents = """
            object NestedListEmailTransformer {
              @ObjectToDbValue
              fun emailsToString(emails: List<List<Email>>): String = emails.toString()

              @DbValueToObject
              fun stringToEmails(value: String): List<List<Email>> = listOf(listOf(Email(value)))
            }
            """
        ),
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
            """
        ),
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
            """
        ),
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
            """
        ),
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
            """
        ),
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
            """
        ),
        kotlinTransformerSource(
          name = "StarProjectedListTransformer",
          contents = """
            object StarProjectedListTransformer {
              @ObjectToDbValue
              fun valuesToString(values: List<*>): String = values.joinToString()

              @DbValueToObject
              fun stringToValues(value: String): List<*> = listOf(value)
            }
            """
        )
      )
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          deserializedType = TransformerTypeElementImpl(
            parsedType = ParsedTypeImpl(
              typeKey = "kotlin.collections.List<$FIXTURE_PACKAGE.Email>",
              typeName = LIST.parameterizedBy(emailTypeName),
              qualifiedName = "kotlin.collections.List",
              sqlStorageType = null
            ),
            transformerName = "List_Email"
          ),
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.ListEmailTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.ListEmailTransformer",
          objectToDbValueMethodName = "emailsToString",
          dbValueToObjectMethodName = "stringToEmails"
        ),
        emailTransformerElement(
          deserializedType = TransformerTypeElementImpl(
            parsedType = ParsedTypeImpl(
              typeKey = "kotlin.collections.List<kotlin.collections.List<$FIXTURE_PACKAGE.Email>>",
              typeName = LIST.parameterizedBy(
                LIST.parameterizedBy(emailTypeName)
              ),
              qualifiedName = "kotlin.collections.List",
              sqlStorageType = null
            ),
            transformerName = "List_List_Email"
          ),
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.NestedListEmailTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.NestedListEmailTransformer",
          objectToDbValueMethodName = "emailsToString",
          dbValueToObjectMethodName = "stringToEmails"
        ),
        emailTransformerElement(
          deserializedType = TransformerTypeElementImpl(
            parsedType = ParsedTypeImpl(
              typeKey = "$FIXTURE_PACKAGE.A.Value",
              typeName = ClassName(FIXTURE_PACKAGE, "A", "Value"),
              qualifiedName = "$FIXTURE_PACKAGE.A.Value",
              sqlStorageType = null
            ),
            transformerName = "A_Value"
          ),
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.NestedValueTransformers",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.NestedValueTransformers",
          objectToDbValueMethodName = "aValueToString",
          dbValueToObjectMethodName = "stringToAValue"
        ),
        emailTransformerElement(
          deserializedType = TransformerTypeElementImpl(
            parsedType = ParsedTypeImpl(
              typeKey = "$FIXTURE_PACKAGE.B.Value",
              typeName = ClassName(FIXTURE_PACKAGE, "B", "Value"),
              qualifiedName = "$FIXTURE_PACKAGE.B.Value",
              sqlStorageType = null
            ),
            transformerName = "B_Value"
          ),
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.NestedValueTransformers",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.NestedValueTransformers",
          objectToDbValueMethodName = "bValueToString",
          dbValueToObjectMethodName = "stringToBValue"
        ),
        emailTransformerElement(
          deserializedType = TransformerTypeElementImpl(
            parsedType = ParsedTypeImpl(
              typeKey = "$FIXTURE_PACKAGE.Outer.Inner.Value",
              typeName = ClassName(FIXTURE_PACKAGE, "Outer", "Inner", "Value"),
              qualifiedName = "$FIXTURE_PACKAGE.Outer.Inner.Value",
              sqlStorageType = null
            ),
            transformerName = "Outer_Inner_Value"
          ),
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.DeeplyNestedValueTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.DeeplyNestedValueTransformer",
          objectToDbValueMethodName = "valueToString",
          dbValueToObjectMethodName = "stringToValue"
        ),
        emailTransformerElement(
          deserializedType = TransformerTypeElementImpl(
            parsedType = ParsedTypeImpl(
              typeKey = "kotlin.collections.List<$FIXTURE_PACKAGE.Container.Value>",
              typeName = LIST.parameterizedBy(ClassName(FIXTURE_PACKAGE, "Container", "Value")),
              qualifiedName = "kotlin.collections.List",
              sqlStorageType = null
            ),
            transformerName = "List_Container_Value"
          ),
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.NestedGenericValueTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.NestedGenericValueTransformer",
          objectToDbValueMethodName = "valuesToString",
          dbValueToObjectMethodName = "stringToValues"
        ),
        emailTransformerElement(
          deserializedType = TransformerTypeElementImpl(
            parsedType = ParsedTypeImpl(
              typeKey = "$FIXTURE_PACKAGE.QualifiedContainer.Value",
              typeName = ClassName(FIXTURE_PACKAGE, "QualifiedContainer", "Value"),
              qualifiedName = "$FIXTURE_PACKAGE.QualifiedContainer.Value",
              sqlStorageType = null
            ),
            transformerName = "QualifiedContainer_Value"
          ),
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.FullyQualifiedValueTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.FullyQualifiedValueTransformer",
          objectToDbValueMethodName = "valueToString",
          dbValueToObjectMethodName = "stringToValue"
        ),
        emailTransformerElement(
          deserializedType = TransformerTypeElementImpl(
            parsedType = ParsedTypeImpl(
              typeKey = "$FIXTURE_PACKAGE.Email",
              typeName = aliasTypeName(
                simpleName = "EmailAlias",
                abbreviatedType = emailTypeName
              ),
              qualifiedName = "$FIXTURE_PACKAGE.Email",
              sqlStorageType = null
            ),
            transformerName = "EmailAlias"
          ),
          serializedType = TransformerTypeElementImpl(
            parsedType = ParsedTypeImpl(
              typeKey = "kotlin.String",
              typeName = aliasTypeName(
                simpleName = "StringAlias",
                abbreviatedType = STRING
              ),
              qualifiedName = "kotlin.String",
              sqlStorageType = SqlStorageType.STRING
            ),
            transformerName = "StringAlias"
          ),
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.AliasedEmailTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.AliasedEmailTransformer"
        ),
        emailTransformerElement(
          deserializedType = TransformerTypeElementImpl(
            parsedType = ParsedTypeImpl(
              typeKey = "kotlin.collections.List<*>",
              typeName = LIST.parameterizedBy(STAR),
              qualifiedName = "kotlin.collections.List",
              sqlStorageType = null
            ),
            transformerName = "List"
          ),
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.StarProjectedListTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.StarProjectedListTransformer",
          objectToDbValueMethodName = "valuesToString",
          dbValueToObjectMethodName = "stringToValues"
        )
      )
  }

  @Test
  fun `collects transformer methods for nullable types`() {
    compiler
      .compile(
        emailValueType(),
        nullableObjectTransformer()
      )
      .isOk()
      .assertTransformers(
        emailTransformerElement(
          deserializedType = TransformerTypeElementImpl(
            parsedType = ParsedTypeImpl(
              typeKey = "$FIXTURE_PACKAGE.Email",
              typeName = emailTypeName.copy(nullable = true),
              qualifiedName = "$FIXTURE_PACKAGE.Email",
              sqlStorageType = null
            ),
            transformerName = "Email"
          ),
          serializedType = TransformerTypeElementImpl(
            parsedType = ParsedTypeImpl(
              typeKey = "kotlin.String",
              typeName = STRING.copy(nullable = true),
              qualifiedName = "kotlin.String",
              sqlStorageType = SqlStorageType.STRING
            ),
            transformerName = "String"
          ),
          objectToDbValueOwnerQualifiedName = "$FIXTURE_PACKAGE.NullableEmailTransformer",
          dbValueToObjectOwnerQualifiedName = "$FIXTURE_PACKAGE.NullableEmailTransformer"
        )
      )
  }

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
      """
  )

  private fun externalEmailTransformerCompilation() = SqliteMagicCompilation
    .compile(
      emailValueType(),
      objectTransformer(className = "ExternalEmailTransformer"),
      processingStepsFactory = { emptyList() }
    )
    .isOk()

  private fun emailTransformerElement(
    deserializedType: TransformerTypeElement = TransformerTypeElementImpl(
      parsedType = ParsedTypeImpl(
        typeKey = "$FIXTURE_PACKAGE.Email",
        typeName = emailTypeName,
        qualifiedName = "$FIXTURE_PACKAGE.Email",
        sqlStorageType = null
      ),
      transformerName = "Email"
    ),
    serializedType: TransformerTypeElement = TransformerTypeElementImpl(
      parsedType = ParsedTypeImpl(
        typeKey = "kotlin.String",
        typeName = STRING,
        qualifiedName = "kotlin.String",
        sqlStorageType = SqlStorageType.STRING
      ),
      transformerName = "String"
    ),
    objectToDbValueOwnerQualifiedName: String?,
    dbValueToObjectOwnerQualifiedName: String?,
    objectToDbValueCallableKind: TransformerCallableKind = CLASS_MEMBER,
    dbValueToObjectCallableKind: TransformerCallableKind = CLASS_MEMBER,
    objectToDbValueMethodName: String = "emailToString",
    dbValueToObjectMethodName: String = "stringToEmail",
    encoderInputNullable: Boolean = deserializedType.typeName.isNullable,
    serializedTypeCanBeNull: Boolean = serializedType.typeName.isNullable,
    decoderResultNullable: Boolean = deserializedType.typeName.isNullable,
    decoderInputNullable: Boolean = serializedTypeCanBeNull
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
    encoderInputNullable = encoderInputNullable,
    serializedTypeCanBeNull = serializedTypeCanBeNull,
    decoderResultNullable = decoderResultNullable,
    decoderInputNullable = decoderInputNullable
  )
}
