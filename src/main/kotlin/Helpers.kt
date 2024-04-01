import java.math.BigDecimal

/**
 * [BigDecimal] constants.
 */
object Constants {  // Separate from Expression constants
    val TWO = 2.toBigDecimal()
    val NEGATIVE_ONE = -BigDecimal.ONE
    val ROUGHLY_TWO_PI = TWO * BigDecimal("3.141592653589793")
    val WHOLE_POWER_MAX = BigDecimal(999999999)
    val INT_MAX = Int.MAX_VALUE.toBigInteger()
}

infix fun BigDecimal.equals(other: BigDecimal) = compareTo(other) == 0
infix fun BigDecimal.notEquals(other: BigDecimal) = compareTo(other) != 0

/**
 * TODO
 */
inline fun <E, T> MutableMap<E, T>.addOrModify(key: E, init: () -> T, modifier: (T) -> Unit) {
    if (!containsKey(key)) {
        this[key] = init()
    }
    modifier(this[key]!!)
}

inline fun <T,U> Iterable<T>.foldInPlace(initial: U, modifier: U.(T) -> Unit): U {
    fold(initial) { _, it ->
        modifier(initial, it)
        initial
    }
    return initial
}


fun hash(vararg objs: Any?): Int {
    var code = 7
    objs.forEach { code = 31*code + it.hashCode() }
    return code
}

inline fun <reified T> T.strictEquals(other: Any?, body: (T) -> Boolean) =  when {
    other !is T -> false
    other == null -> false
    other === this -> true
    else -> body(other as T)
}
