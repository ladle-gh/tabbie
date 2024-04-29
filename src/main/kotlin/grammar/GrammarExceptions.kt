package grammar

/**
 * An exception through by the [grammar] API.
 */
sealed class GrammarException(message: String) : Exception(message)

/**
 * Thrown to denote that a fatal error has occurred during parsing.
 */
class ParseException internal constructor(message : String, index: Int = -1) : GrammarException(message) {
    var index = index   // If -1, must be calculated first (raised by API user in listener)
        internal set
}

/**
 * Thrown when there is an attempt to assign a value to a property that has already been given a value
 * and can only be assigned a value once.
 */
class ReassignmentException internal constructor(message: String) : GrammarException(message)

/**
 * Thrown when a token is asserted to be of a certain type, but is actually of a different type.
 */
class TokenMismatchException internal constructor(message: String) : GrammarException(message)