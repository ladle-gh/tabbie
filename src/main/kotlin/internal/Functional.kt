// Extension functions to support functional programming

package internal

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

// COLLECTIONS

/**
 * Implementation of [Iterable.fold] that returns the same object for each iteration. Instead of returning a new object
 * each time, the same object is reused and modified.
 * @param initial the object to be modified
 * @param modifier the operation to be applied to [initial] using each element
 */
inline fun <T,U> Iterable<T>.mutableFold(initial: U, modifier: U.(T) -> Unit): U {
    fold(initial) { _, it ->
        modifier(initial, it)
        initial
    }
    return initial
}

inline fun <E> Iterable<E>.isolate(predicate: (E) -> Boolean): Pair<List<E>, List<E>> {
    val isolated = mutableListOf<E>()
    val remaining = mutableListOf<E>()
    for (member in this) {
        if (predicate(member)) {
            isolated.add(member)
        } else {
            remaining.add(member)
        }
    }
    return isolated to remaining
}

fun <E> Iterable<E>.isolateIndexIn(indexes: Iterable<Int>): Pair<List<E>, List<E>> {
    val remaining = mutableListOf<E>()
    for ((i, member) in withIndex()) {
        if (i in indexes) {
            return listOf(member) to remaining
        } else {
            remaining.add(member)
        }
    }
    return listOf<E>() to remaining
}


// PAIR

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


// TRIPLE

inline fun <T,R> Triple<T,T,T>.map(transform: (T) -> R) = Triple(transform(first), transform(second), transform(third))