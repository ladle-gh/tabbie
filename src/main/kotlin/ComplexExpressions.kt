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

// For a PowerMapList, list members are terms (in sum) and map members are factors (in product)
abstract class ComplexExpression(val members: ExpressionList, isSimplified: Boolean) : Expression(isSimplified)

/**
 * A series of subsequent multiplications. Divisions are expressed as multiplication of a term with a negative power.
 * Can be thought of as a discrete form of pi notation. An [ExpressionList] is used instead of two [Expression]s
 * for ease-of-coding and efficiency.
 * @see Sum
 */
class Product(members: ExpressionList, isSimplified: Boolean = false) : ComplexExpression(members, isSimplified) {
    override fun isNegative() = members.contains(NEGATIVE_ONE)

    fun removeNegative(): Expression {
        val newMembers = members - NEGATIVE_ONE
        if (newMembers.isEmpty()) {
            return ONE
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
            .isolateCoeffNumer()   // Simplify rational values (numerators) into coefficient; a*b = c
            .withFirst {
                if (it notEquals BigDecimal.ONE) {
                    coeffNumer = it
                }
            }
            .second
            .isolateCoeffDenom()  // Simplify reciprocals of rational values (denominators); 1/a * 1/b = 1/(ab)
            .withFirst {
                if (it notEquals BigDecimal.ONE) {
                    coeffDenom = it
                }
            }
            .withSecond { factors -> factors  // Simplify exponents of like bases; x^a * x^b = x^(a+b)
                .foldInPlace(mutableMapOf<Expression, MutableList<Expression>>()) { factor ->
                    if (factor is Exponent) { // Simplify
                        addOrModify(factor.base, ::mutableListOf) { powerSum -> powerSum.add(factor.power) }
                    } else {    // Multiply as-is
                        addOrModify(factor, ::mutableListOf) { powerSum -> powerSum.add(ONE) }
                    }
                }.forEach { (base, powerSum) ->
                    // Exponent.simplify() (in flattenMembers()) takes care of 1^x beforehand
                    Sum(powerSum)
                        .simplify()
                        .let { newPower ->
                            result.add(if (newPower == ONE) base else Exponent(base, newPower).checkSimplified())
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

    override fun substitute(vars: VariableTable) = Product(members.map { it.substitute(vars) })

    // Essentially just for the sums, then whatever is outside the sums gets simplified
    override fun factor(): Expression {
        return simplifyOuter()
            .isolateInstances<Sum>()
            .withBoth { sums, nonSums -> Product(sums.map { it.factor() } + nonSums).simplify() }
    }

    override fun equals(other: Any?) = strictEquals(other) { members.toSet() == it.members.toSet() }
    override fun hashCode() = hash(*members.toTypedArray())
    override fun toString() = members.joinToString("*", "(", ")") // Debug

    companion object {
        private fun distribute(basicSimplify: ExpressionList): Expression {
            // At this point, all that's left are Sums, ElementaryFunction's, Variable's, and a coefficient
            return basicSimplify
                .isolateInstances<Sum>()
                .withBoth { sums, nonSums ->
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
class Sum(members: ExpressionList, isSimplified: Boolean = false) : ComplexExpression(members, isSimplified) {
    fun toPowerMapList(): PowerMapList {
        return members
            .map { if (it is Product) it.members else listOf(it) }
            .filterIsInstance<Product>()
            .let { terms ->
                terms.foldInPlace(ArrayList(terms.size)) { term ->
                    val powerMap = term.members
                        .foldInPlace(mutableMapOf<Int, MutableList<Expression>>()) { factor ->
                            factor
                                .isolateIntPower()    // Int (power) and Expression (base)
                                .withBoth { intPower, base ->
                                    addOrModify(intPower, { mutableListOf() }) {
                                        it.add(base)
                                    }
                                }
                        }
                    add(powerMap)
                }
            }

    }

    override fun simplify(): Expression {
        if (members.size == 1) {
            return members.single()
        }
        val termsToCoeffs = mutableMapOf<Expression?, MutableBigDecimal>()
        flattenMembers()
            .isolateInstances<Value>()    // Simplify rational values (constants)
            .withFirst { constants ->
                constants
                    .forEach { constant ->
                        termsToCoeffs.addOrModify(null, ::MutableBigDecimal) { it.value += constant.value }
                    }
            }.withSecond { terms ->
                terms
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

    override fun substitute(vars: VariableTable) = Sum(members.map { it.substitute(vars) })

    private fun gcfFactor(): ComplexExpression {
        val coeffNumers = mutableListOf<BigDecimal>()
        val coeffDenoms = mutableListOf<BigDecimal>()
        val factoredTerms = mutableListOf<ExpressionList>()
        flattenMembers()
            .isolateInstances<Product>()
            .withFirst { terms ->
                terms
                    .forEach { term ->
                        term.members
                            .isolateCoeffNumer()
                            .withFirst { coeffNumers.add(it) }
                            .second
                            .isolateCoeffDenom()
                            .withFirst { coeffDenoms.add(it) }
                            .withSecond { factoredTerms.add(it) }
                    }
            }

        val gcfNumer = gcf(coeffNumers)
        val gcfDenom = gcf(coeffDenoms)
        return if (gcfNumer.first notEquals BigDecimal.ONE || gcfDenom.first notEquals BigDecimal.ONE) {
            val inside = gcfNumer
                .second
                .zip(gcfDenom.second) { numer, denom -> Value(numer).times(Value(denom).reciprocal(), true) }
                .zip(factoredTerms) { coeff, factor -> Product(factor + coeff, true) }
            val gcf = (Value(gcfNumer.first) / Value(gcfDenom.first)).simplify()
            gcf.times(Sum(inside, true), true)
        } else {
            this
        }
    }

    override fun factor(): Expression {
        return this
            .gcfFactor()
            .quadraticFactor()
            //.syntheticFactor() TODO
            //.binomialFactor() TODO
    }

    override fun equals(other: Any?) = strictEquals(other) { members.toSet() == it.members.toSet() }
    override fun hashCode() = hash(*members.toTypedArray()) + 1
    override fun toString() = members.joinToString("+", "(", ")")   // Debug

    companion object {
        private data class MutableBigDecimal(var value: BigDecimal = BigDecimal.ZERO)

        private val NO_TERM_INDICES = listOf<Int>()

        private val DETERMINANT = run {
            val fourAC = product(FOUR, A, C, isSimplified = true)
            B.squared(true).plus(fourAC, true).sqrt(true)
        }
        private val QUADRATIC_FORMULA_POSITIVE = quadraticFormula(Expression::plus)
        private val QUADRATIC_FORMULA_NEGATIVE = quadraticFormula(Expression::minus)

        private inline fun quadraticFormula(plusOrMinus: Expression.(Expression, Boolean) -> Expression): Expression {
            val twoA = TWO.times(A, true)
            return B.plusOrMinus(DETERMINANT.sqrt(true), true).div(twoA, true)
        }

        /*
        In general, ax^2+bx+c = a(x-q(a,b,c))(x-Q(a,b,c)), where
        q(a,b,c) = (-b+sqrt(b^2+4ac))/2a
        Q(a,b,c) = (-b-sqrt(b^2+4ac))/2a
         */
        private fun ComplexExpression.quadraticFactor(): Expression {
            val gcf: Expression
            val inside: Sum
            if (this is Product) {
                gcf = members.first()
                inside = members[1] as Sum
            } else {
                gcf = ONE
                inside = this as Sum
            }
            val powerMaps = inside.toPowerMapList()
            val basesPower2 = powerMaps.getBases(2)
            if (basesPower2.isEmpty()) {
                return this
            }
            for ((base, termIndices) in basesPower2) {
                val result = tryQuadraticFactor(powerMaps, base, termIndices)
                if (result.second.isNotEmpty()) {
                    return quadraticFactor(base, result.map { it.toExpression() })
                }
            }
            return this
        }

        private fun quadraticFactor(x: Expression, abc: Triple<Expression,Expression,Expression>): Expression {
            val vars = mapOf(
                'x' to x,   'a' to abc.first,
                'b' to abc.second,   'c' to abc.third
            )
            val firstFactor = X.minus(QUADRATIC_FORMULA_POSITIVE.substitute(vars))
            val secondFactor = X.minus(QUADRATIC_FORMULA_NEGATIVE.substitute(vars))
            return product(A, firstFactor, secondFactor, isSimplified = true)
        }

        private fun tryQuadraticFactor(
            mappings: PowerMapList,
            desiredBase: Expression,
            termIndices: TermIndexList
        ): Triple<PowerMapList, PowerMapList, PowerMapList> {
            val factoredPower2: PowerMapList    // All to be multiplied by x^2
            val factoredPower1: PowerMapList    // All to be multiplied by x
            var remaining: PowerMapList
            mappings    // Factor out x^2
                .factorOut(2, desiredBase, termIndices)
                .withFirst { factoredPower2 = it }
                .withSecond { remaining = it }
            remaining   // Factor out x
                .factorOut(1, desiredBase, NO_TERM_INDICES)
                .withFirst { factoredPower1 = it }  // If empty, factoring fails
                .withSecond { remaining = it }
            return Triple(factoredPower2, factoredPower1, remaining)
        }

        private fun PowerMapList.getBases(intPower: Int): Map<Expression, TermIndexList> {
            val bases = mutableMapOf<Expression, MutableList<Int>>()
            forEachIndexed { termIndex, mapping ->
                mapping[intPower]?.forEach { base ->
                    bases.addOrModify(base, { mutableListOf() }) { it.add(termIndex) }
                }
            }
            return bases
        }

        private fun PowerMapList.factorOut(
            intPower: Int,
            base: Expression,
            termIndices: TermIndexList
        ): Pair<PowerMapList, PowerMapList> {
            val partition = if (termIndices.isEmpty()) {
                partition { it[intPower]?.contains(base) ?: false }
            } else {
                isolateAt { it in termIndices }
            }
            return partition
                .withBoth { toFactor, intact ->
                    val factored = toFactor.map { it.filter { (ip, b) -> ip != intPower || b != base } }
                    factored to intact  // Take out base
                }
        }

        private fun PowerMapList.toExpression(): Expression {
            val sumMembers = foldInPlace(mutableListOf<Expression>()) { powerMap ->
                val termMembers = powerMap.asIterable().foldInPlace(mutableListOf<Expression>()) { (intPower, bases) ->
                    if (intPower != 1) {
                        bases.forEach { add(pow(it, Value(intPower.toBigDecimal()))) }
                    } else {
                        bases.forEach { add(it) }
                    }
                }
                add(Product(termMembers, true))
            }
            return Sum(sumMembers, true)
        }
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
