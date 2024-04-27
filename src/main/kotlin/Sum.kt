import Expression.Companion.A
import Expression.Companion.X
import internal.*
import java.math.BigDecimal
import java.util.Objects.hash

/**
 * A series of subsequent additions. Subtractions are expressed as additions of a term with a negative coefficient.
 * Discrete form of sigma-notation. An [ExpressionList] is used instead of two [Expression]s
 * for ease-of-coding and efficiency.
 * @see Product
 */
open class Sum(members: List<Expression>) : ComplexExpression(members) {
    override fun simplify(foilPower: Int): SimpleExpression {
        if (members.size == 1) {
            return members.single().ensureSimplified(foilPower)
        }
        val termsToCoeffs = mutableMapOf<SimpleExpression, BigDecimalReference>()
        flattenMembers()
            .partitionInstancesOf<Value>()    // Simplify rational values (constants)
            .withFirst { constants ->
                constants
                    .forEach { constant ->
                        termsToCoeffs[Expression.ONE] = termsToCoeffs[Expression.ONE] + !constant.value
                    }
            }
            .withSecond { terms ->
                terms
                    .forEach { term ->
                        termsToCoeffs[term] = termsToCoeffs[term] + !BigDecimal.ONE
                    }
            }
        return SimpleSum(termsToCoeffs.map { (term, coeff) ->
            when {
                term == Expression.ONE -> Value(coeff.value)
                coeff.value isValue BigDecimal.ONE -> term  // 1x = x
                else -> Value(coeff.value) simpleTimes term
            }
        })
    }

    // TODO if degree is 2, use quadratic factor, else use synethetic or diff/sum formulas
    override fun factor(): Expression {
        /*
        In general, ax^2+bx+c = a(x-q(a,b,c))(x-Q(a,b,c)), where
        q(a,b,c) = (-b+sqrt(b^2+4ac))/2a
        Q(a,b,c) = (-b-sqrt(b^2+4ac))/2a
         */
        fun Pair<SimpleExpression, SimpleExpression>.quadraticFactor(): Pair<SimpleExpression, SimpleExpression> {
            fun getQuadraticFactor(
                x: SimpleExpression,
                abc: Triple<SimpleExpression, SimpleExpression, SimpleExpression>
            ): SimpleExpression {
                val vars = mapOf(
                    'x' to x, 'a' to abc.first,
                    'b' to abc.second, 'c' to abc.third
                )
                val firstFactor = (X simpleMinus QUADRATIC_POSITIVE).substitute(vars)
                //TODO debug
                println(QUADRATIC_POSITIVE.substitute(vars))
                println(QUADRATIC_NEGATIVE.substitute(vars))
                val secondFactor = (X simpleMinus QUADRATIC_NEGATIVE).substitute(vars)
                val a = A.substitute(vars)
                return if (a == Expression.ONE) {
                    firstFactor simpleTimes secondFactor
                } else {
                    simpleProduct(a, firstFactor, secondFactor)
                }
            }

            /**
             * @return First:
             */
            fun tryQuadraticFactor(
                mappings: PowerMapList,
                desiredBase: SimpleExpression,
                termIndices: List<Int>
            ): Pair<SimpleExpression, PowerMapListTriple> {
                val a: PowerMapList    // Sequence to be multiplied by x^2
                val b: PowerMapList    // Sequence to be multiplied by x
                var remaining: PowerMapList
                mappings    // Factor out x^2
                    .factorOut(2, desiredBase, termIndices)
                    .withFirst { a = it }
                    .withSecond { remaining = it }
                remaining   // Factor out x
                    .factorOut(1, desiredBase, listOf())
                    .withFirst { b = it }
                    .withSecond { remaining = it }  // If empty, factoring fails
                val c = remaining.apply {
                    if (isEmpty()) {
                        listOf()
                    } else {
                        find { it[1]?.singleOrNull()?.let { base -> base is Value } ?: false }?.let { listOf(it) }
                            ?: listOf()
                    }
                }
                return (remaining - c.toSet()).toExpression() to Triple(a, b, c)
            }

            val inside = second as SimpleSum
            val powerMaps = inside.toTermList().toPowerMapList()
            val basesPower2 = powerMaps.getCommonBases(2)
            if (basesPower2.isEmpty()) {
                return this
            }
            for ((base, termIndices) in basesPower2) {
                val result = tryQuadraticFactor(powerMaps, base, termIndices)
                if (result.second.third.isNotEmpty()) {
                    val newInside = getQuadraticFactor(base, result.second.map { it.toExpression() })
                    val first = first
                    val factored = if (
                        first == Expression.ONE ||
                        first is ComplexExpression && first.members.isEmpty()
                    ) {  // Handles empty Product too
                        newInside
                    } else {
                        first simpleTimes newInside
                    }
                    return factored to result.first
                }
            }
            return this
        }

        return gcfFactor(flattenMembers(), includeVarExpr = true)
            //.syntheticFactor()
            .quadraticFactor().let { (x, _) -> (x) }

        //.binomialFactor() TODO
        //sum/difference of squares/cubes
    }

    override fun equals(other: Any?) = strictEquals(other) { members.toSet() == it.members.toSet() }
    override fun hashCode() = hash(*members.toTypedArray()) + 1
    override fun toString() = members.joinToString("+", "(", ")")   // Debug

    /**
     * @return flattened & simplified member list
     */
    internal fun flattenMembers(): List<SimpleExpression> {
        val flat = mutableListOf<SimpleExpression>()
        members
            .map { it.ensureSimplified() }
            .forEach { if (it is Sum) flat.addAll(it.flattenMembers()) else flat.add(it) }
        return flat
    }
}

class SimpleSum(override val members: List<SimpleExpression>) : Sum(members), SimpleComplexExpression {
    override fun flatten() = SimpleSum(flattenMembers())

    override fun evaluate(precision: Int, foilPower: Int): SimpleExpression {
        TODO("Not yet implemented")
    }

    override fun substitute(vars: Map<Char, SimpleExpression>) = Sum(members.map { it.substitute(vars) }).simplify()

    override fun partitionCoeff(): Pair<BigDecimal, SimpleExpression> {
        val gcfFactored = gcfFactor(members, includeVarExpr = false)
        if (gcfFactored.first != Expression.ONE) { // Coefficient (GCF) exists
            if (gcfFactored.first is Value) {
                return (gcfFactored.first as Value).value to gcfFactored.second
            }
            val (coeff, inside) = gcfFactored.first.partitionCoeff()
            return coeff to (inside simpleTimes gcfFactored.second)
        }
        return BigDecimal.ONE to this
    }

    override fun isReciprocal() = false

    override fun partitionIntPower() = 1 to this

    fun toTermList(): List<List<SimpleExpression>> {
        return members.map { if (it is SimpleProduct) it.members else listOf(it) }
    }

    // a(x + y) = ax + ay
    fun distribute(termMembers: List<SimpleExpression>): List<SimpleExpression> {
        val flat = termMembers.flatMap { if (it is SimpleProduct) it.members else listOf(it) }
        val isolateRecips = flat.partition { it.isReciprocal() }
        if (isolateRecips.first.isEmpty()) {
            return members.map { Product(flat + it).simplify() }
        }
        if (isolateRecips.second.isEmpty()) {
            return listOf(SimpleProduct(isolateRecips.first + this))
        }
        return listOf(Product(isolateRecips.first + Sum(members.map { Product(isolateRecips.second + it) })).simplify())
    }
}

private fun List<List<SimpleExpression>>.toPowerMapList(): PowerMapList {
    return this
        .accumulate(ArrayList(size)) { term ->
            val powerMap = term
                .accumulate(mutableMapOf<Int, MutableList<SimpleExpression>>()) { factor ->
                    factor
                        .partitionIntPower()
                        .let { (intPower, base) ->
                            this[intPower] = this[intPower] + !base
                        }
                }
            add(powerMap)
        }
}

private fun <T : Map<Int, List<SimpleExpression>>> List<T>.toExpression(): SimpleExpression {
    val sumMembers = accumulate(mutableListOf<Expression>()) { powerMap ->
        val termMembers = powerMap.asIterable().accumulate(mutableListOf<Expression>()) inner@{ (intPower, bases) ->
            if (intPower != 1) {
                bases.forEach { this@inner.add(Exponent(it, Value(intPower.toBigDecimal()))) }
            } else {
                bases.forEach { this@inner.add(it) }
            }
        }
        add(Product(termMembers))
    }
    return Sum(sumMembers).simplify()
}

/**
 * @return First: greatest common factor (GCF); Second: the supplied coefficients divided by the GCF
 */
private fun gcf(numbers: List<BigDecimal>): Pair<BigDecimal, List<BigDecimal>> {
    if (numbers.isEmpty()) {
        return BigDecimal.ONE to listOf()
    }
    var result = numbers[0]
    for (i in 1..<numbers.size) {
        result = gcd(result, numbers[i])
    }
    return result to (if (result isValue BigDecimal.ONE) numbers else numbers.map { it / result })
}

private val ONLY_ONE = listOf(Expression.ONE)

private fun gcfFactor(simpleMembers: List<SimpleExpression>,
    includeVarExpr: Boolean): Pair<SimpleExpression, SimpleExpression> {
    val coeffNumers = mutableListOf<BigDecimal>()
    val coeffDenoms = mutableListOf<BigDecimal>()
    val semiFactoredTerms = mutableListOf<List<SimpleExpression>>()
    simpleMembers
        .partitionInstancesOf<SimpleProduct>()
        .withFirst { terms ->
            terms
                .forEach { term ->
                    term
                        .members
                        .partitionCoeffNumer()
                        .withFirst { coeffNumers.add(it) }
                        .second
                        .partitionCoeffDenom()
                        .withFirst { coeffDenoms.add(it) }
                        .withSecond { semiFactoredTerms.add(it) }
                }
        }
        .withSecond { nonProducts ->
            semiFactoredTerms.addAll(nonProducts.map { listOf(it) })
        }
    val gcfNumer = gcf(coeffNumers)
    val gcfDenom = gcf(coeffDenoms)
    val powerMaps = semiFactoredTerms.toPowerMapList().map { it.toMutableMap() }
    val gcfVarExpr: SimpleExpression
    if (includeVarExpr) {
        val gcfFactors = powerMaps
            .flatMap { it.values.flatten() }
            .toSet()
            .accumulate(mutableListOf<SimpleExpression>()) { uniqueTermFactor ->
                if (powerMaps.all { powerMap ->
                        powerMap.values.any {
                            it.contains(uniqueTermFactor)
                        }
                    }) { // Is GCF factor
                    val gcfPowers = powerMaps.map {
                        it.entries.find { (_, commonBases) -> commonBases.contains(uniqueTermFactor) }?.key ?: 0
                    }
                    val minPower = gcfPowers.minOf { it }
                    if (minPower == 1) {
                        add(uniqueTermFactor)
                    } else {
                        add(uniqueTermFactor.simplePow(Value(minPower.toBigDecimal())))
                    }
                    for ((powerMap, gcfPower) in powerMaps.zip(gcfPowers)) {
                        val commonBases = powerMap.getValue(gcfPower)
                        if (commonBases.singleOrNull() == null) {    // Multiple common bases
                            powerMap[gcfPower] = commonBases - uniqueTermFactor
                        } else {    // GCF base is only common base
                            powerMap -= gcfPower
                        }
                        if (gcfPower != minPower) { // Factoring does not remove factor entirely
                            val residualPower = gcfPower - minPower
                            powerMap[residualPower] = powerMap[residualPower] + !uniqueTermFactor
                        } else if (powerMap.isEmpty()) {
                            powerMap[1] = ONLY_ONE
                        }
                    }
                }
            }
        gcfVarExpr = SimpleProduct(gcfFactors)
    } else {
        gcfVarExpr = SimpleProduct.NO_MEMBERS
    }
    return if (
        gcfVarExpr != SimpleProduct.NO_MEMBERS ||
        gcfNumer.first isNotValue BigDecimal.ONE ||
        gcfDenom.first isNotValue BigDecimal.ONE
    ) {
        for ((index, powerMap) in powerMaps.withIndex()) {  // Distribute residual constants
            val numer = gcfNumer.second[index]
            val denom = gcfDenom.second[index]
            if (numer isNotValue BigDecimal.ONE) {
                powerMap[1] = powerMap[1] + !Value(numer)
            }
            if (denom isNotValue BigDecimal.ONE) {
                powerMap[-1] = powerMap[-1] + !Value(denom)
            }
        }
        val inside = powerMaps.toExpression()
        val gcf = Fraction(gcfNumer.first, gcfDenom.first).toExpression() simpleTimes gcfVarExpr
        gcf to inside
    } else {
        Expression.ONE to SimpleSum(simpleMembers)
    }
}
