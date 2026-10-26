package com.siimkinks.sqlitemagic.writer

import com.siimkinks.sqlitemagic.WriterTypes.NULL_BYPASS_PARAMETER_ENCODING
import com.siimkinks.sqlitemagic.WriterTypes.PARAMETER_ENCODING_FACTORY
import com.siimkinks.sqlitemagic.WriterTypes.nullnessType
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.TypeName

internal data class ParameterEncodingPolicy(
  val factory: MemberName,
  val nullable: Boolean
) {
  val nullness get() = nullnessType(nullable)

  fun parameterResultType(resultType: TypeName) = resultType.copy(nullable = resultType.isNullable || nullable)
}

internal fun parameterEncodingPolicy(
  inputNullable: Boolean,
  encoderInputNullable: Boolean,
  encodingNullable: Boolean
): ParameterEncodingPolicy {
  val nullBypass = inputNullable && !encoderInputNullable
  return ParameterEncodingPolicy(
    factory = when {
      nullBypass -> NULL_BYPASS_PARAMETER_ENCODING
      else -> PARAMETER_ENCODING_FACTORY
    },
    nullable = nullBypass || encodingNullable
  )
}
