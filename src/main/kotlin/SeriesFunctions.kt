/* Functions for calculating elementary functions numerically. If an exact, non-numeric answer exists,
 * ElementaryFunction will handle it. Each numeric result is returned as a fraction, wherein it is later converted to
 * (and simplified as) an Expression.
 */

import internal.Constants
import internal.Constants.ROUGHLY_TWO_PI
import internal.integralPart
import internal.isNotValue
import internal.isValue
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

private object Factorial {
    private val factorialCache = mutableListOf<BigDecimal>(BigDecimal.ONE)

    /**
     * @param x some positive integer
     */
    fun of(x: Int): BigDecimal {
        if (factorialCache.size >= x) {
            return factorialCache[x - 1]
        }
        var result = factorialCache.last()
        repeat (x - factorialCache.size) {
            result *= (factorialCache.size + 1).toBigDecimal()
            factorialCache += result
        }
        return result
    }
}

// Special cases, pi and stuff, handled in ElementaryFunction
private fun neg1Pow(x: Int): BigDecimal = if (x % 2 == 0) BigDecimal.ONE else Constants.NEGATIVE_ONE



/**
 * If [power] is greater than [Constants.WHOLE_POWER_MAX], the result is x^WHOLE_POWER_MAX*x^(n - WHOLE_POWER_MAX).
 * @return the result of the specified exponentiation in symbolic form
 * @see series
 */
fun BigDecimal.pow(power: BigDecimal, precision: Int): SimpleExpression {
    if (power isValue BigDecimal.ONE) {
        return Value(this.round(MathContext(precision, RoundingMode.HALF_UP)))
    }
    val (intPart, fracPart) = power.integralPart()
    if (intPart > Constants.WHOLE_POWER_MAX) {
        val next = this.pow(power - Constants.WHOLE_POWER_MAX, precision)
        return Value(this).simplePow(Expression.WHOLE_POWER_MAX) simpleTimes next
    }

    val ln = log(Fraction(this), precision)
    return (exp(ln * fracPart, precision) * this.pow(intPart.toInt())).toExpression()
}

internal fun sin(x: Fraction, precision: Int) = series(x % ROUGHLY_TWO_PI, precision,
    getNumer = { neg1Pow(it) },
    getDenom = { Factorial.of(2*it + 1) },
    pow0 = 1,
    powStep = 2
)

internal fun cos(x: Fraction, precision: Int) = series(x % ROUGHLY_TWO_PI, precision,
    getNumer = { neg1Pow(it) },
    getDenom = { Factorial.of(2*it) },
    pow0 = 0,
    powStep = 2
)

fun tan(x: Fraction, precision: Int) = sin(x, precision) / cos(x, precision)

fun sinh(x: Fraction, precision: Int) = series(x % ROUGHLY_TWO_PI, precision,
    getNumer = { BigDecimal.ONE },
    getDenom = { Factorial.of(2*it + 1) },
    pow0 = 1,
    powStep = 2
)

fun cosh(x: Fraction, precision: Int) = series(x % ROUGHLY_TWO_PI, precision,
    getNumer = { BigDecimal.ONE },
    getDenom = { Constants.TWO * Factorial.of(it) },
    pow0 = 0,
    powStep = 2
)

fun tanh(x: Fraction, precision: Int) = sinh(x, precision) / cosh(x, precision)

fun exp(x: Fraction, precision: Int) = series(x, precision,
    getNumer = { BigDecimal.ONE },
    getDenom = { Factorial.of(it) },
    pow0 = 0,
    powStep = 1
)

internal fun log(x: Fraction, precision: Int) = series(x - Fraction.ONE, precision,
    getNumer = { neg1Pow(it) },
    getDenom = { it.toBigDecimal() },
    pow0 = 0,
    powStep = 1
)

internal fun arcsin(x: Fraction, precision: Int) = series(x, precision,
    getNumer = { Factorial.of(2*it) },
    getDenom = {
        val square = Constants.TWO.pow(it) * Factorial.of(it)
        square * square * (2 * it + 1).toBigDecimal()
    },
    pow0 = 1,
    powStep = 2
)

// pi - x does not matter, just use the result of the subtraction
// Special cases and shortcuts covered by elem func symbols in simlpify()
fun arccos(x: Fraction, precision: Int) = Fraction(Pi.withPrecision(precision), Constants.TWO) - arcsin(x, precision)

fun arctan(x: Fraction, precision: Int) = series(x, precision,
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
 *    (-1)^n * x^(2n+1)   -> +1 means pow0=1
 *    ----------------- = x - x^3/3! + x^5/5! ...
 *         (2n+1)!
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
inline fun series(
    x: Fraction,
    precision: Int,
    getNumer: (Int) -> BigDecimal,
    getDenom: (Int) -> BigDecimal,
    pow0: Int,
    powStep: Int
): Fraction {
    val isFractional = x.denom isNotValue BigDecimal.ONE

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
        numer = BigDecimal.ONE
    } else {
        exponent = x.numer  // x^1=x
        denomExponent = x.denom
        numer = BigDecimal.ZERO
    }
    var n = pow0 xor 1  // If pow0=0 (first term is 1), skip first iteration
    var result = BigDecimal.ZERO
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
        result = numer.divide(denom, MathContext(precision + 1, RoundingMode.DOWN))
        ++n
    } while (result isNotValue result0)
     return Fraction(numer, denom)
}
