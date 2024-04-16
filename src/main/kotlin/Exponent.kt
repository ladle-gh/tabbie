import Expression.Companion.NEGATIVE_ONE
import Expression.Companion.ONE
import Expression.Companion.ZERO
import internal.*

import java.math.BigDecimal
import java.util.Objects.hash

open class Exponent(
    open val base: Expression,
    open val power: Expression
) : Expression {
    override fun simplify(foilPower: Int): SimpleExpression {
        var base = base.ensureSimplified()
        var power = power.ensureSimplified()

        when {
            power == NEGATIVE_ONE -> return base.simplePow(power)
            power == ZERO || base == ONE -> return ONE
            power == ONE -> return base
        }
        if (base is Exponent) {  // Bring outer exponent down (Power Rule); (x^a)^b = x^(a*b)
            power = (base.power * power).simplify(foilPower)
            base = base.base.ensureSimplified()
        }
        if (base is Value && power is Value) {  // Simplify decimal value
            return simplifyIntPower(base, power)
        }
        if (base is SimpleProduct) { // Distribute across terms; (x/y)^a = x^a/y^a, etc.
            return SimpleProduct(base.members.map { it.pow(power).simplify(foilPower) })
        }
        if (base is SimpleSum && power is Value && power.value <= foilPower.toBigDecimal()) {  // Work out manually
            val sumTerms = mutableListOf<Expression>()
            val (intPower, fracPower) = power.value.integralPart()
            val root = if (fracPower isNotValue BigDecimal.ZERO) {
                base.simplePow(Value(fracPower))
            } else null
            repeat ((intPower - BigDecimal.ONE).toInt()) {
                for (term in base.members) {
                    for (nextTerm in base.members) {  // FOIL
                        if (root != null) { // Distribute root
                            sumTerms.add(product(term, nextTerm, root))
                        } else {
                            sumTerms.add(term * nextTerm)
                        }
                    }
                }
            }
            return Sum(sumTerms).simplify(foilPower)    // Simplifies terms as well
        }
        // ...base is Variable || argument is ElementaryFunction
        return base.simplePow(power)
    }

    override fun factor(): Expression = base.ensureSimplified().pow(power.ensureSimplified())

    override fun equals(other: Any?) = strictEquals(other) { base == it.base && power == it.power }
    override fun hashCode() = hash(base, power)
    override fun toString() = "($base^$power)"  // Debug

    private fun simplifyIntPower(base: Value, power: Value): SimpleExpression {
        if (base == ZERO && power.value > BigDecimal.ZERO) {
            return ZERO
        }
        val (intPower, fracPower) = power.value.integralPart()
        if (intPower > Constants.WHOLE_POWER_MAX) { // Too big to simplify
            return base.simplePow(power)
        }
        val newBase = Value(base.value.pow(intPower.toInt()))
        return newBase.takeIf { fracPower isValue BigDecimal.ZERO } ?: newBase.simplePow(Value(fracPower))
    }
}

class SimpleExponent(
    override val base: SimpleExpression,
    override val power: SimpleExpression
) : Exponent(base, power), SimpleExpression {
    override fun isReciprocal() = base is Value && power == NEGATIVE_ONE
    override fun isolateCoeff() = BigDecimal.ONE to this
    override fun substitute(vars: VariableTable) = base.substitute(vars).pow(power.substitute(vars)).simplify()

    override fun evaluate(precision: Int, foilPower: Int): SimpleExpression {
        val newBase = base.evaluate(precision)
        val newPower = power.evaluate(precision)
        if (newBase !is Value) {
            return newBase.simplePow(newPower)
        }
        if (newPower is Value) {
            if (newPower.value isValue BigDecimal.ONE) { // More efficient than recursive calls to powExact()
                return newBase
            }
            return newBase.value.pow(newPower.value, precision)
        }
        val (coeff, inside) = newPower.isolateCoeff()   // Evaluate fractional powers
        if (coeff isValue BigDecimal.ONE) {
            return newBase simpleTimes newBase.simplePow(inside)
        }
        return newBase.value.pow(coeff, precision) simpleTimes newBase.simplePow(inside)
    }

    override fun isolateIntPower(): Pair<Int, SimpleExpression> {
        val intPower = power
            .isolateCoeff().first
            .integralPart().first
            .toBigInteger()
        return if (intPower > Constants.INT_MAX) {  // Treat power as part of base
            1 to this
        } else {
            intPower.toInt() to base
        }
    }
}
