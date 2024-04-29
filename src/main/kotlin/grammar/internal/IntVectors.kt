package grammar.internal

import org.jetbrains.annotations.Contract
import kotlin.NoSuchElementException

internal fun vectorOf(c: Char): IntVector = SingletonIntVector(c.code)

internal fun vectorOf(vararg c: Char): IntVector {
    val nArray = IntArray(c.size)
    c.indices.forEach { nArray[it] = c[it].code }
    return ArrayIntVector(nArray)
}

/**
 * Optimization of [List] for integers.
 */
internal sealed class IntVector {
    abstract val size: Int
    abstract val indices: IntRange

    abstract fun sum(): Int
    abstract fun isEmpty(): Boolean
    abstract fun isNotEmpty(): Boolean
    abstract operator fun get(index: Int): Int

    final override fun toString() = indices.map { this[it] }.joinToString(prefix = "[", postfix = "]")
}

internal open class ArrayIntVector : IntVector {
    final override var size: Int
        protected set
    final override val indices get() = data.indices.let { it.first..<it.last.coerceAtMost(size) }

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

internal class MutableIntVector(initialSize: Int = DEFAULT_SIZE) : ArrayIntVector(initialSize) {
    operator fun plusAssign(c: Char) = plusAssign(c.code)

    operator fun plusAssign(n: Int) {
        if (size == data.size) {
            val new = IntArray(size * 2)
            System.arraycopy(data, 0, new, 0, size)
            data = new
        }
        data[size] = n
        ++size
    }

    fun removeLast(): Int {
        try {
            return data[size - 1].also { --size }
        } catch (e: IndexOutOfBoundsException) {
            throw NoSuchElementException("Cannot remove integer from empty vector", e)
        }
    }

    private companion object {
        const val DEFAULT_SIZE = 8
    }
}

private class SingletonIntVector(val value: Int) : IntVector() {
    override val indices get() = INDICES
    override val size get() = 1

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
        val INDICES = 0..<1
    }
}