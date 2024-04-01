import java.math.BigDecimal

class Exponent(
    val base: Expression,
    val power: Expression,
    isSimplified: Boolean = false
) : Expression(isSimplified) {
    override fun isolateIntPower(): Pair<Int, Expression> {
        val intPower = power.coefficient().toBigInteger()
        return if (intPower > Constants.INT_MAX) {  // Treat power as part of base
            Pair(1, this)
        } else {
            Pair(intPower.toInt(), base)
        }
    }

    override fun isReciprocal() = base is Value && power == NEGATIVE_ONE

    override fun simplify(): Expression = simplify(3)

    fun simplify(maxPower: Int): Expression {
        var base = base.checkSimplified()
        var power = power.checkSimplified()

        when {
            power == NEGATIVE_ONE -> return Exponent(base, power, true)
            power == ZERO || base == ONE -> return ONE
            power == ONE -> return base
        }
        if (base is Exponent) {  // Bring outer exponent down (Power Rule); (x^a)^b = x^(a*b)
            power = (base.power * power).simplify()
            base = base.base
        }
        if (base is Value && power is Value) {  // Simplify decimal value
            return simplifyAsFraction(base, power)
        }
        if (base is Product) { // Distribute across terms; (x/y)^a = x^a/y^a, etc.
            return Product(base.members.map { pow(it, power) }, true).apply { members.forEach { it.simplify() }}
        }
        if (base is Sum && power is Value && power.value <= maxPower.toBigDecimal()) {  // Work out manually
            val sumTerms = base.members.toMutableList()
            val (intPower, fracPower) = power.value.isolateIntegralPart()
            val root = if (fracPower notEquals BigDecimal.ZERO) {
                pow(base, Value(fracPower), true)
            } else null
            repeat ((intPower - BigDecimal.ONE).toInt()) {
                for (term in sumTerms) {
                    for (nextTerm in base.members) {  // FOIL
                        if (root != null) { // Distribute root
                            sumTerms.add(product(term, nextTerm, root))
                        } else {
                            sumTerms.add(term * nextTerm)
                        }
                    }
                }
            }
            return Sum(sumTerms).simplify() // Simplifies terms as well
        }
        // ...argument is Variable || argument is ElementaryFunction
        return Exponent(base, power, true)
    }

    override fun factor(): Expression {
        TODO("Not yet implemented")
    }

    override fun substitute(vars: VariableTable) =
        pow(base.substitute(vars), power.substitute(vars)).checkSimplified()

    override fun equals(other: Any?) = strictEquals(other) { base == it.base && power == it.power }
    override fun hashCode() = hash(base, power)
    override fun toString() = "($base^$power)"  // Debug

    fun simplifyAsFraction(base: Value, power: Value): Expression {
        if (base == ZERO && power.value > BigDecimal.ZERO) {
            return ZERO
        }
        val (intPower, _) = power.value.isolateIntegralPart()
        if (intPower > Constants.WHOLE_POWER_MAX) { // Too big to simplify
            return Exponent(base, power, true)
        }
        return Value(base.value.pow(intPower.toInt())).times(exp(power*log(base.value)), true)
    }
}
