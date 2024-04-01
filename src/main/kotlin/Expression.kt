import java.math.BigDecimal

typealias ExpressionList = List<Expression>
typealias PowerMapList = List<Map<Int, List<Expression>>>
typealias TermIndexList = List<Int>
typealias VariableTable = Map<Char, Expression>

fun sum(vararg members: Expression) = Sum(members.toList())
fun product(vararg members: Expression, isSimplified: Boolean = false) = Product(members.toList(), isSimplified)
fun pow(x: Expression, power: Expression) = Exponent(x, power)
fun pow(x: Expression, power: Expression, isSimplified: Boolean) = Exponent(x, power, isSimplified)

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
 * (note how fractions are expressed as [Products][Product]).
 */
abstract class Expression(private val isSimplified: Boolean) {
    fun checkSimplified() = if (isSimplified) this else simplify()
    fun reciprocal() = if (isReciprocal()) (this as Exponent).base else Exponent(this, NEGATIVE_ONE, true)
    fun negate() = if (isNegative()) (this as Product).removeNegative() else Product(listOf(NEGATIVE_ONE, this), true)

    operator fun plus(other: Expression) = Sum(listOf(this, other))
    operator fun minus(other: Expression) = this + other.negate()
    operator fun times(other: Expression) = Product(listOf(this, other))
    operator fun div(other: Expression) = this * other.reciprocal()

    fun plus(other: Expression, isSimplified: Boolean) = Sum(listOf(this, other), isSimplified)
    fun minus(other: Expression, isSimplified: Boolean) = Sum(listOf(this, other.negate()), isSimplified)
    fun times(other: Expression, isSimplified: Boolean) = Product(listOf(this, other), isSimplified)
    fun div(other: Expression, isSimplified: Boolean) = Product(listOf(this, other.reciprocal()), isSimplified)
    fun sqrt(isSimplified: Boolean = false) = pow(this, ONE_HALF, isSimplified)
    fun squared(isSimplified: Boolean = false) = pow(this, TWO, isSimplified)

    abstract fun simplify(): Expression
    abstract fun factor(): Expression

    /**
     * Substitutes every instance of a variable with its corresponding [Expression].
     * @return simplified expression after substitution
     */
    abstract fun substitute(vars: VariableTable): Expression

    /**
     * [simplify] must be called beforehand to ensure correct behavior.
     * @return coefficient of this expression
     */
    open fun coefficient(): BigDecimal = BigDecimal.ONE

    /**
     * [simplify] must be called beforehand to ensure correct behavior.
     */
    open fun isReciprocal() = false

    /**
     * [simplify] must be called beforehand to ensure correct behavior.
     */
    open fun isNegative() = false

    /**
     * @return First: the integer power of this expression;
     * Second: the base when this expression is expressed as an exponent using the integer power
     */
    open fun isolateIntPower() = Pair(1, this)

    companion object {
        val NEGATIVE_ONE = Value(BigDecimal(-1))
        val ZERO = Value(BigDecimal.ZERO)
        val ONE = Value(BigDecimal.ONE)
        val TWO = Value(BigDecimal(2))
        val ONE_HALF = TWO.reciprocal()
        val FOUR = Value(BigDecimal(4))
        val E = Variable('e')
        val PI = Variable("{pi}")
        val TWO_PI = TWO.times(PI, true)
        val HALF_PI = PI.div(ONE_HALF, true)
        val X = Variable('x')
        val A = Variable('a')
        val B = Variable('b')
        val C = Variable('c')
    }
}