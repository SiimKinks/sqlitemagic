package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.GeneratedNames.PACKAGE_ROOT
import com.siimkinks.sqlitemagic.model.ModelGenerationNames
import com.siimkinks.sqlitemagic.view.ViewGenerationNames
import com.squareup.kotlinpoet.ClassName
import org.junit.jupiter.api.Test

internal class ReadModelGenerationNamesTest {
  @Test
  fun `model and view wrappers share canonical dao and table names`() {
    val packageName = "com.example.feature"
    val artifactStem = "Container_Account"
    val modelNames = ModelGenerationNames(
      packageName = packageName,
      artifactStem = artifactStem
    )
    val viewNames = ViewGenerationNames(
      packageName = packageName,
      artifactStem = artifactStem
    )
    val names: List<ReadModelGenerationNames> = listOf(
      modelNames,
      viewNames
    )

    assertThat(names.map(ReadModelGenerationNames::daoClassName))
      .containsExactly(
        ClassName(packageName, "SqliteMagic_Container_Account_Dao"),
        ClassName(packageName, "SqliteMagic_Container_Account_Dao")
      )
      .inOrder()
    assertThat(names.map(ReadModelGenerationNames::tableClassName))
      .containsExactly(
        ClassName(PACKAGE_ROOT, "Container_AccountTable"),
        ClassName(PACKAGE_ROOT, "Container_AccountTable")
      )
      .inOrder()

    assertThat(modelNames.daoClassName)
      .isSameInstanceAs(modelNames.daoClassName)
    assertThat(modelNames.tableClassName)
      .isSameInstanceAs(modelNames.tableClassName)
    assertThat(viewNames.daoClassName)
      .isSameInstanceAs(viewNames.daoClassName)
    assertThat(viewNames.tableClassName)
      .isSameInstanceAs(viewNames.tableClassName)
  }
}
