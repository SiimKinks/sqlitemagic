package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.transformer.toTransformerRoundTypeElement
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.isOk
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class SqlStorageTypeParsingTest {
  @ParameterizedTest(name = "{0}")
  @MethodSource("storageTypeCases")
  fun `parses supported SQL storage types of Kotlin and Java properties`(
    label: String,
    className: String,
    expected: Map<String, SqlStorageType?>
  ) {
    assertThat(PARSED_TYPES.getValue(className)).containsExactlyEntriesIn(expected)
  }

  companion object {
    @JvmStatic
    fun storageTypeCases() = listOf(
      Arguments.of(
        "Kotlin SQL storage type variants",
        "KotlinSqlTypes",
        mapOf(
          "byte" to SqlStorageType.BYTE,
          "nullableByte" to SqlStorageType.BYTE,
          "byteAlias" to SqlStorageType.BYTE,
          "nullableByteAlias" to SqlStorageType.BYTE,
          "short" to SqlStorageType.SHORT,
          "nullableShort" to SqlStorageType.SHORT,
          "shortAlias" to SqlStorageType.SHORT,
          "nullableShortAlias" to SqlStorageType.SHORT,
          "int" to SqlStorageType.INT,
          "nullableInt" to SqlStorageType.INT,
          "intAlias" to SqlStorageType.INT,
          "nullableIntAlias" to SqlStorageType.INT,
          "long" to SqlStorageType.LONG,
          "nullableLong" to SqlStorageType.LONG,
          "longAlias" to SqlStorageType.LONG,
          "nullableLongAlias" to SqlStorageType.LONG,
          "float" to SqlStorageType.FLOAT,
          "nullableFloat" to SqlStorageType.FLOAT,
          "floatAlias" to SqlStorageType.FLOAT,
          "nullableFloatAlias" to SqlStorageType.FLOAT,
          "double" to SqlStorageType.DOUBLE,
          "nullableDouble" to SqlStorageType.DOUBLE,
          "doubleAlias" to SqlStorageType.DOUBLE,
          "nullableDoubleAlias" to SqlStorageType.DOUBLE,
          "string" to SqlStorageType.STRING,
          "nullableString" to SqlStorageType.STRING,
          "stringAlias" to SqlStorageType.STRING,
          "nullableStringAlias" to SqlStorageType.STRING,
          "byteArray" to SqlStorageType.BYTE_ARRAY,
          "nullableByteArray" to SqlStorageType.BYTE_ARRAY,
          "byteArrayAlias" to SqlStorageType.BYTE_ARRAY,
          "nullableByteArrayAlias" to SqlStorageType.BYTE_ARRAY,
          "boxedByteArray" to SqlStorageType.BOXED_BYTE_ARRAY,
          "nullableBoxedByteArray" to SqlStorageType.BOXED_BYTE_ARRAY,
          "nullableByteElementArray" to SqlStorageType.BOXED_BYTE_ARRAY,
          "nullableBoxedByteAndElementArray" to SqlStorageType.BOXED_BYTE_ARRAY,
          "boxedByteAliasArray" to SqlStorageType.BOXED_BYTE_ARRAY,
          "boxedByteArrayAlias" to SqlStorageType.BOXED_BYTE_ARRAY,
          "nullableBoxedByteArrayAlias" to SqlStorageType.BOXED_BYTE_ARRAY
        )
      ),
      Arguments.of(
        "unsupported Kotlin SQL storage types",
        "UnsupportedKotlinSqlTypes",
        mapOf(
          "boolean" to null,
          "nullableBoolean" to null,
          "booleanAlias" to null,
          "char" to null,
          "unsignedInt" to null,
          "intArray" to null,
          "boxedIntArray" to null,
          "byteList" to null,
          "value" to null
        )
      ),
      Arguments.of(
        "Java primitive and boxed SQL storage types",
        "JavaSqlTypes",
        mapOf(
          "primitiveByte" to SqlStorageType.BYTE,
          "boxedByte" to SqlStorageType.BYTE,
          "primitiveShort" to SqlStorageType.SHORT,
          "boxedShort" to SqlStorageType.SHORT,
          "primitiveInt" to SqlStorageType.INT,
          "boxedInt" to SqlStorageType.INT,
          "primitiveLong" to SqlStorageType.LONG,
          "boxedLong" to SqlStorageType.LONG,
          "primitiveFloat" to SqlStorageType.FLOAT,
          "boxedFloat" to SqlStorageType.FLOAT,
          "primitiveDouble" to SqlStorageType.DOUBLE,
          "boxedDouble" to SqlStorageType.DOUBLE,
          "string" to SqlStorageType.STRING,
          "primitiveByteArray" to SqlStorageType.BYTE_ARRAY,
          "boxedByteArray" to SqlStorageType.BOXED_BYTE_ARRAY
        )
      ),
      Arguments.of(
        "unsupported Java SQL storage types",
        "UnsupportedJavaSqlTypes",
        mapOf(
          "unsupportedBoolean" to null,
          "unsupportedBoxedBoolean" to null,
          "unsupportedChar" to null,
          "unsupportedBoxedChar" to null,
          "unsupportedPrimitiveIntArray" to null,
          "unsupportedBoxedIntArray" to null,
          "unsupportedStringArray" to null,
          "unsupportedByteList" to null,
          "unsupportedObject" to null
        )
      )
    )

    private val PARSED_TYPES by lazy {
      val step = SqlStorageTypeCollectionStep()
      SqliteMagicCompilation
        .compile(
          SourceFile.kotlin(
            name = "KotlinSqlTypes.kt",
            contents = """
              package com.example

              typealias ByteAlias = Byte
              typealias ShortAlias = Short
              typealias IntAlias = Int
              typealias LongAlias = Long
              typealias FloatAlias = Float
              typealias DoubleAlias = Double
              typealias StringAlias = String
              typealias ByteArrayAlias = ByteArray
              typealias BoxedByteArrayAlias = Array<Byte>

              class KotlinSqlTypes {
                val byte: Byte = 0
                val nullableByte: Byte? = null
                val byteAlias: ByteAlias = 0
                val nullableByteAlias: ByteAlias? = null
                val short: Short = 0
                val nullableShort: Short? = null
                val shortAlias: ShortAlias = 0
                val nullableShortAlias: ShortAlias? = null
                val int: Int = 0
                val nullableInt: Int? = null
                val intAlias: IntAlias = 0
                val nullableIntAlias: IntAlias? = null
                val long: Long = 0
                val nullableLong: Long? = null
                val longAlias: LongAlias = 0
                val nullableLongAlias: LongAlias? = null
                val float: Float = 0f
                val nullableFloat: Float? = null
                val floatAlias: FloatAlias = 0f
                val nullableFloatAlias: FloatAlias? = null
                val double: Double = 0.0
                val nullableDouble: Double? = null
                val doubleAlias: DoubleAlias = 0.0
                val nullableDoubleAlias: DoubleAlias? = null
                val string: String = ""
                val nullableString: String? = null
                val stringAlias: StringAlias = ""
                val nullableStringAlias: StringAlias? = null
                val byteArray: ByteArray = byteArrayOf()
                val nullableByteArray: ByteArray? = null
                val byteArrayAlias: ByteArrayAlias = byteArrayOf()
                val nullableByteArrayAlias: ByteArrayAlias? = null
                val boxedByteArray: Array<Byte> = emptyArray()
                val nullableBoxedByteArray: Array<Byte>? = null
                val nullableByteElementArray: Array<Byte?> = emptyArray()
                val nullableBoxedByteAndElementArray: Array<Byte?>? = null
                val boxedByteAliasArray: Array<ByteAlias> = emptyArray()
                val boxedByteArrayAlias: BoxedByteArrayAlias = emptyArray()
                val nullableBoxedByteArrayAlias: BoxedByteArrayAlias? = null
              }
            """
          ),
          SourceFile.kotlin(
            name = "UnsupportedKotlinSqlTypes.kt",
            contents = """
              package com.example

              typealias BooleanAlias = Boolean

              class Value

              class UnsupportedKotlinSqlTypes {
                val boolean: Boolean = false
                val nullableBoolean: Boolean? = null
                val booleanAlias: BooleanAlias = false
                val char: Char = 'a'
                val unsignedInt: UInt = 0u
                val intArray: IntArray = intArrayOf()
                val boxedIntArray: Array<Int> = emptyArray()
                val byteList: List<Byte> = emptyList()
                val value: Value = Value()
              }
            """
          ),
          SourceFile.java(
            name = "JavaSqlTypes.java",
            contents = """
              package com.example;

              public class JavaSqlTypes {
                public byte primitiveByte;
                public Byte boxedByte;
                public short primitiveShort;
                public Short boxedShort;
                public int primitiveInt;
                public Integer boxedInt;
                public long primitiveLong;
                public Long boxedLong;
                public float primitiveFloat;
                public Float boxedFloat;
                public double primitiveDouble;
                public Double boxedDouble;
                public String string;
                public byte[] primitiveByteArray;
                public Byte[] boxedByteArray;
              }
            """
          ),
          SourceFile.java(
            name = "UnsupportedJavaSqlTypes.java",
            contents = """
              package com.example;

              public class UnsupportedJavaSqlTypes {
                public boolean unsupportedBoolean;
                public Boolean unsupportedBoxedBoolean;
                public char unsupportedChar;
                public Character unsupportedBoxedChar;
                public int[] unsupportedPrimitiveIntArray;
                public Integer[] unsupportedBoxedIntArray;
                public String[] unsupportedStringArray;
                public java.util.List<Byte> unsupportedByteList;
                public Object unsupportedObject;
              }
            """
          ),
          processingStepsFactory = { listOf(step) }
        )
        .isOk()
      step.parsedTypes
    }
  }
}

private class SqlStorageTypeCollectionStep : ProcessingStep {
  val parsedTypes: Map<String, Map<String, SqlStorageType?>>
    field = linkedMapOf()

  override fun process(resolver: Resolver): ProcessingStepResult {
    listOf(
      "com.example.KotlinSqlTypes",
      "com.example.UnsupportedKotlinSqlTypes",
      "com.example.JavaSqlTypes",
      "com.example.UnsupportedJavaSqlTypes"
    )
      .mapNotNull(resolver::getClassDeclarationByName)
      .associateTo(parsedTypes) { declaration ->
        declaration.simpleName.asString() to declaration
          .getAllProperties()
          .associate { property ->
            property.simpleName.asString() to property.type
              .toTransformerRoundTypeElement()
              .sqlStorageType
          }
      }
    return Continue
  }
}
