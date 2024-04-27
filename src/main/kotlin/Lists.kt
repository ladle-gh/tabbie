import java.math.BigDecimal

/**
 * @return First: the [coefficient][SimpleExpression.partitionCoeff]; Second: all other expressions in member list
 */
fun List<SimpleExpression>.partitionCoeffNumer(): Pair<BigDecimal, List<SimpleExpression>> {
    return partitionInstancesOf<Value>().let { (vals, nonvals) -> vals.multiplyAll { it.value } to nonvals }
}

/**
 * @return First: the [coefficient][SimpleExpression.partitionCoeff]; Second: all other expressions in member list
 */
fun List<SimpleExpression>.partitionCoeffDenom(): Pair<BigDecimal, List<SimpleExpression>> {
    return partition { it.isReciprocal() }  // Simplify reciprocals of rational values (denominators); 1/a * 1/b = 1/(ab)
        .let { (reciprocals, nonReciprocals) ->
            reciprocals.map { it as Exponent }.multiplyAll { (it.base as Value).value } to nonReciprocals
        }
}

/**
 * @return First: expressions of the specified type; Second: all other expressions
 */
inline fun <reified T> List<SimpleExpression>.partitionInstancesOf(): Pair<List<T>, List<SimpleExpression>> {
    return with(partition { it is T }) { first.map { it as T } to second }
}

private inline fun <E> Iterable<E>.multiplyAll(getValue: (E) -> BigDecimal): BigDecimal {
    return fold(BigDecimal.ONE) { last, it -> getValue(it) * last }
}