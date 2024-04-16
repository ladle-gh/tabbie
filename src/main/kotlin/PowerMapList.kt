import internal.isolateIndexIn
import internal.mutablePut
import internal.withBoth

/**
 * A list representing the terms in a sum (or in other words, a polynomial).
 * TODO finish description
 */
typealias PowerMapList = List<Map<Int, List<SimpleExpression>>>

/**
 * TODO add description
 */
fun PowerMapList.factorOut( // FIXME a and b bug here
    intPower: Int,
    base: SimpleExpression,
    termIndices: List<Int>
): Pair<PowerMapList, PowerMapList> {
    val possible = if (termIndices.isEmpty()) {
        partition { it[intPower]?.contains(base) ?: false }
    } else {
        isolateIndexIn(termIndices)
    }
    return possible
        .withBoth { termsToFactor, intact ->
            val factored = termsToFactor.map { term -> term
                .toMutableMap()
                .apply {
                    this[intPower] = (this.getValue(intPower) - base).ifEmpty { listOf(Expression.ONE) }
                }   // Remove x
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
            bases.mutablePut(base, { mutableListOf() }) { it.add(termIndex) }
        }
    }
    return bases
}