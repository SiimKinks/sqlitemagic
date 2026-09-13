package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

fun mockViewQueryElement(
  ownerType: ClassName = ClassName(PACKAGE, "TestView"),
  propertyName: String = "QUERY",
  declaredType: TypeName = ClassName(PACKAGE, "CompiledSelect")
) = ViewQueryElement(
  ownerType = ownerType,
  propertyName = propertyName,
  declaredType = declaredType
)
