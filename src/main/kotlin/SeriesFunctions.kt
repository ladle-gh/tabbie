import java.math.BigDecimal
import java.math.BigDecimal.*
import java.math.BigInteger
import java.math.RoundingMode.*
import java.util.*

private val NEGATIVE_ONE = -ONE
private val ROUGHLY_TWO_PI = DECIMAL_TWO * BigDecimal("3.141592653589793")

private fun BigDecimal.remainder2Pi(): BigDecimal = if (this < ROUGHLY_TWO_PI) this else this.remainder(ROUGHLY_TWO_PI)
private fun neg1Pow(x: Int): BigDecimal = if (x % 2 == 0) ONE else NEGATIVE_ONE

private fun twoToThe(x: Int): BigDecimal =
    BigDecimal(BigInteger(BitSet(x + 1).apply { set(x, true) }.toByteArray().reversedArray()))
// TODO benchmark against other methods

/**
 * [x] must be non-negative.
 */
private fun factorial(x: Int): BigDecimal {
    var result = BigInteger.ONE
    repeat (x - 1) {
        result *= it.toBigInteger() + BigInteger.TWO
    }
    return result.toBigDecimal()
}


internal fun sin(x: BigDecimal) = series(
    x = x.remainder2Pi(),
    getNumer = { neg1Pow(it) },
    getDenom = { factorial(2*it + 1) },
    pow0 = 1,
    powStep = 2
)

internal fun cos(x: BigDecimal) = series(
    x = x.remainder2Pi(),
    getNumer = { neg1Pow(it) },
    getDenom = { factorial(2*it) },
    pow0 = 0,
    powStep = 2
)

internal fun tan(x: BigDecimal) = sin(x) / cos(x)

internal fun sinh(x: BigDecimal) = series(
    x = x.remainder2Pi(),
    getNumer = { ONE },
    getDenom = { factorial(2*it + 1) },
    pow0 = 1,
    powStep = 2
)

internal fun cosh(x: BigDecimal) = series(
    x = x.remainder2Pi(),
    getNumer = { ONE },
    getDenom = { DECIMAL_TWO*factorial(it) },
    pow0 = 0,
    powStep = 2
)

internal fun tanh(x: BigDecimal) = sinh(x) / cosh(x)

internal fun exp(x: BigDecimal) = series(
    x = x,
    getNumer = { ONE },
    getDenom = { factorial(it) },
    pow0 = 0,
    powStep = 1
)

internal fun exp(frac: Product) = series(
    x = (frac.members[0] as Value).value,
    xDenom = (frac.members[1] as Value).value,
    getNumer = { ONE },
    getDenom = { factorial(it) },
    pow0 = 0,
    powStep = 1
)

internal fun log(x: BigDecimal) = series(
    x = x - ONE,
    getNumer = { neg1Pow(it) },
    getDenom = { it.toBigDecimal() },
    pow0 = 0,
    powStep = 1
)

internal fun arcsin(x: BigDecimal) = series(
    x = x,
    getNumer = { factorial(2*it) },
    getDenom = {
        val square = twoToThe(it)*factorial(it)
        square * square * (2*it+1).toBigDecimal()
    },
    pow0 = 1,
    powStep = 2
)

// Special cases and shortcuts covered by elem func symbols in simlpify()
internal fun arccos(x: BigDecimal) = HALF_PI - arcsin(x)

internal fun arctan(x: BigDecimal) = series(
    x = x,
    getNumer = { neg1Pow(it) },
    getDenom = { (2*it+1).toBigDecimal() },
    pow0 = 1,
    powStep = 2
)

internal fun pow(x: BigDecimal, y: BigDecimal) {

}

/**
 * Influenced by *https://github.com/eobermuhlner/big-math/blob/master/ch.obermuhlner.math.big/src/main/java/ch/
 * obermuhlner/math/big/internal/SeriesCalculator.java*
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
 * @return the MacLaurin series (Taylor series at a=0) approximation.
 * @param x the value supplied to the function
 * @param xDenom the denominator of the value supplied; used with a fractional argument
 * @param getNumer numerator of the coefficient
 * @param getDenom denominator of the coefficient
 * @param pow0 0 or 1; the power x is raised to for n=0 (if 0, the first term is 1)
 * @param powStep 1 or 2; the difference between subsequent powers x is raised to
 */
internal inline fun series(
    x: BigDecimal,
    xDenom: BigDecimal = ONE,
    getNumer: (Int) -> BigDecimal,
    getDenom: (Int) -> BigDecimal,
    pow0: Int,
    powStep: Int
): Expression {
    val isFractional = xDenom notEquals ONE

     /* Instead of calculating each exponent individually, the value is accumulated (exponent).
      * Each iteration, the exponent is multiplied by the appropriate factor. */
    val nextFactor = if (powStep == 1) x else x*x
    val denomNextFactor = if (!isFractional || powStep == 1) xDenom else xDenom*xDenom
    var exponent: BigDecimal
    var denomExponent: BigDecimal

    var numer: BigDecimal
    var denom = xDenom
    if (pow0 == 0) {
        exponent = nextFactor   // x^0=1 (first iteration is skipped); Second exponent
        denomExponent = denomNextFactor
        numer = ONE
    } else {
        exponent = x    // x^1=x
        denomExponent = xDenom
        numer = ZERO
    }
    var n = pow0 xor 1  // If pow0=0 (first term is 1), skip first iteration
    var result = ZERO
    var result0: BigDecimal

    do {
        println(n)
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
        result = numer.divide(denom, 500 + 1, DOWN)
        ++n
    } while (result notEquals result0)
     return Value(numer) / Value(denom)
}
// if power is fractional:
/*
if (numerator is not one, bring down)
if multipled by [denom] times itself, becomes the base
 */
