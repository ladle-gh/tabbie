import Expression.Companion.A
import Expression.Companion.B
import Expression.Companion.C
import Expression.Companion.FOUR
import Expression.Companion.ONE
import Expression.Companion.ONE_HALF
import Expression.Companion.TWO
import internal.Constants
import internal.and
import java.math.BigDecimal

/**
 * @param foilPower if not provided, FOIL method is not used to simplify in the case that it's needed.
 * @see Expression.simplify
 */
fun Expression.ensureSimplified(foilPower: Int = 0) = if (this is SimpleExpression) this else simplify(foilPower)

fun sum(vararg members: Expression) = Sum(members.toList())
fun product(vararg members: Expression) = Product(members.toList())

fun Expression.pow(power: Expression) = Exponent(this, power)
fun Expression.sqrt() = this.pow(ONE_HALF)
fun Expression.squared() = this.pow(TWO)

fun Expression.reciprocal(): Expression {
    return when {
        this is SimpleExpression && isReciprocal() -> (this as SimpleExponent).base
        this == ONE -> ONE
        else -> Exponent(this, Expression.NEGATIVE_ONE)
    }
}

fun Expression.negate(): Expression {
    return if (this is CanBeNegative && isNegative()) {
        removeNegative()
    } else {
        Product(Expression.NEGATIVE_ONE and this)
    }
}

operator fun Expression.plus(other: Expression) = Sum(this and other)
operator fun Expression.minus(other: Expression) = this + other.negate()
operator fun Expression.times(other: Expression) = Product(this and other)
operator fun Expression.div(other: Expression) = this * other.reciprocal()

fun simpleSum(vararg members: SimpleExpression) = SimpleSum(members.toList())
fun simpleProduct(vararg members: SimpleExpression) = SimpleProduct(members.toList())

fun SimpleExpression.simplePow(power: SimpleExpression) = SimpleExponent(this, power)
fun SimpleExpression.simpleSqrt() = this.simplePow(ONE_HALF)
fun SimpleExpression.simpleSquared() = this.simplePow(TWO)

fun SimpleExpression.simpleReciprocal(): SimpleExpression {
    return if (isReciprocal()) {
        (this as SimpleExponent).base
    } else {
        SimpleExponent(this, Expression.NEGATIVE_ONE)
    }
}

fun SimpleExpression.simpleNegate(): SimpleExpression {
    return if (this is CanBeNegative && isNegative()) {
        removeNegative() as SimpleExpression    // Always true if caller is simplified
    } else {
        SimpleProduct(Expression.NEGATIVE_ONE and this)
    }
}

infix fun SimpleExpression.simplePlus(other: SimpleExpression) = SimpleSum(this and other)
infix fun SimpleExpression.simpleMinus(other: SimpleExpression) = SimpleSum(this and other.simpleNegate())
infix fun SimpleExpression.simpleTimes(other: SimpleExpression) = SimpleProduct(this and other)
infix fun SimpleExpression.simpleDiv(other: SimpleExpression) = SimpleProduct(this and other.simpleReciprocal())

/**
 * Any mathematical expression. The different types are as follows:
 *
 * - [Pure][PureExpression]: Expressions whose simplified value is always themselves
 *      - Value: A rational number
 *      - Variable: A placeholder for any rational number
 * - [Complex][ComplexExpression]: Expressions comprised of multiple other expressions
 *      - Product: A series of multiplications/divisions
 *      - Sum: A series of additions/subtractions
 * - [Elementary Function][ElementaryFunction]: Expressions comprised of a single other expression (the argument)
 * - [Exponent][Exponent]: An expression raised to a power, which is another expression
 *
 * The *integer power* of an expression is the largest integer that can be expressed as a power when the expression
 * is expressed as an exponent. For example:
 *
 * x^(2*y) = (x^y)^2, where 2 is the integer power
 *
 * Expressions are used when simplification to a [BigDecimal] value is not possible or is not exact
 * (note how fractions are expressed as [Products][Product]). Apart from [simplify] and [factor], all
 * member functions are extensions to prevent overriding. As a result, implementations must not redeclare these
 * functions either. When returning values of this class, it's most efficient to pass them as
 * [SimpleExpressions][SimpleExpression] when possible. A variable expression is a complex expression that cannot be
 * reduced to a [Value] or a [Fraction]
 * @see CanBeNegative
 * @see SimpleExpression
 */
interface Expression {
    /**
     * @param foilPower maximum integer power of a polynomial for this function to simplify using the FOIL
     * (first-outer-inner-last) method. If not provided, FOIL method is not used to simplify in the case that it's needed.
     * @return this expression in simplified form (without [evaluating][SimpleExpression.evaluate] it)
     * @see Expression.ensureSimplified
     */
    fun simplify(foilPower: Int = 0): SimpleExpression

    /**
     * Factoring is done using the following methods:
     * - Greatest common factor (GCF)
     * - Quadratic factoring
     * - Difference of squares
     * - Synthetic division
     * - Reverse Binomial Theorem
     * @return the fully-factored form of this expression
     */
    fun factor(): Expression

    companion object {
        val NEGATIVE_ONE = Value(BigDecimal(-1))
        val ZERO = Value(BigDecimal.ZERO)
        val ONE = Value(BigDecimal.ONE)
        val TWO = Value(BigDecimal(2))
        val ONE_HALF = SimpleExponent(TWO, NEGATIVE_ONE)
        val FOUR = Value(BigDecimal(4))
        val WHOLE_POWER_MAX = Value(Constants.WHOLE_POWER_MAX)

        val E = Variable('e')
        val PI = Variable("{pi}")
        val TWO_PI = SimpleProduct(TWO and PI)
        val HALF_PI = SimpleProduct(PI and ONE_HALF)

        val X = Variable('x')
        val A = Variable('a')
        val B = Variable('b')
        val C = Variable('c')
    }
}

// Must be taken out of Expression due to initialization errors
private val DETERMINANT = (B.simplePow(TWO) simpleMinus simpleProduct(FOUR, A, C)).simpleSqrt()
private val TWO_A = (TWO simpleTimes A).simpleReciprocal()
val QUADRATIC_POSITIVE = (B simplePlus DETERMINANT) simpleTimes TWO_A
val QUADRATIC_NEGATIVE = (B simpleMinus DETERMINANT) simpleTimes TWO_A

/**
 * An [Expression] in simplified form. Allows for efficient computation of certain functions.
 */
interface SimpleExpression : Expression {
    /**
     * Simplifies further any [fractions][Product] or [exact functions][ElementaryFunction].
     * @return the simplified form of this expression when all values are calculated to the specified
     * [precision]
     */
    fun evaluate(precision: Int, foilPower: Int = 0): SimpleExpression

    /**
     * @param vars a map of variable IDs ([Chars][Char]) to their respective substitutions
     * @return the simplified form of this expression when the given substitutions are made
     * @see VariableTable
     */
    fun substitute(vars: Map<Char, SimpleExpression>): SimpleExpression

    /**
     * Optimized for use in [Expression.div]
     */
    fun isReciprocal(): Boolean

    /**
     * @return decimal coefficient (not including [Exponents][Exponent] which cannot be simplified further)
     */
    fun partitionCoeff(): Pair<BigDecimal, SimpleExpression>

    /**
     * @return First: the integer power of this expression;
     * Second: the base when this expression is expressed as an exponent using the integer power
     */
    fun partitionIntPower(): Pair<Int, SimpleExpression>

    /**
     * TODO add description
     */
    fun toFraction(): Fraction {
        throw IllegalCallerException("Calling type can never be a valid Fraction")
    }
}

/**
 * Signifies that an expression can be negated (using the abstract function below) in a matter more efficient than
 * just wrapping it as a [Product] with a coefficient of -1.
 * @see Expression
 */
interface CanBeNegative {
    /**
     * Optimized for use in [Expression.minus]
     */
    fun isNegative(): Boolean

    /**
     * Optimized for use in [Expression.minus]
     * @return unsimplified expression equivalent to caller with opposite sign. If caller is simplified, the
     * expression returned will also be simplified
     */
    fun removeNegative(): Expression
}

/**
 * An expression that is always simplified and equivalent to its factored form.
 * @see Value
 * @see Variable
 */
abstract class PureExpression : SimpleExpression {
    final override fun simplify(foilPower: Int) = this
    final override fun factor() = this
    final override fun evaluate(precision: Int, foilPower: Int) = this

    final override fun partitionIntPower() = 1 to this
    final override fun isReciprocal() = false
}

/**
 * An expression comprised of a repetition of the same operation (n >= 1). Factoring is aided with the use of an
 * integer-power-map list. For each [PowerMapList], the list members are terms (in sum) and map members ar
 * factors (in product).
 * @see PowerMapList
 * @see Product
 * @see Sum
 */
abstract class ComplexExpression(open val members: List<Expression>) : Expression

interface SimpleComplexExpression : SimpleExpression {
    val members: List<SimpleExpression>

    fun flatten(): SimpleComplexExpression
}