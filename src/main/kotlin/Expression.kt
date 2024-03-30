import java.math.BigDecimal

val String.r get() = Value(this.toBigDecimal())
val Char.v get() = Variable(this)

fun sum(vararg members: Expression) = Sum(members.toList())
fun product(vararg members: Expression) = Product(members.toList())
fun pow(x: Expression, power: Expression) = Exponent(x, power)
fun pow(x: Expression, power: Expression, isSimplified: Boolean) = Exponent(x, power, isSimplified)

private interface Symbol {



}

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
 */
abstract class Expression(private val isSimplified: Boolean) : Symbol {
    fun checkSimplified() = if (isSimplified) this else simplify()
    fun reciprocal() = if (isReciprocal()) (this as Exponent).base else Exponent(this, Value.NEGATIVE_ONE, true)
    fun negate() = if (isNegative()) (this as Product).removeNegative() else Product(listOf(Value.NEGATIVE_ONE, this), true)

    operator fun plus(other: Expression) = Sum(listOf(this, other))
    operator fun minus(other: Expression) = this + other.negate()
    operator fun times(other: Expression) = Product(listOf(this, other))
    operator fun div(other: Expression) = this * other.reciprocal()

    fun plus(other: Expression, isSimplified: Boolean) = Sum(listOf(this, other), isSimplified)
    fun minus(other: Expression, isSimplified: Boolean) = Sum(listOf(this, other.negate()), isSimplified)
    fun times(other: Expression, isSimplified: Boolean) = Product(listOf(this, other), isSimplified)
    fun div(other: Expression, isSimplified: Boolean) = Product(listOf(this, other.reciprocal()), isSimplified)

    abstract fun simplify(): Expression
    abstract fun factor(): Expression

    /**
     * Substitutes every instance of [varl] in this expression with [sub]. [simplify] is NOT called afterward!
     */
    abstract fun substitute(varl: Char, sub: BigDecimal): Expression

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

    open fun splitIntPower() = Split(1, this)
}