import internal.*

/**
 * A list representing the terms in a sum (or in other words, a polynomial).
 * Each integer power (see [Expression]) is mapped to a list of each base with that power, per term.
 * This kind of list is useful for factoring.
 */
typealias PowerMapList = List<Map<Int, List<SimpleExpression>>>

typealias PowerMapListPair = Pair<PowerMapList,PowerMapList>
typealias PowerMapListTriple = Triple<PowerMapList,PowerMapList,PowerMapList>

/**
 * TODO add description
 */
fun PowerMapList.factorOut( // FIXME a and b bug here
    intPower: Int,
    base: SimpleExpression,
    termIndices: List<Int>
): PowerMapListPair {
    val possible = if (termIndices.isEmpty()) {
        partition { it[intPower]?.contains(base) ?: false }
    } else {
        isolateIndexIn(termIndices)
    }
    return possible
        .let { (termsToFactor, intact) ->
            val factored = termsToFactor.map { term -> term
                .mapValues { (curIntPower, bases) ->    // Remove x
                    if (curIntPower == intPower) (bases - base).ifEmpty { listOf(Expression.ONE) } else bases
                }
            }
            factored to intact
        }
}

/**
 * TODO add description
 */
fun PowerMapList.getCommonBases(intPower: Int): Map<SimpleExpression, List<Int>> {
    val bases = mutableMapOf<SimpleExpression, MutableList<Int>>()
    forEachIndexed { termIndex, mapping ->
        mapping[intPower]?.forEach { base ->
            bases[base] = bases[base] +! termIndex
        }
    }
    return bases
}