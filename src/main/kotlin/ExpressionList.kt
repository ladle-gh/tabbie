import internal.withBoth
import java.math.BigDecimal

typealias ExpressionList = List<Expression>
typealias SimpleExpressionList = List<SimpleExpression>
typealias SimpleExpressionPair = Pair<SimpleExpression,SimpleExpression>

/**
 * @return First: the [coefficient][SimpleExpression.coefficient]; Second: all other expressions in member list
 */
fun SimpleExpressionList.isolateCoeffNumer(): Pair<BigDecimal, SimpleExpressionList> {
    return isolateInstances<Value>().withBoth { vals, nonvals -> vals.multiplyAll { it.value } to  nonvals }
}

/**
 * @return First: the [coefficient][SimpleExpression.coefficient]; Second: all other expressions in member list
 */
fun SimpleExpressionList.isolateCoeffDenom(): Pair<BigDecimal, SimpleExpressionList> {
    return partition { it.isReciprocal() }  // Simplify reciprocals of rational values (denominators); 1/a * 1/b = 1/(ab)
        .withBoth { reciprocals, nonReciprocals ->
            reciprocals.map { it as Exponent }.multiplyAll { (it.base as Value).value } to nonReciprocals
        }
}

/**
 * @return First: expressions of the specified type; Second: all other expressions
 */
inline fun <reified T> SimpleExpressionList.isolateInstances(): Pair<List<T>, SimpleExpressionList> {
    return with(partition { it is T }) { first.map { it as T } to second }
}

private inline fun <E> Collection<E>.multiplyAll(getValue: (E) -> BigDecimal): BigDecimal {
    return fold(BigDecimal.ONE) { last, it -> getValue(it) * last }
}