package com.siimkinks.sqlitemagic.index

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier.JAVA_STATIC
import com.google.devtools.ksp.validate
import com.siimkinks.sqlitemagic.AnnotationNames.COLUMN_ANNOTATION
import com.siimkinks.sqlitemagic.AnnotationNames.INDEX_ANNOTATION
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
import com.siimkinks.sqlitemagic.annotation.Index
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.index.IndexKind.COMPOSITE
import com.siimkinks.sqlitemagic.index.IndexKind.FIELD
import com.siimkinks.sqlitemagic.index.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.model.ColumnElement
import com.siimkinks.sqlitemagic.model.TableElement
import com.siimkinks.sqlitemagic.model.TableRoundElement
import com.siimkinks.sqlitemagic.model.toPropertySourceKey
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Deferred
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Failed
import com.siimkinks.sqlitemagic.utils.findAnnotationWithType
import com.siimkinks.sqlitemagic.utils.isUncheckedAnnotationPresent
import com.siimkinks.sqlitemagic.utils.qualifiedNameOrSimpleName
import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY as TEMPORARY_TABLE
import com.siimkinks.sqlitemagic.index.SqliteSchema.TEMPORARY as TEMPORARY_SCHEMA

class IndexCollectionStep(
  private val environment: Environment
) : ProcessingStep {
  override fun process(resolver: Resolver): ProcessingStepResult {
    val (validIndexSymbols, deferredIndexSymbols) = resolver
      .getSymbolsWithAnnotation(INDEX_ANNOTATION)
      .partition(KSAnnotated::validate)
    val indexQueries = validIndexSymbols.mapNotNull(::parseIndexQuery)

    val (validColumnSymbols, deferredColumnSymbols) = resolver
      .getSymbolsWithAnnotation(COLUMN_ANNOTATION)
      .partition(KSAnnotated::validate)
    val memberships = validColumnSymbols
      .filterIsInstance<KSPropertyDeclaration>()
      .mapNotNull(::parseMembership)

    val tableAnnotations = TableAnnotationCache()
    val propertyClassifier = PropertyClassifier(
      environment = environment,
      tableAnnotations = tableAnnotations
    )
    val reporter = IndexCollectionReporter(environment)
    val deferredProperties = mutableListOf<KSAnnotated>()
    validateIndexSymbols(
      queries = indexQueries,
      propertyClassifier = propertyClassifier,
      deferredProperties = deferredProperties,
      reporter = reporter
    )
    validateMembershipSymbols(
      memberships = memberships,
      propertyClassifier = propertyClassifier,
      deferredProperties = deferredProperties,
      reporter = reporter
    )
    val compositeQueries = indexQueries
      .asSequence()
      .mapNotNull { query ->
        (query.symbol as? KSClassDeclaration)?.let { declaration ->
          declaration.qualifiedNameOrSimpleName() to query
        }
      }
      .toMap()
    val candidates = environment.tableRoundElementsForCurrentRound.flatMap { roundTable ->
      collectTableIndexes(
        roundTable = roundTable,
        compositeQueries = compositeQueries,
        reporter = reporter
      )
    }
    validateCandidates(
      candidates = candidates,
      reporter = reporter
    )
    if (!reporter.hasErrors) {
      candidates
        .map(IndexCandidate::roundElement)
        .forEach(environment::addIndexElement)
    }

    val deferred = (deferredIndexSymbols + deferredColumnSymbols + deferredProperties)
      .distinct()
    return when {
      reporter.hasErrors -> Failed
      deferred.isNotEmpty() -> Deferred(deferred)
      else -> Continue
    }
  }

  private fun validateIndexSymbols(
    queries: List<IndexQuery>,
    propertyClassifier: PropertyClassifier,
    deferredProperties: MutableList<KSAnnotated>,
    reporter: IndexCollectionReporter
  ) {
    queries.forEach { query ->
      when (val symbol = query.symbol) {
        is KSClassDeclaration -> if (propertyClassifier.tableAnnotation(symbol) == null) {
          reporter.error(
            message = "@Index is only valid on a @Table or a persisted property of a @Table: ${query.name}",
            symbol = symbol
          )
        }
        is KSPropertyDeclaration -> when (propertyClassifier.classify(symbol)) {
          PropertyState.Persisted -> Unit
          PropertyState.Deferred -> deferredProperties += symbol
          PropertyState.TopLevel -> reporter.error(
            message = "@Index is only valid on an instance property of a @Table: ${symbol.simpleName.asString()}",
            symbol = symbol
          )
          PropertyState.Companion -> reporter.error(
            message = "@Index is only valid on instance table properties: " +
                "${symbol.tableDisplayName()}.${symbol.simpleName.asString()}",
            symbol = symbol
          )
          PropertyState.Static -> reporter.error(
            message = "@Index is only valid on instance table properties: " +
                "${symbol.parentDeclaration?.simpleName?.asString().orEmpty()}.${symbol.simpleName.asString()}",
            symbol = symbol
          )
          PropertyState.Ignored -> reporter.error(
            message = "@Index cannot be used on an ignored property: " +
                "${symbol.parentDeclaration?.simpleName?.asString().orEmpty()}.${symbol.simpleName.asString()}",
            symbol = symbol
          )
          PropertyState.Excluded -> reporter.error(
            message = "@Index property is not persisted when Table.persistAll is false: " +
                "${symbol.parentDeclaration?.simpleName?.asString().orEmpty()}.${symbol.simpleName.asString()}",
            symbol = symbol
          )
          PropertyState.Unmatched -> reporter.error(
            message = "@Index is only valid on a @Table or a persisted property of a @Table: ${query.name}",
            symbol = symbol
          )
        }
        else -> reporter.error(
          message = "@Index is only valid on a @Table or a persisted property of a @Table",
          symbol = symbol
        )
      }
    }
  }

  private fun validateMembershipSymbols(
    memberships: List<IndexMembership>,
    propertyClassifier: PropertyClassifier,
    deferredProperties: MutableList<KSAnnotated>,
    reporter: IndexCollectionReporter
  ) {
    memberships.forEach { membership ->
      when (propertyClassifier.classify(membership.property)) {
        PropertyState.Persisted -> Unit
        PropertyState.Deferred -> deferredProperties += membership.property
        PropertyState.TopLevel,
        PropertyState.Companion,
        PropertyState.Static,
        PropertyState.Ignored,
        PropertyState.Excluded,
        PropertyState.Unmatched -> reporter.error(
          message = "belongsToIndex '${membership.indexName}' is only valid on a persisted property of a @Table",
          symbol = membership.property
        )
      }
    }
  }

  private fun collectTableIndexes(
    roundTable: TableRoundElement,
    compositeQueries: Map<String, IndexQuery>,
    reporter: IndexCollectionReporter
  ): List<IndexCandidate> {
    val table = roundTable.table
    val compositeMetadata = compositeQueries[roundTable.sourceDeclaration.qualifiedNameOrSimpleName()]
    val candidates = mutableListOf<IndexCandidate>()
    val schema = table.schema()

    val compositeColumns = compositeMetadata?.let { metadata ->
      table.allColumns.filter { column ->
        when {
          metadata.name.isEmpty() -> column.belongsToIndex == null
          else -> column.belongsToIndex == metadata.name
        }
      }
    }
    if (compositeMetadata != null && compositeColumns != null) {
      when {
        compositeColumns.isEmpty() -> reporter.error(
          message = "Composite index '${compositeMetadata.name}' must include at least one persisted member",
          symbol = roundTable.sourceDeclaration
        )
        else -> candidates += roundTable.candidate(
          index = compositeIndex(
            table = table,
            schema = schema,
            name = compositeMetadata.name,
            isUnique = compositeMetadata.isUnique,
            columns = compositeColumns
          ),
          symbol = roundTable.sourceDeclaration
        )
      }
    }

    table.allColumns.forEach { column ->
      column.index?.let { fieldIndex ->
        candidates += roundTable.candidate(
          index = IndexElement(
            identity = SqliteSchemaIdentity(
              schema = schema,
              identifier = SqliteIdentifier.from(fieldIndex.name)
            ),
            tableType = table.modelClassName,
            tableName = table.tableName,
            kind = FIELD,
            columns = listOf(column.toIndexColumn()),
            isUnique = fieldIndex.isUnique,
            sourcePropertyPath = column.access.path
          ),
          symbol = roundTable.sourceProperties[column.access.path]
        )
      }
    }

    val declaredCompositeName = compositeMetadata?.name
    table.allColumns
      .filter { column -> column.belongsToIndex != null && column.belongsToIndex != declaredCompositeName }
      .forEach { column ->
        reporter.error(
          message = "Composite membership '${column.belongsToIndex}' has no matching class-level @Index",
          symbol = roundTable.sourceProperties[column.access.path]
        )
      }
    return candidates
  }

  private fun validateCandidates(
    candidates: List<IndexCandidate>,
    reporter: IndexCollectionReporter
  ) {
    val accepted = environment.indexElements.values.associateByTo(
      destination = linkedMapOf(),
      keySelector = IndexElement::normalizedKey
    )
    val tablesByName = environment.tableElements.values.associateBy { table ->
      NormalizedIndexKey(
        schema = table.schema(),
        name = SqliteIdentifier.from(table.tableName).normalizedName
      )
    }
    candidates.forEach { candidate ->
      val index = candidate.roundElement.index
      val table = tablesByName[
        NormalizedIndexKey(
          schema = index.schema,
          name = index.normalizedName
        )
      ]
      when {
        index.name.contains('\n') || index.name.contains('\r') -> reporter.error(
          message = "Index name must not contain line breaks: ${index.name}",
          symbol = candidate.symbol
        )
        index.name.contains('\u0000') -> reporter.error(
          message = "Index name must not contain NUL characters",
          symbol = candidate.symbol
        )
        index.normalizedName.startsWith(prefix = "sqlite_") -> reporter.error(
          message = "Index name '${index.name}' is reserved",
          symbol = candidate.symbol
        )
        table != null -> reporter.error(
          message = "Index name '${index.name}' conflicts with table name '${table.tableName}'",
          symbol = candidate.symbol
        )
        accepted[index.normalizedKey] == index -> Unit
        accepted.containsKey(index.normalizedKey) -> reporter.error(
          message = "Duplicate index name '${accepted.getValue(index.normalizedKey).name}'",
          symbol = candidate.symbol
        )
        else -> accepted[index.normalizedKey] = index
      }
    }
  }
}

private class IndexCollectionReporter(
  private val environment: Environment
) {
  var errorCount = 0
    private set

  val hasErrors get() = errorCount > 0

  fun error(
    message: String,
    symbol: KSAnnotated?
  ) {
    errorCount++
    environment.logger.error(
      message = message,
      symbol = symbol
    )
  }
}

private class TableAnnotationCache {
  private val annotations = linkedMapOf<String, Table?>()

  fun get(declaration: KSClassDeclaration): Table? {
    val key = declaration.qualifiedNameOrSimpleName()
    if (key !in annotations) {
      annotations[key] = declaration.findAnnotationWithType<Table>()
    }
    return annotations[key]
  }
}

private class PropertyClassifier(
  private val environment: Environment,
  private val tableAnnotations: TableAnnotationCache
) {
  private val states = mutableMapOf<KSPropertyDeclaration, PropertyState>()

  fun tableAnnotation(declaration: KSClassDeclaration) = tableAnnotations.get(declaration)

  fun classify(property: KSPropertyDeclaration) = states.getOrPut(property) {
    classifyUncached(property)
  }

  private fun classifyUncached(property: KSPropertyDeclaration): PropertyState {
    if (property.toPropertySourceKey() in environment.persistedPropertySourceKeys) {
      return PropertyState.Persisted
    }
    val parent = property.parentDeclaration as? KSClassDeclaration
      ?: return PropertyState.TopLevel
    if (parent.isCompanionObject) return PropertyState.Companion
    if (JAVA_STATIC in property.modifiers) return PropertyState.Static
    if (property.isUncheckedAnnotationPresent<IgnoreColumn>()) return PropertyState.Ignored
    val tableAnnotation = tableAnnotations.get(parent)
    if (
      tableAnnotation != null &&
      parent.qualifiedNameOrSimpleName() in environment.deferredTableSourceKeys
    ) {
      return PropertyState.Deferred
    }
    if (tableAnnotation?.persistAll == false) return PropertyState.Excluded
    return PropertyState.Unmatched
  }
}

private enum class PropertyState {
  Persisted,
  Deferred,
  TopLevel,
  Companion,
  Static,
  Ignored,
  Excluded,
  Unmatched
}

private data class IndexQuery(
  val symbol: KSAnnotated,
  val name: String,
  val isUnique: Boolean
)

private data class IndexMembership(
  val property: KSPropertyDeclaration,
  val indexName: String
)

private data class IndexCandidate(
  val roundElement: IndexRoundElement,
  val symbol: KSAnnotated?
)

private data class NormalizedIndexKey(
  val schema: SqliteSchema,
  val name: String
)

private fun parseIndexQuery(symbol: KSAnnotated) = symbol
  .findAnnotationWithType<Index>()
  ?.let { annotation ->
    IndexQuery(
      symbol = symbol,
      name = annotation.value,
      isUnique = annotation.unique
    )
  }

private fun parseMembership(property: KSPropertyDeclaration) = property
  .findAnnotationWithType<Column>()
  ?.belongsToIndex
  ?.takeIf(String::isNotEmpty)
  ?.let { indexName ->
    IndexMembership(
      property = property,
      indexName = indexName
    )
  }

private fun TableRoundElement.candidate(
  index: IndexElement,
  symbol: KSAnnotated?
) = IndexCandidate(
  roundElement = IndexRoundElement(
    index = index,
    originatingFiles = originatingFiles
  ),
  symbol = symbol
)

private fun compositeIndex(
  table: TableElement,
  schema: SqliteSchema,
  name: String,
  isUnique: Boolean,
  columns: List<ColumnElement>
) = IndexElement(
  identity = SqliteSchemaIdentity(
    schema = schema,
    identifier = SqliteIdentifier.from(
      name.ifEmpty {
        "index_${table.tableName}_${columns.joinToString(separator = "_", transform = ColumnElement::columnName)}"
      }
    )
  ),
  tableType = table.modelClassName,
  tableName = table.tableName,
  kind = COMPOSITE,
  columns = columns.map(ColumnElement::toIndexColumn),
  isUnique = isUnique,
  sourcePropertyPath = null
)

private fun ColumnElement.toIndexColumn() = IndexColumnElement(
  propertyPath = access.path,
  columnName = columnName
)

private val IndexElement.normalizedKey
  get() = NormalizedIndexKey(
    schema = schema,
    name = normalizedName
  )

private fun TableElement.schema() = when {
  TEMPORARY_TABLE in options -> TEMPORARY_SCHEMA
  else -> MAIN
}

private fun KSPropertyDeclaration.tableDisplayName() = parentDeclaration
  ?.parentDeclaration
  ?.simpleName
  ?.asString()
  ?: parentDeclaration?.simpleName?.asString().orEmpty()
