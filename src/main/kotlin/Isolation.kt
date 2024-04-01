import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

private inline fun <E> List<E>.multiplyAll(getValue: (E) -> BigDecimal): BigDecimal {
    return fold(BigDecimal.ONE) { last, it -> getValue(it) * last }
}

inline fun <E> List<E>.isolateAt(predicate: (Int) -> Boolean): Pair<List<E>, List<E>> {
    val isolated = mutableListOf<E>()
    val remaining = mutableListOf<E>()
    for ((i, member) in withIndex()) {
        if (predicate(i)) {
            isolated.add(member)
        } else {
            remaining.add(member)
        }
    }
    return isolated to remaining
}

fun BigDecimal.isolateIntegralPart(): Pair<BigDecimal, BigDecimal> {
    if (this equals BigDecimal.ZERO) {
        return Pair(BigDecimal.ZERO, BigDecimal.ZERO)
    }
    val whole = setScale(0, RoundingMode.DOWN)
    return if (scale() <= 0 || this == whole) this to BigDecimal.ZERO else whole to (this - whole)
}

/**
 * @return First: the [coefficient][Expression.coefficient]; Second: all other expressions in member list
 */
fun ExpressionList.isolateCoeffNumer(): Pair<BigDecimal, ExpressionList> {
    return isolateInstances<Value>().withBoth { vals, nonvals -> vals.multiplyAll { it.value } to  nonvals }
}

/**
 * @return First: the [coefficient][Expression.coefficient]; Second: all other expressions in member list
 */
fun ExpressionList.isolateCoeffDenom(): Pair<BigDecimal, ExpressionList> {
    return partition { it.isReciprocal() }  // Simplify reciprocals of rational values (denominators); 1/a * 1/b = 1/(ab)
        .withBoth { reciprocals, nonReciprocals ->
            reciprocals.map { it as Exponent }.multiplyAll { (it.base as Value).value } to nonReciprocals
        }
}

/**
 * @return First: expressions of the specified type; Second: all other expressions
 */
inline fun <reified T> ExpressionList.isolateInstances(): Pair<List<T>, ExpressionList> {
    return with(partition { it is T }) { first.map { it as T } to second }
}

inline fun <A,B,T> Pair<A,B>.withBoth(withBoth: (A, B) -> T) = withBoth(first, second)

@OptIn(ExperimentalContracts::class)
inline fun <A,B> Pair<A,B>.withFirst(withFirst: (A) -> Unit): Pair<A,B> {
    contract {
        callsInPlace(withFirst, InvocationKind.EXACTLY_ONCE)
    }
    return this.also { withFirst(first) }
}

@OptIn(ExperimentalContracts::class)
inline fun <A,B> Pair<A,B>.withSecond(withSecond: (B) -> Unit) {
    contract {
        callsInPlace(withSecond, InvocationKind.EXACTLY_ONCE)
    }
    withSecond(second)
}

inline fun <T,R> Triple<T,T,T>.map(transform: (T) -> R) = Triple(transform(first), transform(second), transform(third))