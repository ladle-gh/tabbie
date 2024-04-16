import internal.and
import internal.gcd
import internal.isValue
import java.math.BigDecimal

/**
 * A rational number without precision loss. Similar in premise to ch.obermuhlner.math.big.BigRational, but more simple.
 * Also provides functions useful for symbolic computing.
 */
data class Fraction(val numer: BigDecimal, val denom: BigDecimal) {
    constructor(x: BigDecimal) : this(x, BigDecimal.ONE)

    fun toExpression(): SimpleExpression {
        if (denom isValue BigDecimal.ONE) {
            return Value(numer)
        }
        return SimpleProduct(Value(numer) and Value(denom).simpleReciprocal())
    }

    fun simplify(): Fraction {
        val gcd = gcd(numer, denom)
        if (gcd isValue BigDecimal.ONE) {
            return this
        }
        return Fraction(numer / gcd, denom / gcd)
    }

    operator fun minus(other: BigDecimal) = Fraction(numer - other*denom, denom)
    operator fun times(other: BigDecimal) = Fraction(numer * other, denom)

    operator fun rem(divisor: BigDecimal): Fraction {
        val intDivision = numer.divideToIntegralValue(denom*divisor)
        return Fraction(numer - divisor*intDivision, denom)
    }
}