import Expression.Companion.NEGATIVE_ONE
import Expression.Companion.ONE
import internal.*
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Objects.hash

/**
 * A series of subsequent multiplications. Divisions are expressed as multiplication of a term with a negative power.
 * Can be thought of as a discrete form of pi notation. An [ExpressionList] is used instead of two [Expression]s
 * for ease-of-coding and efficiency.
 * @see Sum
 */
open class Product(members: ExpressionList) : ComplexExpression(members), CanBeNegative {
    final override fun isNegative() = members.contains(NEGATIVE_ONE)

    final override fun removeNegative(): Expression {
        val newMembers = members - NEGATIVE_ONE
        if (newMembers.isEmpty()) {
            return ONE
        }
        return newMembers.singleOrNull() ?: Product(newMembers)
    }

    final override fun simplify(foilPower: Int) = distribute(simplifyOuter(foilPower), foilPower)

    // Simplifies, but does not distribute terms across sums
    private fun simplifyOuter(foilPower: Int = 0): SimpleExpressionList {
        if (members.size == 1) {    // Single term (0 operations)
            return members.map { it.ensureSimplified(foilPower) }
        }
        val result = mutableListOf<SimpleExpression>()
        var coeffNumer = BigDecimal.ONE
        var coeffDenom = BigDecimal.ONE
        flattenMembers() // x*(y*z) = x*y*z
            .isolateCoeffNumer()   // Simplify rational values (numerators) into coefficient; a*b = c
            .withFirst {
                if (it isNotValue BigDecimal.ONE) {
                    coeffNumer = it
                }
            }
            .second
            .isolateCoeffDenom()  // Simplify reciprocals of rational values (denominators); 1/a * 1/b = 1/(ab)
            .withFirst {
                if (it isNotValue BigDecimal.ONE) {
                    coeffDenom = it
                }
            }
            .withSecond { factors -> factors  // Simplify exponents of like bases; x^a * x^b = x^(a+b)
                .mutableFold(mutableMapOf<SimpleExpression, MutableList<Expression>>()) { factor ->
                    if (factor is SimpleExponent) { // Simplify
                        this[factor.base] = this[factor.base] +! factor.power
                    } else {    // Multiply as-is
                        this[factor] = this[factor] +! ONE
                    }
                }
                .forEach { (base, powerSum) ->
                    // Exponent.simplify() (in flattenMembers()) takes care of 1^x beforehand
                    Sum(powerSum)
                        .simplify(foilPower)
                        .let { newPower ->
                            result.add(if (newPower == ONE) base else base.pow(newPower).ensureSimplified())
                        }
                }
            }
        when {  // Finish simplification of coefficient; a*b = c given a%b = 0
            (coeffNumer % coeffDenom) isValue BigDecimal.ZERO -> {
                (coeffNumer / coeffDenom)
                    .takeIf { it isNotValue BigDecimal.ONE }
                    ?.let { result.add(Value(it)) }
            }
            coeffNumer isNotValue BigDecimal.ONE -> {
                val fracCoeff = if (coeffDenom isValue BigDecimal.ONE) {
                    Value(coeffNumer)
                } else {
                    Fraction(coeffNumer, coeffDenom).toExpression()
                }
                result.add(fracCoeff)
            }
            coeffDenom isNotValue BigDecimal.ONE -> result.add(Value(coeffDenom).simpleReciprocal())
        }
        // TODO automate simplifications according to simple rules
        // TODO add rules for elementary functions
        return result
    }

    // Essentially just for the sums, then whatever is outside the sums gets simplified
    final override fun factor(): Expression {
        return simplifyOuter(0)
            .isolateInstances<Sum>()
            .withBoth { sums, nonSums -> Product(sums.map { it.factor() } + nonSums).simplify() }
    }

    final override fun equals(other: Any?) = strictEquals(other) { members.toSet() == it.members.toSet() }
    final override fun hashCode() = hash(*members.toTypedArray())
    final override fun toString() = members.joinToString("*", "(", ")") // Debug

    /**
     * @return flattened & simplified member list
     */
    internal fun flattenMembers(): SimpleExpressionList {
        val flat = mutableListOf<SimpleExpression>()
        members
            .map { it.ensureSimplified() }
            .forEach { if (it is Product) flat.addAll(it.flattenMembers()) else flat.add(it) }
        return flat
    }

    companion object {
        private fun distribute(basicSimplify: SimpleExpressionList, foilPower: Int = 0): SimpleExpression {
            // At this point, all that's left are Sums, ElementaryFunction's, Variable's, and a coefficient
            return basicSimplify
                .isolateInstances<SimpleSum>()
                .withBoth { sums, nonSums ->
                    if (sums.isNotEmpty()) {    // Distribute coefficient; a(x + y) = ax + ay
                        return Sum(sums.fold(nonSums) { last, it -> it.distribute(last) }).simplify(foilPower)
                    }
                    nonSums.singleOrNull() ?: SimpleProduct(nonSums)
                }
        }
    }
}

class SimpleProduct(override val members: SimpleExpressionList) : Product(members), SimpleComplexExpression {
    override fun substitute(vars: VariableTable) = Product(members.map { it.substitute(vars) }).simplify()
    override fun isReciprocal() = false
    override fun isolateIntPower() = 1 to this
    override fun flatten() = SimpleProduct(flattenMembers())

    override fun isolateCoeff(): Pair<BigDecimal,SimpleExpression> {
        val coeff = members.find { it is Value } as Value? ?: return BigDecimal.ONE to this
        return coeff.value to SimpleProduct(members - coeff)
    }

    override fun evaluate(precision: Int, foilPower: Int): SimpleExpression {
        var numer = ONE
        var denom = ONE
        val newMembers = members
            .map { it.evaluate(precision) }
            .mutableFold(mutableListOf<SimpleExpression>()) {
                when {  // There is only one of each fraction part, since members are simplified beforehand
                    it is Value -> numer = it
                    it is SimpleExponent && it.isReciprocal() && it.base is Value -> denom = it.base
                    else -> add(it)
                }
        }
        if (numer == ONE && denom == ONE) {
            return Product(newMembers).simplify(foilPower)
        }
        newMembers.add(Value(numer.value.divide(denom.value, MathContext(precision, RoundingMode.HALF_UP))))
        return Product(newMembers).simplify(foilPower)
    }

    override fun toFraction(): Fraction {
        try {
            val numer = (members[0] as Value).value
            val denom = ((members[1] as SimpleExponent).base as Value).value
            return Fraction(numer, denom)
        } catch (e: ClassCastException) {
            throw IllegalCallerException("Caller is not a valid Fraction", e)
        }
    }

    companion object {
        val NO_MEMBERS = SimpleProduct(listOf())
    }
}
