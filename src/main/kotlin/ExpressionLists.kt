import java.math.BigDecimal

internal typealias ExpressionList = List<Expression>
typealias PowerMapping = Map<Int, List<Expression>>

private inline fun <reified T> ExpressionList.castMembers() = map { it as T }

private inline fun <E> List<E>.multiplyAll(getValue: (E) -> BigDecimal): BigDecimal {
    return fold(BigDecimal.ONE) { last, it -> getValue(it) * last }
}

private inline fun ExpressionList.split(predicate: (Expression) -> Boolean): Split<ExpressionList, ExpressionList> {
    return groupBy { predicate(it) }.let { Split(it[true] ?: listOf(),  it[false] ?: listOf()) }
}

/**
 * TODO
 */
internal inline fun <E, T> MutableMap<E, T>.addOrModify(key: E, init: () -> T, modifier: (T) -> Unit) {
    if (!containsKey(key)) {
        this[key] = init()
    }
    modifier(this[key]!!)
}

/**
 * @return First: the [coefficient][Expression.coefficient]; Second: all other expressions in member list
 */
internal fun ExpressionList.splitCoeffNumer(): Split<BigDecimal, ExpressionList> {
    return splitIsInstance<Value>().forBoth { vals, nonvals -> Split(vals.multiplyAll { it.value },  nonvals) }
}

/**
 * @return First: the [coefficient][Expression.coefficient]; Second: all other expressions in member list
 */
internal fun ExpressionList.splitCoeffDenom(): Split<BigDecimal, ExpressionList> {
    return split { it.isReciprocal() }  // Simplify reciprocals of rational values (denominators); 1/a * 1/b = 1/(ab)
        .forBoth { reciprocals, nonReciprocals ->
            Split(reciprocals.castMembers<Exponent>().multiplyAll { (it.base as Value).value }, nonReciprocals)
        }
}

/**
 * @return First: expressions of the specified type; Second: all other expressions
 */
internal inline fun <reified T> ExpressionList.splitIsInstance(): Split<List<T>, ExpressionList> {
    return with(split { it is T }) { Split(forTrue().castMembers<T>(), forFalse()) }
}

inline fun <T,U> Iterable<T>.foldInPlace(init: U, modifier: U.(T) -> Unit): U {
    fold(init) { acc, it ->
        modifier(init, it)
        init
    }
    return init
}

internal fun List<ExpressionList>.powerMappings(): List<PowerMapping> {
    return this
        .filterIsInstance<Product>()
        .foldInPlace(ArrayList(size)) {term ->
            val powerMapping = term.members
                .foldInPlace(mutableMapOf<Int, MutableList<Expression>>()) { factor ->
                    factor
                        .splitIntPower()    // Int (power) and Expression (base)
                        .forBoth { intPower, base -> addOrModify(intPower, { mutableListOf() }) { it.add(base) } }
                }
            add(powerMapping)
        }
}