import internal.Constants
import internal.Constants.NEGATIVE_ONE
import internal.Constants.ROUGHLY_TWO_PI
import internal.Constants.TWO
import internal.integralPart
import internal.isNotValue
import internal.isValue
import java.math.BigDecimal
import java.math.BigDecimal.*
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode.*

private fun neg1Pow(x: Int): BigDecimal = if (x % 2 == 0) ONE else NEGATIVE_ONE

/**
 * @param x some positive integer
 */
private fun factorial(x: Int): BigDecimal {
    var result = BigInteger.ONE
    repeat (x - 1) {
        result *= it.toBigInteger() + BigInteger.TWO
    }
    return result.toBigDecimal()
}

/**
 * If [power] is greater than [Constants.WHOLE_POWER_MAX], the result is x^WHOLE_POWER_MAX*x^(n - WHOLE_POWER_MAX).
 * @return the result of the specified exponentiation in symbolic form
 * @see series
 */
fun BigDecimal.pow(power: BigDecimal, precision: Int): SimpleExpression {
    if (power isValue ONE) {
        return Value(this.round(MathContext(precision, HALF_UP)))
    }
    val (intPart, fracPart) = power.integralPart()
    if (intPart > Constants.WHOLE_POWER_MAX) {
        val next = this.pow(power - Constants.WHOLE_POWER_MAX, precision)
        return Value(this).simplePow(Expression.WHOLE_POWER_MAX) simpleTimes next
    }

    val ln = log(Fraction(this), precision)
    val numer: BigDecimal
    val denom: BigDecimal
    if (ln is Value) {
        numer = fracPart * ln.value
        denom = ONE
    } else {
        ln as SimpleProduct
        numer = fracPart * (ln.members[0] as Value).value
        denom = ((ln.members[1] as SimpleExponent).base as Value).value
    }
    return Value(this.pow(intPart.toInt()) * exp(Fraction(numer, denom), precision).evaluateAsFraction(precision))
}

internal fun sin(x: Fraction, precision: Int) = series(x % ROUGHLY_TWO_PI, precision,
    getNumer = { neg1Pow(it) },
    getDenom = { factorial(2*it + 1) },
    pow0 = 1,
    powStep = 2
)

internal fun cos(x: Fraction, precision: Int) = series(x % ROUGHLY_TWO_PI, precision,
    getNumer = { neg1Pow(it) },
    getDenom = { factorial(2*it) },
    pow0 = 0,
    powStep = 2
)

internal fun tan(x: Fraction, precision: Int) = sin(x, precision) / cos(x, precision)

internal fun sinh(x: Fraction, precision: Int) = series(x % ROUGHLY_TWO_PI, precision,
    getNumer = { ONE },
    getDenom = { factorial(2*it + 1) },
    pow0 = 1,
    powStep = 2
)

internal fun cosh(x: Fraction, precision: Int) = series(x % ROUGHLY_TWO_PI, precision,
    getNumer = { ONE },
    getDenom = { TWO *factorial(it) },
    pow0 = 0,
    powStep = 2
)

internal fun tanh(x: Fraction, precision: Int) = sinh(x, precision) / cosh(x, precision)

internal fun exp(x: Fraction, precision: Int) = series(x, precision,
    getNumer = { ONE },
    getDenom = { factorial(it) },
    pow0 = 0,
    powStep = 1
)

internal fun log(x: Fraction, precision: Int) = series(x - ONE, precision,
    getNumer = { neg1Pow(it) },
    getDenom = { it.toBigDecimal() },
    pow0 = 0,
    powStep = 1
)

internal fun arcsin(x: Fraction, precision: Int) = series(x, precision,
    getNumer = { factorial(2*it) },
    getDenom = {
        val square = TWO.pow(it) * factorial(it)
        square * square * (2 * it + 1).toBigDecimal()
    },
    pow0 = 1,
    powStep = 2
)

// Special cases and shortcuts covered by elem func symbols in simlpify()
internal fun arccos(x: Fraction, precision: Int) = Expression.HALF_PI - arcsin(x, precision)

internal fun arctan(x: Fraction, precision: Int) = series(x, precision,
    getNumer = { neg1Pow(it) },
    getDenom = { (2*it+1).toBigDecimal() },
    pow0 = 1,
    powStep = 2
)

/**
 * Influenced by [https://github.com/eobermuhlner/big-math/blob/master/ch.obermuhlner.math.big/src/
 * main/java/ch/obermuhlner/math/big/internal/SeriesCalculator.java]
 *
 * Example: sin(x) =
 *
 *  (-1)^n * x^(2n+1) -> +1 means pow0=1
 *
 *  ———————— = x - x^3/3! + x^5/5! ...
 *
 *       (2n+1)!
 *
 * , where pow0=1 and powStep=2
 *
 * @return the MacLaurin series (Taylor series at a=0) approximation in symbolic form
 * @param x the value supplied to the function
 * @param precision the number of decimal digits in the result
 * @param getNumer numerator of the coefficient
 * @param getDenom denominator of the coefficient
 * @param pow0 0 or 1; the power x is raised to for n=0 (if 0, the first term is 1)
 * @param powStep 1 or 2; the difference between subsequent powers x is raised to
 * @see pow
 */
internal fun series(
    x: Fraction,
    precision: Int,
    getNumer: (Int) -> BigDecimal,
    getDenom: (Int) -> BigDecimal,
    pow0: Int,
    powStep: Int
): SimpleExpression {
    val isFractional = x.denom isNotValue ONE

     /* Instead of calculating each exponent individually, the value is accumulated (exponent).
      * Each iteration, the exponent is multiplied by the appropriate factor. */
    val nextFactor = if (powStep == 1) x.numer else x.numer*x.numer
    val denomNextFactor = if (!isFractional || powStep == 1) x.denom else x.denom*x.denom
    var exponent: BigDecimal
    var denomExponent: BigDecimal

    var numer: BigDecimal
    var denom = x.denom
    if (pow0 == 0) {
        exponent = nextFactor   // x^0=1 (first iteration is skipped); Second exponent
        denomExponent = denomNextFactor
        numer = ONE
    } else {
        exponent = x.numer  // x^1=x
        denomExponent = x.denom
        numer = ZERO
    }
    var n = pow0 xor 1  // If pow0=0 (first term is 1), skip first iteration
    var result = ZERO
    var result0: BigDecimal

    do {
        result0 = result

        //  x - x^3/3! + x^5/5! - x^7/7! ... = (7!(5!((3!x - x^3) + 3!x^5)) - 3!5!x^7).../(3!5!7!)...
        val nextDenom = if (isFractional) (getDenom(n) * denomExponent) else getDenom(n)
        numer *= nextDenom
        numer += getNumer(n) * exponent * denom
        denom *= nextDenom

        exponent *= nextFactor
        if (isFractional) {
            denomExponent *= denomNextFactor
        }
        result = numer.divide(denom, MathContext(precision + 1, DOWN))
        ++n
    } while (result isNotValue result0)
     return Fraction(numer, denom).toExpression()
}
