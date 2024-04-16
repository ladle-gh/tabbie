// Miscellaneous helper functions

package internal

/**
 * @return [List] containing the receiver and the parameter
 */
infix fun <T, U : T> T.and(that: U) = listOf(this, that)

/**
 * Contract for [Any.equals] that ensures:
 * - The other object is not null
 * - The other object is of EXACTLY the same type
 * - The other object is not refer to the same backing field as the receiver
 */
inline fun <reified T> T.strictEquals(other: Any?, body: (T) -> Boolean) =  when {
    other == null -> false
    other !is T -> false
    other === this -> true
    else -> body(other as T)
}
