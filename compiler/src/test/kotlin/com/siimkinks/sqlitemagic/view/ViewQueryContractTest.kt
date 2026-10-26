package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.isOk
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class ViewQueryContractTest {
  private val compiler = SqliteMagicCompiler(::viewProcessingSteps)

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidDefiningQueries")
  fun `defining queries outside their contracts do not compile`(
    viewName: String,
    body: String,
    diagnostic: String,
    subject: String
  ) {
    compiler
      .compile(
        ViewSources.view(
          name = viewName,
          body = body
        )
      )
      .assertCompilationError(diagnostic, subject)
  }

  @Test
  fun `accepts companion, JvmField, Java static and inferred defining queries`() {
    compiler
      .compile(
        ViewSources.view(
          name = "CompanionQuerySurfaces",
          body = """
            @View
            data class JvmFieldQueryView(
              @ViewColumn("value")
              val value: String
            ) {
              companion object {
                @JvmField
                @ViewQuery
                val QUERY: SelectQuery<String, *> = error("compile-only")
              }
            }

            @View
            data class KotlinPropertyQueryView(
              @ViewColumn("value")
              val value: String
            ) {
              companion object {
                @ViewQuery
                val QUERY: SelectQuery<String, *> = error("compile-only")
              }
            }
          """
        ),
        SourceFile.java(
          name = "JavaStaticQueryView.java",
          contents = """
            package $PACKAGE;

            import com.siimkinks.sqlitemagic.SelectQuery;
            import com.siimkinks.sqlitemagic.annotation.View;
            import com.siimkinks.sqlitemagic.annotation.ViewColumn;
            import com.siimkinks.sqlitemagic.annotation.ViewQuery;

            @View
            public class JavaStaticQueryView {
              @ViewColumn("value") public String value;

              @ViewQuery
              public static final SelectQuery<String, ?> QUERY = null;

              public JavaStaticQueryView() {}
            }
          """
        ),
        ViewSources.view(
          name = "QueryTypeVariants",
          body = """
            import com.siimkinks.sqlitemagic.QuerySourceTable.Companion.QUERY_SOURCE
            import com.siimkinks.sqlitemagic.SELECT
            import com.siimkinks.sqlitemagic.annotation.Id

            data class QueryRow(val value: String)

            fun stringQuery(): SelectQuery<String, *> = error("compile-only")

            @Table
            data class QuerySource(
              @Id val id: Long? = null,
              val value: String
            )

            @View
            data class InferredQueryView(
              @ViewColumn("value")
              val value: String
            ) {
              companion object {
                @ViewQuery
                val QUERY = stringQuery()
              }
            }

            @View
            data class ExplicitRowTypeQueryView(
              @ViewColumn("value")
              val value: String
            ) {
              companion object {
                @ViewQuery
                val QUERY: SelectQuery<QueryRow, *> = error("compile-only")
              }
            }

            @View
            data class QueryStageSubtypeView(
              @ViewColumn("value")
              val value: String
            ) {
              companion object {
                @ViewQuery
                val QUERY = SELECT FROM QUERY_SOURCE
              }
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "JvmFieldQueryViewTable.kt",
        "KotlinPropertyQueryViewTable.kt",
        "JavaStaticQueryViewTable.kt",
        "SqliteMagic_JavaStaticQueryView_Dao.kt",
        "InferredQueryViewTable.kt",
        "ExplicitRowTypeQueryViewTable.kt",
        "QueryStageSubtypeViewTable.kt"
      )
  }

  companion object {
    @JvmStatic
    fun invalidDefiningQueries() = listOf(
      Arguments.of(
        "MissingQueryView",
        """
          @View
          data class MissingQueryView(
            @ViewColumn("value")
            val value: String
          )
        """,
        "@View must declare exactly one @ViewQuery",
        "MissingQueryView"
      ),
      Arguments.of(
        "MultipleQueryView",
        """
          @View
          data class MultipleQueryView(
            @ViewColumn("value")
            val value: String
          ) {
            companion object {
              @ViewQuery
              val FIRST_QUERY: SelectQuery<String, *> = error("compile-only")

              @ViewQuery
              val SECOND_QUERY: SelectQuery<String, *> = error("compile-only")
            }
          }
        """,
        "@View must declare exactly one @ViewQuery",
        "MultipleQueryView"
      ),
      Arguments.of(
        "PrivateQueryView",
        """
          @View
          data class PrivateQueryView(
            @ViewColumn("value")
            val value: String
          ) {
            companion object {
              @ViewQuery
              private val QUERY: SelectQuery<String, *> = error("compile-only")
            }
          }
        """,
        "@ViewQuery property must be accessible to generated code",
        "PrivateQueryView.QUERY"
      ),
      Arguments.of(
        "PrivateCompanionQueryView",
        """
          @View
          data class PrivateCompanionQueryView(
            @ViewColumn("value")
            val value: String
          ) {
            private companion object {
              @ViewQuery
              val QUERY: SelectQuery<String, *> = error("compile-only")
            }
          }
        """,
        "@ViewQuery property must be accessible to generated code",
        "PrivateCompanionQueryView.QUERY"
      ),
      Arguments.of(
        "InstanceQueryView",
        """
          @View
          class InstanceQueryView(
            @ViewColumn("value")
            val value: String
          ) {
            @ViewQuery
            val QUERY: SelectQuery<String, *> = error("compile-only")
          }
        """,
        "@ViewQuery must be a companion or static property",
        "InstanceQueryView.QUERY"
      ),
      Arguments.of(
        "NullableQueryView",
        """
          @View
          data class NullableQueryView(
            @ViewColumn("value")
            val value: String
          ) {
            companion object {
              @ViewQuery
              val QUERY: SelectQuery<String, *>? = error("compile-only")
            }
          }
        """,
        "@ViewQuery property must not be nullable",
        "NullableQueryView.QUERY"
      ),
      Arguments.of(
        "WrongQueryTypeView",
        """
          @View
          data class WrongQueryTypeView(
            @ViewColumn("value")
            val value: String
          ) {
            companion object {
              @ViewQuery
              val QUERY: String = "not a select query"
            }
          }
        """,
        "@ViewQuery property must have a SelectQuery type",
        "WrongQueryTypeView.QUERY"
      ),
      Arguments.of(
        "MutableQueryView",
        """
          @View
          data class MutableQueryView(
            @ViewColumn("value") val value: String
          ) {
            companion object {
              @ViewQuery
              var QUERY: SelectQuery<String, *> = error("compile-only")
            }
          }
        """,
        "@ViewQuery property must be immutable",
        "MutableQueryView.QUERY"
      ),
      Arguments.of(
        "LateinitQueryView",
        """
          @View
          data class LateinitQueryView(
            @ViewColumn("value") val value: String
          ) {
            companion object {
              @ViewQuery
              lateinit var QUERY: SelectQuery<String, *>
            }
          }
        """,
        "@ViewQuery property must be immutable",
        "LateinitQueryView.QUERY"
      )
    )
  }
}
