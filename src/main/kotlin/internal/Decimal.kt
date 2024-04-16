// BigDecimal-related helper functions and properties

package internal

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * [BigDecimal] constants. Encapsulation prevents naming conflict with static members in [Expression].
 */
object Constants {
    val TWO = 2.toBigDecimal()
    val NEGATIVE_ONE = -BigDecimal.ONE
    val ROUGHLY_TWO_PI = TWO * BigDecimal("3.141592653589793")
    val WHOLE_POWER_MAX = BigDecimal(999999999)
    val INT_MAX = Int.MAX_VALUE.toBigInteger()
}

class MutableBigDecimal(var value: BigDecimal = BigDecimal.ZERO)

/**
 * Alternative to [BigDecimal.equals] that compares ONLY the value of the BigDecimal.
 * @see BigDecimal.isNotValue
 */
infix fun BigDecimal.isValue(other: BigDecimal) = compareTo(other) == 0

/**
 * Alternative to ![BigDecimal.equals] that compares ONLY the value of the BigDecimal.
 * @see BigDecimal.isValue
 */
infix fun BigDecimal.isNotValue(other: BigDecimal) = compareTo(other) != 0

/**
 * @return whole-valued, greatest common denominator (GCF) of the supplied values
 */
tailrec fun gcd(a: BigDecimal, b: BigDecimal): BigDecimal {
    return if (b isValue BigDecimal.ZERO) a else gcd(b, a % b)
}

/**
 * @return value to the left of the decimal point
 */
fun BigDecimal.integralPart(): Pair<BigDecimal, BigDecimal> {
    if (this isValue BigDecimal.ZERO) {
        return Pair(BigDecimal.ZERO, BigDecimal.ZERO)
    }
    val whole = setScale(0, RoundingMode.DOWN)
    return if (scale() <= 0 || this == whole) this to BigDecimal.ZERO else whole to (this - whole)
}
