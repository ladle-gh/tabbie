import java.math.BigDecimal
import java.math.RoundingMode

val DECIMAL_TWO = 2.toBigDecimal()
val INTEGER_MAX = Int.MAX_VALUE.toBigInteger()

internal infix fun BigDecimal.equals(other: BigDecimal) = compareTo(other) == 0
internal infix fun BigDecimal.notEquals(other: BigDecimal) = compareTo(other) != 0

internal fun BigDecimal.splitDecimal(): Pair<BigDecimal, BigDecimal> {
    if (this equals BigDecimal.ZERO) {
        return Pair(BigDecimal.ZERO, BigDecimal.ZERO)
    }
    val whole = setScale(0, RoundingMode.DOWN)
    return if (scale() <= 0 || this == whole) this to BigDecimal.ZERO else whole to (this - whole)
}

internal fun hash(vararg objs: Any?): Int {
    var code = 7
    objs.forEach { code = 31*code + it.hashCode() }
    return code
}

internal inline fun <reified T> T.strictEquals(other: Any?, body: (T) -> Boolean) =  when {
    other !is T -> false
    other == null -> false
    other === this -> true
    else -> body(other as T)
}
