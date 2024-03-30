import java.math.BigDecimal

/**
 * @return flattened member list without expressions of type [T]
 */
private inline fun <reified T : ComplexExpression> T.flattenMembers(): ExpressionList {
    val flat = mutableListOf<Expression>()
    members
        .map { it.checkSimplified() }
        .forEach { if (it is T) flat.addAll(it.members) else flat.add(it) }
    return flat
}

abstract class ComplexExpression(val members: ExpressionList, isSimplified: Boolean) : Expression(isSimplified) {
    fun advancedFactor(factors: List<ExpressionList>): Expression {
        val gcf: Expression
        val inside: Sum
        if (this is Product) {
            gcf = members.first()
            inside = members[1] as Sum
        } else {
            gcf = Value.ONE
            inside = this as Sum
        }
        factors
            .powerMappings()
            .quadraticFactor()
        //syntheticDivisionFactor()
        //binomialFactor()
    }

    companion object {
        fun List<PowerMapping>.quadraticFactor() {
            val squareFactors = mutableMapOf<Expression, MutableList<Int>>()
            forEachIndexed { termIndex, mapping ->
                mapping[2 /* intPower */]?.forEach { base ->
                    squareFactors.addOrModify(base, { mutableListOf() }) { it.add(termIndex) }
                }
            }
            if (squareFactors.isEmpty()) {
                return this
            }
            for ((base, locations) in squareFactors) {
                val result = tryQuadraticFactor(this, base, locations)
                if (result == VALID) {
                    return result
                }
            }
            return this
        }

        private fun List<PowerMapping>.factorOut(intPower: Int, base: Expression): List<PowerMapping> {

        }

        private fun tryQuadraticFactor(mappings: List<PowerMapping>, desiredBase: Expression, locations: List<Int>) {
            if (locations.size > 1) {   // Factor out x^2

            }
            val factors = mutableMapOf<Expression, MutableList<Int>>()
            mappings.forEachIndexed { termIndex, mapping ->
                mapping[1]?.forEach { base ->
                    if (base == desiredBase) {
                        factors.addOrModify(base, { mutableListOf() }) { it.add(termIndex) }
                    }
                }
            }
            if (factors.isEmpty()) {
                return this
            }
            if (factors.size > 1) { // Factor out x

            }

        }
    }
}

/**
 * A series of subsequent multiplications. Divisions are expressed as multiplication of a term with a negative power.
 * Can be thought of as a discrete form of pi notation. An [ExpressionList] is used instead of two [Expression]s
 * for ease-of-coding and efficiency.
 * @see Sum
 */
// TODO add synthetic (polynomial) division
class Product(members: ExpressionList, isSimplified: Boolean = false) : ComplexExpression(members, isSimplified) {
    override fun isNegative() = members.contains(Value.NEGATIVE_ONE)

    fun removeNegative(): Expression {
        val newMembers = members - Value.NEGATIVE_ONE
        if (newMembers.isEmpty()) {
            return Value.ONE
        }
        return newMembers.singleOrNull() ?: Product(newMembers, true)
    }

    override fun coefficient() = (members.find { it is Value } as Value?)?.value ?: BigDecimal.ONE

    override fun simplify() = distribute(simplifyOuter())

    // Simplifies, but does not distribute terms across sums
    private fun simplifyOuter(): ExpressionList {
        if (members.size == 1) {    // Single term (0 operations)
            return members
        }
        val result = mutableListOf<Expression>()
        var coeffNumer = BigDecimal.ONE
        var coeffDenom = BigDecimal.ONE
        flattenMembers() // x*(y*z) = x*y*z
            .splitCoeffNumer()   // Simplify rational values (numerators) into coefficient; a*b = c
            .forTrue {
                if (it notEquals BigDecimal.ONE) {
                    coeffNumer = it
                }
            }
            .forFalse()
            .splitCoeffDenom()  // Simplify reciprocals of rational values (denominators); 1/a * 1/b = 1/(ab)
            .forTrue {
                if (it notEquals BigDecimal.ONE) {
                    coeffDenom = it
                }
            }
            .forFalse { factors -> factors  // Simplify exponents of like bases; x^a * x^b = x^(a+b)
                .foldInPlace(mutableMapOf<Expression, MutableList<Expression>>()) { factor ->
                    if (factor is Exponent) { // Simplify
                        addOrModify(factor.base, ::mutableListOf) { powerSum -> powerSum.add(factor.power) }
                    } else {    // Multiply as-is
                        addOrModify(factor, ::mutableListOf) { powerSum -> powerSum.add(Value.ONE) }
                    }
                }.forEach { baseToPowerSum ->
                    // Exponent.simplify() (in flattenMembers()) takes care of 1^x beforehand
                    Sum(baseToPowerSum.value)
                        .simplify()
                        .let { newPower ->
                            val base = baseToPowerSum.key
                            result.add(if (newPower == Value.ONE) base else Exponent(base, newPower).checkSimplified())
                        }
                }
            }
        when {  // Finish simplification of coefficient; a*b = c given a%b = 0
            (coeffNumer % coeffDenom) equals BigDecimal.ZERO -> result.add(Value(coeffNumer / coeffDenom))
            coeffNumer notEquals BigDecimal.ONE -> {
                val fracCoeff = if (coeffDenom equals BigDecimal.ONE) {
                    Value(coeffNumer)
                } else {
                    Value(coeffNumer).div(Value(coeffDenom), true)
                }
                result.add(fracCoeff)
            }
            coeffDenom notEquals BigDecimal.ONE -> result.add(Value(coeffDenom).reciprocal())
        }
        // TODO automate simplifications according to simple rules
        // TODO add rules for elementary functions
        return result
    }

    override fun substitute(varl: Char, sub: BigDecimal) = Product(members.map { it.substitute(varl, sub) })

    // Essentially just for the sums, then whatever is outside the sums gets simplified
    override fun factor(): Expression {
        return simplifyOuter()
            .splitIsInstance<Sum>()
            .forBoth { sums, nonSums -> Product(sums.map { it.factor() } + nonSums).simplify() }
    }

    override fun equals(other: Any?) = strictEquals(other) { members.toSet() == it.members.toSet() }
    override fun hashCode() = hash(*members.toTypedArray())
    override fun toString() = members.joinToString("*", "(", ")") // Debug

    companion object {
        private fun distribute(basicSimplify: ExpressionList): Expression {
            // At this point, all that's left are Sums, ElementaryFunction's, Variable's, and a coefficient
            return basicSimplify
                .splitIsInstance<Sum>()
                .forBoth { sums, nonSums ->
                    if (sums.isNotEmpty()) {    // Distribute coefficient; a(x + y) = ax + ay
                        return Sum(sums.fold(nonSums) { last, it -> it.distribute(last) }).simplify()
                    }
                    nonSums.singleOrNull() ?: Product(nonSums, true)
                }
        }
    }
}

/**
 * A series of subsequent additions. Subtractions are expressed as additions of a term with a negative coefficient.
 * Discrete form of sigma-notation. An [ExpressionList] is used instead of two [Expression]s
 * for ease-of-coding and efficiency.
 * @see Product
 */
// TODO add avanced factoring techniques
class Sum(members: ExpressionList, isSimplified: Boolean = false) : ComplexExpression(members, isSimplified) {
    override fun simplify(): Expression {
        if (members.size == 1) {
            return members.single()
        }
        val termsToCoeffs = mutableMapOf<Expression?, MutableBigDecimal>()
        flattenMembers()
            .splitIsInstance<Value>()    // Simplify rational values (constants)
            .forTrue { constants -> constants
                .forEach { constant ->
                    termsToCoeffs.addOrModify(null, ::MutableBigDecimal) { it.value += constant.value }
                }
            }.forFalse { terms -> terms
                .forEach { term ->
                    termsToCoeffs.addOrModify(term, ::MutableBigDecimal) { it.value += BigDecimal.ONE }
                }
            }
        return Sum(termsToCoeffs.map {
            val (factor, coeff) = it
            when {
                factor == null -> Value(coeff.value)
                coeff.value equals BigDecimal.ONE -> factor // 1x = x
                else -> Value(coeff.value).times(factor, true)
            }
        }, true)
    }

    // a(x + y) = ax + ay
    fun distribute(termMembers: ExpressionList) = members.map { Product(termMembers + it).simplify() }

    override fun substitute(varl: Char, sub: BigDecimal) = Sum(members.map { it.substitute(varl, sub) })

    // TODO add rules for quadratic equations, completing the square, etc.
    override fun factor(): Expression {
        val coeffNumers = mutableListOf<BigDecimal>()
        val coeffDenoms = mutableListOf<BigDecimal>()
        val factors = mutableListOf<ExpressionList>()
        flattenMembers()
            .splitIsInstance<Product>()
            .forTrue {  terms -> terms
                .forEach { term ->
                    term.members
                        .splitCoeffNumer()
                        .forTrue { coeffNumers.add(it) }
                        .forFalse()
                        .splitCoeffDenom()
                        .forTrue { coeffDenoms.add(it) }
                        .forFalse { factors.add(it) }
                }
            }

        val gcfNumer = gcf(coeffNumers)
        val gcfDenom = gcf(coeffDenoms)
        val gcfFactored = if (gcfNumer.first notEquals BigDecimal.ONE || gcfDenom.first notEquals BigDecimal.ONE) {
            val inside = gcfNumer
                .second
                .zip(gcfDenom.second) { numer, denom -> Value(numer).times(Value(denom).reciprocal(), true) }
                .zip(factors) { coeff, factor -> Product(factor + coeff, true) }
            val gcf = (Value(gcfNumer.first) / Value(gcfDenom.first)).simplify()
            gcf.times(Sum(inside, true), true)
        } else this
        return gcfFactored.advancedFactor(factors)
    }

    override fun equals(other: Any?) = strictEquals(other) { members.toSet() == it.members.toSet() }
    override fun hashCode() = hash(*members.toTypedArray()) + 1
    override fun toString() = members.joinToString("+", "(", ")")   // Debug

    companion object {
        private data class MutableBigDecimal(var value: BigDecimal = BigDecimal.ZERO)
    }
}

/**
 * @return First: greatest common factor (GCF); Second: the supplied coefficients divided by the GCF
 */
private fun gcf(numbers: List<BigDecimal>): Pair<BigDecimal, List<BigDecimal>> {
    tailrec fun gcd(a: BigDecimal, b: BigDecimal): BigDecimal {
        return if (b equals BigDecimal.ZERO) a else gcd(b, a % b)
    }

    var result = numbers[0]
    for (i in 1..<numbers.size) {
        result = gcd(result, numbers[i])
    }

    return result to (if (result equals BigDecimal.ONE) numbers else numbers.map { it / result })
}
