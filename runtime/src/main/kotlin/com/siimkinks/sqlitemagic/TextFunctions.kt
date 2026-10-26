@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode

/**
 * The lower(X) function returns a copy of string X with all ASCII characters converted to lower case.
 * The default built-in lower() function works for ASCII characters only. To do case conversions
 * on non-ASCII characters, load the ICU extension.
 *
 * @param value Input of this function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
 */
@JvmName("requiredLower")
fun LOWER(value: SqlExpression<String?, Required, *>) = requiredNativeResult(
  node = unaryCall(
    function = "LOWER",
    value = value
  ),
  type = TEXT
)

/**
 * The lower(X) function returns a copy of string X with all ASCII characters converted to lower case.
 * The default built-in lower() function works for ASCII characters only. To do case conversions
 * on non-ASCII characters, load the ICU extension.
 *
 * @param value Input of this function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
 */
fun LOWER(value: SqlExpression<String?, *, *>) = nullableNativeResult(
  node = unaryCall(
    function = "LOWER",
    value = value
  ),
  type = TEXT
)

/**
 * The upper(X) function returns a copy of input string X in which all lower-case ASCII characters
 * are converted to their upper-case equivalent.
 *
 * @param value Input of this function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
 */
@JvmName("requiredUpper")
fun UPPER(value: SqlExpression<String?, Required, *>) = requiredNativeResult(
  node = unaryCall(
    function = "UPPER",
    value = value
  ),
  type = TEXT
)

/**
 * The upper(X) function returns a copy of input string X in which all lower-case ASCII characters
 * are converted to their upper-case equivalent.
 *
 * @param value Input of this function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
 */
fun UPPER(value: SqlExpression<String?, *, *>) = nullableNativeResult(
  node = unaryCall(
    function = "UPPER",
    value = value
  ),
  type = TEXT
)

/**
 * For a string value X, the length(X) function returns the number of characters (not bytes) in
 * X prior to the first NUL character. Since SQLite strings do not normally contain NUL
 * characters, the length(X) function will usually return the total number of characters in the
 * string X. For a blob value X, length(X) returns the number of bytes in the blob.
 * If X is NULL then length(X) is NULL. If X is numeric then length(X) returns the length of a
 * string representation of X.
 *
 * @param value Input of this function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
 */
@JvmName("requiredLength")
fun LENGTH(value: SqlExpression<*, Required, *>) = requiredNativeResult(
  node = unaryCall(
    function = "LENGTH",
    value = value
  ),
  type = INTEGER
)

/**
 * For a string value X, the length(X) function returns the number of characters (not bytes) in
 * X prior to the first NUL character. Since SQLite strings do not normally contain NUL
 * characters, the length(X) function will usually return the total number of characters in the
 * string X. For a blob value X, length(X) returns the number of bytes in the blob.
 * If X is NULL then length(X) is NULL. If X is numeric then length(X) returns the length of a
 * string representation of X.
 *
 * @param value Input of this function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
 */
fun LENGTH(value: SqlExpression<*, *, *>) = nullableNativeResult(
  node = unaryCall(
    function = "LENGTH",
    value = value
  ),
  type = INTEGER
)

/**
 * The substr(X,Y,Z) function returns a substring of input string X that begins with
 * the Y-th character and which is Z characters long. If Z is omitted then substr(X,Y) returns
 * all characters through the end of X beginning with the Y-th.
 *
 * The left-most character of X is number 1. If Y is negative then the first character
 * of the substring is found by counting from the right rather than the left.
 * If Z is negative then the abs(Z) characters preceding the Y-th character are returned.
 * Character indices refer to actual UTF-8 characters.
 *
 * Examples:
 * ```
 * SUBSTRING("first".asValue(), 1, 2) returns "fi"
 * SUBSTRING("smiles".asValue(), 2, 5) returns "miles"
 * SUBSTRING("hamburger".asValue(), 4, -3) returns "ham"
 * SUBSTRING("unhappy".asValue(), 3) returns "happy"
 * SUBSTRING("last".asValue(), -1) returns "t"
 * ```
 *
 * @param value Input of this function
 * @param start The begin position (`Y`). The first position is always 1
 * @param length Number of characters to be returned (`Z`) or `null` to return all characters through the end
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
 */
@JvmName("requiredSubstring")
fun SUBSTRING(value: SqlExpression<String?, Required, *>, start: Int, length: Int? = null) = requiredNativeResult(
  node = substring(
    value = value,
    start = start,
    length = length
  ),
  type = TEXT
)

/**
 * The substr(X,Y,Z) function returns a substring of input string X that begins with
 * the Y-th character and which is Z characters long. If Z is omitted then substr(X,Y) returns
 * all characters through the end of X beginning with the Y-th.
 *
 * The left-most character of X is number 1. If Y is negative then the first character
 * of the substring is found by counting from the right rather than the left.
 * If Z is negative then the abs(Z) characters preceding the Y-th character are returned.
 * Character indices refer to actual UTF-8 characters.
 *
 * Examples:
 * ```
 * SUBSTRING("first".asValue(), 1, 2) returns "fi"
 * SUBSTRING("smiles".asValue(), 2, 5) returns "miles"
 * SUBSTRING("hamburger".asValue(), 4, -3) returns "ham"
 * SUBSTRING("unhappy".asValue(), 3) returns "happy"
 * SUBSTRING("last".asValue(), -1) returns "t"
 * ```
 *
 * @param value Input of this function
 * @param start The begin position (`Y`). The first position is always 1
 * @param length Number of characters to be returned (`Z`) or `null` to return all characters through the end
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
 */
fun SUBSTRING(value: SqlExpression<String?, *, *>, start: Int, length: Int? = null) = nullableNativeResult(
  node = substring(
    value = value,
    start = start,
    length = length
  ),
  type = TEXT
)

/**
 * The substr(X,Y,Z) function returns a substring of input blob X that begins with
 * the Y-th byte and which is Z bytes long. If Z is omitted then substr(X,Y) returns
 * all bytes through the end of X beginning with the Y-th.
 *
 * The left-most byte of X is number 1. If Y is negative then the first byte
 * of the substring is found by counting from the right rather than the left.
 * If Z is negative then the abs(Z) bytes preceding the Y-th byte are returned.
 *
 * @param value Input of this function
 * @param start The begin position (`Y`). The first position is always 1
 * @param length Number of bytes to be returned (`Z`) or `null` to return all bytes through the end
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
 */
@JvmName("requiredBlobSubstring")
fun SUBSTRING(value: SqlExpression<ByteArray?, Required, *>, start: Int, length: Int? = null) = requiredNativeResult(
  node = substring(
    value = value,
    start = start,
    length = length
  ),
  type = BLOB
)

/**
 * The substr(X,Y,Z) function returns a substring of input blob X that begins with
 * the Y-th byte and which is Z bytes long. If Z is omitted then substr(X,Y) returns
 * all bytes through the end of X beginning with the Y-th.
 *
 * The left-most byte of X is number 1. If Y is negative then the first byte
 * of the substring is found by counting from the right rather than the left.
 * If Z is negative then the abs(Z) bytes preceding the Y-th byte are returned.
 *
 * @param value Input of this function
 * @param start The begin position (`Y`). The first position is always 1
 * @param length Number of bytes to be returned (`Z`) or `null` to return all bytes through the end
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
 */
@JvmName("blobSubstring")
fun SUBSTRING(value: SqlExpression<ByteArray?, *, *>, start: Int, length: Int? = null) = nullableNativeResult(
  node = substring(
    value = value,
    start = start,
    length = length
  ),
  type = BLOB
)

/**
 * The trim(X,Y) function returns a string formed by removing any and all characters
 * that appear in Y from both ends of X. If the Y argument is omitted, trim(X) removes spaces from both ends of X.
 *
 * @param value Input of this function
 * @param characters The characters that will be removed from both ends of [value], or `null` to remove spaces
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
 */
@JvmName("requiredTrim")
fun TRIM(value: SqlExpression<String?, Required, *>, characters: String? = null) = requiredNativeResult(
  node = trim(
    value = value,
    characters = characters
  ),
  type = TEXT
)

/**
 * The trim(X,Y) function returns a string formed by removing any and all characters
 * that appear in Y from both ends of X. If the Y argument is omitted, trim(X) removes spaces from both ends of X.
 *
 * @param value Input of this function
 * @param characters The characters that will be removed from both ends of [value], or `null` to remove spaces
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
 */
fun TRIM(value: SqlExpression<String?, *, *>, characters: String? = null) = nullableNativeResult(
  node = trim(
    value = value,
    characters = characters
  ),
  type = TEXT
)

/**
 * The replace(X,Y,Z) function returns a string formed by substituting string
 * Z for every occurrence of string Y in string X.
 *
 * The BINARY collating sequence is used for comparisons. If Y is an empty string
 * then return X unchanged.
 *
 * Example:
 * ```
 * REPLACE(AUTHOR.NAME, "-", " ")
 * ```
 *
 * @param value Input of this function
 * @param search The string to be replaced
 * @param replacement The replacement string
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
 */
@JvmName("requiredReplace")
fun REPLACE(value: SqlExpression<String?, Required, *>, search: String, replacement: String) = requiredNativeResult(
  node = replace(
    value = value,
    search = search,
    replacement = replacement
  ),
  type = TEXT
)

/**
 * The replace(X,Y,Z) function returns a string formed by substituting string
 * Z for every occurrence of string Y in string X.
 *
 * The BINARY collating sequence is used for comparisons. If Y is an empty string
 * then return X unchanged.
 *
 * Example:
 * ```
 * REPLACE(AUTHOR.NAME, "-", " ")
 * ```
 *
 * @param value Input of this function
 * @param search The string to be replaced
 * @param replacement The replacement string
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
 */
fun REPLACE(value: SqlExpression<String?, *, *>, search: String, replacement: String) = nullableNativeResult(
  node = replace(
    value = value,
    search = search,
    replacement = replacement
  ),
  type = TEXT
)

/**
 * Format string.
 *
 * The format(FORMAT,...) function works like the printf() function from the standard C library. It is rendered as
 * SQL printf(), its name before SQLite 3.38.0. The format string is never NULL and a NULL argument is printed as an
 * empty string or zero, so the result is never NULL.
 *
 * Example:
 * ```
 * FORMAT("%s / %d", AUTHOR.NAME, AUTHOR.ID)
 * ```
 *
 * @param format Format string that specifies how to construct the output string using values taken from subsequent
 *     arguments
 * @param arguments Arguments to [format]: expressions, `null` or values of type `String`, `Long`, `Int`, `Short`,
 *     `Double`, `Float`, `Boolean`, `Byte` or `ByteArray`
 * @return Expression representing the result of this function
 * @throws IllegalArgumentException if an argument is a query or a value of another type
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html#format)
 */
fun FORMAT(format: String, vararg arguments: Any?) = requiredNativeResult(
  node = ExpressionNode.Call(
    function = "PRINTF",
    arguments = buildList(arguments.size + 1) {
      add(textValue(format))
      arguments.mapTo(
        destination = this,
        transform = ::operandNode
      )
    },
    requiredBy = -1
  ),
  type = TEXT
)

/**
 * The abs(X) function returns the absolute value of the numeric argument X.
 * Abs(X) returns NULL if X is NULL. If X is the integer -9223372036854775808 then abs(X)
 * throws an integer overflow error since there is no equivalent positive 64-bit two's complement value.
 *
 * @param value Input of this function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
 */
@JvmName("requiredEncodableAbs")
fun <W, R : Number?> ABS(value: EncodableSqlExpression<W, R, Required, *>) = EncodableSqlExpression(
  node = unaryCall(
    function = "ABS",
    value = value
  ),
  nullness = Required,
  nonNullRead = value.nonNullRead,
  selectRead = value.nonNullRead,
  encode = value.encode
)

/**
 * The abs(X) function returns the absolute value of the numeric argument X.
 * Abs(X) returns NULL if X is NULL. If X is the integer -9223372036854775808 then abs(X)
 * throws an integer overflow error since there is no equivalent positive 64-bit two's complement value.
 *
 * @param value Input of this function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
 */
@JvmName("encodableAbs")
fun <W, R : Number?> ABS(value: EncodableSqlExpression<W, R, *, *>) = EncodableSqlExpression(
  node = unaryCall(
    function = "ABS",
    value = value
  ),
  nullness = SqlNullable,
  nonNullRead = value.nonNullRead,
  selectRead = contextualRead(value.nonNullRead),
  encode = value.encode
)

/**
 * The abs(X) function returns the absolute value of the numeric argument X.
 * Abs(X) returns NULL if X is NULL. If X is the integer -9223372036854775808 then abs(X)
 * throws an integer overflow error since there is no equivalent positive 64-bit two's complement value.
 *
 * @param value Input of this function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
 */
@JvmName("requiredAbs")
fun <R : Number?> ABS(value: SqlExpression<R, Required, *>) = SqlExpression(
  node = unaryCall(
    function = "ABS",
    value = value
  ),
  nonNullRead = value.nonNullRead,
  selectRead = value.nonNullRead,
  nullness = Required
)

/**
 * The abs(X) function returns the absolute value of the numeric argument X.
 * Abs(X) returns NULL if X is NULL. If X is the integer -9223372036854775808 then abs(X)
 * throws an integer overflow error since there is no equivalent positive 64-bit two's complement value.
 *
 * @param value Input of this function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
 */
fun <R : Number?> ABS(value: SqlExpression<R, *, *>) = SqlExpression(
  node = unaryCall(
    function = "ABS",
    value = value
  ),
  nonNullRead = value.nonNullRead,
  selectRead = contextualRead(value.nonNullRead),
  nullness = SqlNullable
)

/**
 * Concatenate this expression with another one.
 *
 * SQL: this || other
 *
 * Both values are converted to text. This operator always evaluates to either NULL or a text value.
 *
 * Example:
 * ```
 * AUTHOR.NAME CONCAT " " CONCAT BOOK.TITLE
 * ```
 *
 * @param other The expression to concatenate
 * @return Expression representing the result of this operator
 * @see [SQLite documentation: Expression](https://www.sqlite.org/lang_expr.html)
 */
@JvmName("requiredConcat")
infix fun SqlExpression<*, Required, *>.CONCAT(other: SqlExpression<*, Required, *>) = requiredNativeResult(
  node = ExpressionNode.Binary(
    left = node,
    operator = SqlOperator.CONCAT,
    right = other.node
  ),
  type = TEXT
)

/**
 * Concatenate this expression with another one.
 *
 * SQL: this || other
 *
 * Both values are converted to text. This operator always evaluates to either NULL or a text value.
 *
 * Example:
 * ```
 * AUTHOR.NAME CONCAT " " CONCAT BOOK.TITLE
 * ```
 *
 * @param other The expression to concatenate
 * @return Expression representing the result of this operator
 * @see [SQLite documentation: Expression](https://www.sqlite.org/lang_expr.html)
 */
infix fun SqlExpression<*, *, *>.CONCAT(other: SqlExpression<*, *, *>) = nullableNativeResult(
  node = ExpressionNode.Binary(
    left = node,
    operator = SqlOperator.CONCAT,
    right = other.node
  ),
  type = TEXT
)

/**
 * Concatenate this expression with a string.
 *
 * SQL: this || other
 *
 * The expression value is converted to text. This operator always evaluates to either NULL or a text value.
 *
 * @param other The string to concatenate
 * @return Expression representing the result of this operator
 * @see [SQLite documentation: Expression](https://www.sqlite.org/lang_expr.html)
 */
@JvmName("requiredHostConcat")
infix fun SqlExpression<*, Required, *>.CONCAT(other: String) = requiredNativeResult(
  node = ExpressionNode.Binary(
    left = node,
    operator = SqlOperator.CONCAT,
    right = textValue(other)
  ),
  type = TEXT
)

/**
 * Concatenate this expression with a string.
 *
 * SQL: this || other
 *
 * The expression value is converted to text. This operator always evaluates to either NULL or a text value.
 *
 * @param other The string to concatenate
 * @return Expression representing the result of this operator
 * @see [SQLite documentation: Expression](https://www.sqlite.org/lang_expr.html)
 */
@JvmName("hostConcat")
infix fun SqlExpression<*, *, *>.CONCAT(other: String) = nullableNativeResult(
  node = ExpressionNode.Binary(
    left = node,
    operator = SqlOperator.CONCAT,
    right = textValue(other)
  ),
  type = TEXT
)

/**
 * Concatenate this string with an expression.
 *
 * SQL: this || other
 *
 * The expression value is converted to text. This operator always evaluates to either NULL or a text value.
 *
 * @param other The expression to concatenate
 * @return Expression representing the result of this operator
 * @see [SQLite documentation: Expression](https://www.sqlite.org/lang_expr.html)
 */
@JvmName("requiredLeadingHostConcat")
infix fun String.CONCAT(other: SqlExpression<*, Required, *>) = requiredNativeResult(
  node = ExpressionNode.Binary(
    left = textValue(this),
    operator = SqlOperator.CONCAT,
    right = other.node
  ),
  type = TEXT
)

/**
 * Concatenate this string with an expression.
 *
 * SQL: this || other
 *
 * The expression value is converted to text. This operator always evaluates to either NULL or a text value.
 *
 * @param other The expression to concatenate
 * @return Expression representing the result of this operator
 * @see [SQLite documentation: Expression](https://www.sqlite.org/lang_expr.html)
 */
@JvmName("leadingHostConcat")
infix fun String.CONCAT(other: SqlExpression<*, *, *>) = nullableNativeResult(
  node = ExpressionNode.Binary(
    left = textValue(this),
    operator = SqlOperator.CONCAT,
    right = other.node
  ),
  type = TEXT
)

/**
 * Collating sequence of the COLLATE operator.
 *
 * Use one of the built-in [BINARY], [NOCASE] and [RTRIM] collations or a registered [collation].
 *
 * @see [SQLite documentation: Collating Sequences](https://www.sqlite.org/datatype3.html#collation)
 */
class Collation internal constructor(internal val sql: String)

/**
 * BINARY collation. Compares string data using memcmp(), regardless of text encoding.
 */
val BINARY = Collation("BINARY")

/**
 * NOCASE collation. Similar to binary, except that the 26 upper case characters of ASCII are
 * folded to their lower case equivalents before the comparison is performed.
 */
val NOCASE = Collation("NOCASE")

/**
 * RTRIM collation. The same as binary, except that trailing space characters are ignored.
 */
val RTRIM = Collation("RTRIM")

/**
 * Collation registered under [name], for example Android's `LOCALIZED` or `UNICODE`.
 *
 * @param name The collation name
 * @return Collation
 */
fun collation(name: String) = Collation(quoteSqlIdentifier(name))

private fun unaryCall(function: String, value: SqlExpression<*, *, *>) = ExpressionNode.Call(
  function = function,
  arguments = listOf(value.node),
  requiredBy = 0
)

private fun substring(
  value: SqlExpression<*, *, *>,
  start: Int,
  length: Int?
) = ExpressionNode.Call(
  function = "SUBSTR",
  arguments = when (length) {
    null -> listOf(value.node, integerValue(start.toLong()))
    else -> listOf(value.node, integerValue(start.toLong()), integerValue(length.toLong()))
  },
  requiredBy = 0
)

private fun trim(value: SqlExpression<*, *, *>, characters: String?) = ExpressionNode.Call(
  function = "TRIM",
  arguments = when (characters) {
    null -> listOf(value.node)
    else -> listOf(value.node, textValue(characters))
  },
  requiredBy = 0
)

private fun replace(
  value: SqlExpression<*, *, *>,
  search: String,
  replacement: String
) = ExpressionNode.Call(
  function = "REPLACE",
  arguments = listOf(value.node, textValue(search), textValue(replacement)),
  requiredBy = 0
)
