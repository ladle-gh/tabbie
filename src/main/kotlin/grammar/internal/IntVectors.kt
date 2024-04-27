package grammar.internal

import kotlin.NoSuchElementException

fun vectorOf(c: Char): IntVector = SingletonIntVector(c.code)

fun vectorOf(vararg c: Char): IntVector {
    val nArray = IntArray(c.size)
    c.indices.forEach { nArray[it] = c[it].code }
    return ArrayIntVector(nArray)
}

/**
 * An array of integers. Implementations allow for modification without need to reallocate the underlying array.
 */
sealed interface IntVector {
    val size: Int
    val indices: IntRange

    fun sum(): Int
    fun isEmpty(): Boolean
    fun isNotEmpty(): Boolean
    operator fun get(index: Int): Int
}

open class ArrayIntVector : IntVector {
    final override var size: Int
        protected set
    final override val indices get() = data.indices

    protected var data: IntArray

    final override fun sum() = data.sum()
    final override operator fun get(index: Int) = data[index]

    constructor(nArray: IntArray) {
        size = nArray.size
        data = nArray
    }

    protected constructor(initialSize: Int) {
        size = initialSize
        data = IntArray(size)
    }

    final override fun isEmpty() = size == 0
    final override fun isNotEmpty() = size != 0

    override fun equals(other: Any?): Boolean {
        if (other !is IntVector) {
            return false
        }
        return when (other.size) {
            0 -> size == 0
            1 -> data[0] == if (other is SingletonIntVector) other.value else (other as ArrayIntVector).data[0]
            else -> data.contentEquals((other as ArrayIntVector).data)
        }
    }

    override fun hashCode(): Int {
        var result = 1
        data.forEach { result = PRIME * result + it }
        return result
    }

    private companion object {
        const val PRIME = 7
    }
}

class MutableIntVector(size: Int = DEFAULT_SIZE) : ArrayIntVector(size) {
    fun push(c: Char) = push(c.code)

    fun push(n: Int) {
        if (size == data.size) {
            data = IntArray(size * 2).apply { indices.forEach { this[it] = data[it] } }
        }
        data[size] = n
    }

    fun pop(): Int {
        try {
            return data[size - 1].also { --size }
        } catch (e: IndexOutOfBoundsException) {
            throw NoSuchElementException("Cannot pop value from empty stack", e)
        }
    }

    private companion object {
        const val DEFAULT_SIZE = 8
    }
}

private class SingletonIntVector(val value: Int) : IntVector {
    override val indices = INDICES
    override val size = 1

    override fun sum() = value
    override fun isEmpty() = false
    override fun isNotEmpty() = true

    override fun get(index: Int): Int {
        if (index == 0) {
            return value
        }
        throw IndexOutOfBoundsException(index)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is IntVector) {
            return false
        }
        return other.size == 1 && other[1] == value
    }

    override fun hashCode(): Int {
        return value
    }

    private companion object {
        val INDICES = IntRange(0, 0)
    }
}