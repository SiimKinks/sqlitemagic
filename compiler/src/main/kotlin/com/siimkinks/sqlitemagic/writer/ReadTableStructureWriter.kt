package com.siimkinks.sqlitemagic.writer

import com.google.devtools.ksp.processing.CodeGenerator
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_AS
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_OBJECT_FROM_CURSOR_POSITION
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_OBJECT_FROM_SELECTED_POSITIONS
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_ALIAS
import com.siimkinks.sqlitemagic.GenerationNames
import com.siimkinks.sqlitemagic.WriterTypes.CHECK_NOT_NULL
import com.siimkinks.sqlitemagic.WriterTypes.COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.CURSOR
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_PROPERTY
import com.siimkinks.sqlitemagic.WriterTypes.RELATED_ENTITIES
import com.siimkinks.sqlitemagic.WriterTypes.TABLE
import com.siimkinks.sqlitemagic.WriterTypes.TABLE_RELATIONSHIP
import com.siimkinks.sqlitemagic.model.PropertyMetadata
import com.siimkinks.sqlitemagic.model.writeModelSource
import com.siimkinks.sqlitemagic.utils.callCode
import com.siimkinks.sqlitemagic.utils.namedArgument
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.INT_ARRAY
import com.squareup.kotlinpoet.KModifier.INFIX
import com.squareup.kotlinpoet.KModifier.INTERNAL
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

internal class ReadTableStructureWriter(
  private val names: GenerationNames,
  private val modelClassName: ClassName,
  private val isPublic: Boolean,
  private val constructorArguments: List<CodeBlock>,
  private val columns: List<PropertySpec>,
  private val readsRelated: Boolean,
  private val relationships: CodeBlock?,
  private val partialRead: PartialRead?
) {
  class PartialRead(
    val entityProperties: CodeBlock,
    val readsRelated: Boolean
  )

  private val className = names.tableClassName

  fun write(
    codeGenerator: CodeGenerator,
    originatingFiles: OriginatingFiles,
    fileProperties: List<PropertySpec> = emptyList()
  ) = FileSpec
    .builder(className)
    .apply { fileProperties.forEach(::addProperty) }
    .addType(
      TypeSpec
        .classBuilder(className)
        .primaryConstructor(
          FunSpec
            .constructorBuilder()
            .addModifiers(PRIVATE)
            .addParameter(name = VARIABLE_ALIAS, type = STRING.copy(nullable = true))
            .build()
        )
        .superclass(TABLE.parameterizedBy(modelClassName))
        .apply {
          if (!isPublic) {
            addModifiers(INTERNAL)
          }
          constructorArguments.forEach {
            addSuperclassConstructorParameter("%L", it)
          }
        }
        .addProperties(columns)
        .addProperty(
          PropertySpec
            .builder(
              name = "entityFields",
              type = LIST.parameterizedBy(COLUMN.parameterizedBy(modelClassName, STAR, STAR, STAR, STAR, STAR, STAR))
            )
            .addModifiers(OVERRIDE)
            .initializer(
              callCode(
                callee = CodeBlock.of("listOf"),
                arguments = columns.map { CodeBlock.of("%N", it) }
              )
            )
            .build()
        )
        .apply {
          if (relationships != null) {
            addProperty(
              companionListOverride(
                name = "relationships",
                type = TABLE_RELATIONSHIP,
                companionName = RELATIONSHIPS
              )
            )
          }
          if (partialRead != null) {
            addProperty(
              companionListOverride(
                name = "entityProperties",
                type = ENTITY_PROPERTY,
                companionName = ENTITY_PROPERTIES
              )
            )
          }
        }
        .addFunction(
          reader(
            name = "readEntity",
            position = "startIndex",
            positionType = INT,
            daoReader = METHOD_OBJECT_FROM_CURSOR_POSITION,
            related = readsRelated
          )
        )
        .apply {
          if (partialRead != null) {
            addFunction(
              reader(
                name = "readPartialEntity",
                position = "positions",
                positionType = INT_ARRAY,
                daoReader = METHOD_OBJECT_FROM_SELECTED_POSITIONS,
                related = partialRead.readsRelated
              )
            )
          }
        }
        .addFunction(
          FunSpec
            .builder(METHOD_AS)
            .addModifiers(OVERRIDE, INFIX)
            .addParameter(name = VARIABLE_ALIAS, type = STRING)
            .returns(className)
            .addStatement("return %T(%N)", className, VARIABLE_ALIAS)
            .build()
        )
        .addType(
          TypeSpec
            .companionObjectBuilder()
            .addProperty(
              PropertySpec
                .builder(name = names.structureFieldName, type = className)
                .initializer("%T(null)", className)
                .build()
            )
            .apply {
              if (relationships != null) {
                addProperty(
                  companionList(
                    name = RELATIONSHIPS,
                    type = TABLE_RELATIONSHIP,
                    initializer = relationships
                  )
                )
              }
              if (partialRead != null) {
                addProperty(
                  companionList(
                    name = ENTITY_PROPERTIES,
                    type = ENTITY_PROPERTY,
                    initializer = partialRead.entityProperties
                  )
                )
              }
            }
            .build()
        )
        .build()
    )
    .writeModelSource(
      codeGenerator = codeGenerator,
      originatingFiles = originatingFiles
    )

  /** A model with [related] reads always receives related entities, so the DAO reader takes them as non-null. */
  private fun reader(
    name: String,
    position: String,
    positionType: TypeName,
    daoReader: String,
    related: Boolean
  ) = FunSpec
    .builder(name)
    .addModifiers(OVERRIDE)
    .addParameter(name = "cursor", type = CURSOR)
    .addParameter(name = position, type = positionType)
    .addParameter(name = "related", type = RELATED_ENTITIES.copy(nullable = true))
    .returns(modelClassName)
    .addStatement(
      "return %T.%N(cursor, %N%L)",
      names.daoClassName,
      daoReader,
      position,
      when {
        related -> CodeBlock.of(", %M(related)", CHECK_NOT_NULL)
        else -> CodeBlock.of("")
      }
    )
    .build()

  companion object {
    private const val RELATIONSHIPS = "RELATIONSHIPS"
    private const val ENTITY_PROPERTIES = "ENTITY_PROPERTIES"

    private fun companionListOverride(
      name: String,
      type: ClassName,
      companionName: String
    ) = PropertySpec
      .builder(name = name, type = LIST.parameterizedBy(type))
      .addModifiers(OVERRIDE)
      .getter(
        FunSpec
          .getterBuilder()
          .addStatement("return %N", companionName)
          .build()
      )
      .build()

    private fun companionList(
      name: String,
      type: ClassName,
      initializer: CodeBlock
    ) = PropertySpec
      .builder(name = name, type = LIST.parameterizedBy(type))
      .addModifiers(PRIVATE)
      .initializer(initializer)
      .build()
  }
}

internal fun <P : PropertyMetadata> entityProperties(
  modelName: String,
  properties: List<P>,
  partialRequirements: Map<P, String?>
) = callCode(
  callee = CodeBlock.of("listOf"),
  arguments = properties.map { property ->
    callCode(
      callee = CodeBlock.of("%T", ENTITY_PROPERTY),
      arguments = listOfNotNull(
        namedArgument("path", "%S", "$modelName.${property.access.path.displayName}"),
        namedArgument("required", "%L", property in partialRequirements),
        partialRequirements[property]?.let { namedArgument("within", "%S", it) }
      )
    )
  }
)
