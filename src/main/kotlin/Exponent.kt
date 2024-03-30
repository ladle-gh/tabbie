import java.math.BigDecimal

private val MAX_WHOLE_POWER = BigDecimal(999999999)

class Exponent(
    val base: Expression,
    val power: Expression,
    isSimplified: Boolean = false
) : Expression(isSimplified) {
    override fun splitIntPower(): Split<Int, Expression> {
        val intPower = power.coefficient().toBigInteger()
        return if (intPower > INTEGER_MAX) {  // Treat power as part of base
            Split(1, this)
        } else {
            Split(intPower.toInt(), base)
        }
    }

    override fun isReciprocal() = base is Value && power == Value.NEGATIVE_ONE

    override fun simplify(): Expression = simplify(3)

    fun simplify(maxPower: Int): Expression {
        var base = base.checkSimplified()
        var power = power.checkSimplified()

        when {
            power == Value.NEGATIVE_ONE -> return Exponent(base, power, true)
            power == Value.ZERO || base == Value.ONE -> return Value.ONE
            power == Value.ONE -> return base
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
            val (intPower, fracPower) = power.value.splitDecimal()
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

    override fun substitute(varl: Char, sub: BigDecimal) =
        pow(base.substitute(varl, sub), power.substitute(varl, sub)).checkSimplified()

    override fun equals(other: Any?) = strictEquals(other) { base == it.base && power == it.power }
    override fun hashCode() = hash(base, power)
    override fun toString() = "($base^$power)"  // Debug

    fun simplifyAsFraction(base: Value, power: Value): Expression {
        if (base == Value.ZERO && power.value > BigDecimal.ZERO) {
            return Value.ZERO
        }
        val (intPower, _) = power.value.splitDecimal()
        if (intPower > MAX_WHOLE_POWER) {   // Too big to simplify
            return Exponent(base, power, true)
        }
        return Value(base.value.pow(intPower.toInt())).times(exp(power*log(base.value)), true)
    }
}
