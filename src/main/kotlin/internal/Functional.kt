// Extension functions to support functional programming

package internal

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

// COLLECTIONS

// TODO move all helper functions to one file

/**
 * Implementation of [Iterable.fold] that returns the same object for each iteration. Instead of returning a new object
 * each time, the same object is reused and modified.
 * @param initial the object to be modified
 * @param modifier the operation to be applied to [initial] using each element
 */
inline fun <T,R> Iterable<T>.accumulate(initial: R, modifier: R.(T) -> Unit): R {
    fold(initial) { _, it ->
        modifier(initial, it)
        initial
    }
    return initial
}

fun <E> Iterable<E>.isolateIndexIn(indexes: Iterable<Int>): Pair<List<E>, List<E>> {
    for ((i, member) in withIndex()) {
        if (i in indexes) {
            return listOf(member) to (this - member)
        }
    }
    return listOf<E>() to this.toList()
}

// PAIR

// TODO phase out
@OptIn(ExperimentalContracts::class)
inline fun <A,B> Pair<A,B>.withFirst(withFirst: (A) -> Unit): Pair<A,B> {
    contract {
        callsInPlace(withFirst, InvocationKind.EXACTLY_ONCE)
    }
    return this.also { withFirst(first) }
}

// TODO phase out
@OptIn(ExperimentalContracts::class)
inline fun <A,B> Pair<A,B>.withSecond(withSecond: (B) -> Unit) {
    contract {
        callsInPlace(withSecond, InvocationKind.EXACTLY_ONCE)
    }
    withSecond(second)
}

// TRIPLE

inline fun <T,R> Triple<T,T,T>.map(transform: (T) -> R) = Triple(transform(first), transform(second), transform(third))