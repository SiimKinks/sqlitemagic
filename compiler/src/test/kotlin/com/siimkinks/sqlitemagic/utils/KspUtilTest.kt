package com.siimkinks.sqlitemagic.utils

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.Modifier.JAVA_STATIC
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class KspUtilTest : ProcessingStepsTest {
  private val propertySurfaces = linkedMapOf<String, PropertySurface>()

  override val processingSteps
    get() = { _: Environment ->
      listOf(PropertySurfaceStep(propertySurfaces))
    }

  @Test
  fun `local and inherited properties include all instance KSP properties`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "KotlinModels.kt",
          contents = """
            package com.example

            open class KotlinBase(open val inherited: String)

            class KotlinChild(
              override val inherited: String,
              val local: String
            ) : KotlinBase(inherited) {
              companion object {
                const val companionValue = "companion"
              }
            }

            interface KotlinContract {
              val contractValue: String
            }

            class KotlinContractImplementation(
              override val contractValue: String
            ) : KotlinContract
          """
        ),
        SourceFile.java(
          name = "JavaBase.java",
          contents = """
            package com.example;

            public class JavaBase {
              public String inherited;
            }
          """
        ),
        SourceFile.java(
          name = "JavaChild.java",
          contents = """
            package com.example;

            public class JavaChild extends JavaBase {
              public static String staticValue;
              public String local;
            }
          """
        ),
        SourceFile.java(
          name = "JavaBean.java",
          contents = """
            package com.example;

            public class JavaBean {
              private String value;

              public String getValue() {
                return value;
              }

              public void setValue(String value) {
                this.value = value;
              }
            }
          """
        ),
        SourceFile.java(
          name = "JavaRecord.java",
          contents = """
            package com.example;

            public record JavaRecord(String value) {}
          """
        )
      )
      .isOk()

    assertThat(propertySurfaces).containsExactly(
      "KotlinChild", PropertySurface(
        helperProperties = setOf("inherited", "local"),
        allInstanceProperties = setOf("inherited", "local")
      ),
      "KotlinContractImplementation", PropertySurface(
        helperProperties = setOf("contractValue"),
        allInstanceProperties = setOf("contractValue")
      ),
      "JavaChild", PropertySurface(
        helperProperties = setOf("inherited", "local"),
        allInstanceProperties = setOf("inherited", "local")
      ),
      "JavaBean", PropertySurface(
        helperProperties = setOf("value"),
        allInstanceProperties = setOf("value")
      ),
      "JavaRecord", PropertySurface(
        helperProperties = emptySet(),
        allInstanceProperties = emptySet()
      )
    )
  }
}

internal data class PropertySurface(
  val helperProperties: Set<String>,
  val allInstanceProperties: Set<String>
)

internal class PropertySurfaceStep(
  private val propertySurfaces: MutableMap<String, PropertySurface>
) : ProcessingStep {
  override fun process(resolver: Resolver): ProcessingStepResult {
    listOf(
      "KotlinChild",
      "KotlinContractImplementation",
      "JavaChild",
      "JavaBean",
      "JavaRecord"
    ).map { name -> "com.example.$name" }
      .mapNotNull(resolver::getClassDeclarationByName)
      .forEach { declaration ->
        propertySurfaces[declaration.simpleName.asString()] = PropertySurface(
          helperProperties = declaration
            .getLocalAndInheritedProperties()
            .mapTo(linkedSetOf()) { it.simpleName.asString() },
          allInstanceProperties = declaration
            .getAllProperties()
            .filter { JAVA_STATIC !in it.modifiers }
            .mapTo(linkedSetOf()) { it.simpleName.asString() }
        )
      }
    return ProcessingStepResult.Continue
  }
}
