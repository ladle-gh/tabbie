package grammar.internal

import java.io.*

/**
 * Incrementally iterates through a set of characters with the ability to move the position of the iterator.
 */
internal sealed class CharStream(initialPosition: Int) : Closeable {
    var position = initialPosition
        protected set
    private val savedPositions = MutableIntVector()

    fun next() = peek().also { ++position }

    fun advancePosition(places: Int) {
        position += places
    }

    fun savePosition() {
        savedPositions += position
    }

    fun revertPosition() {
        try {
            position = savedPositions.removeLast()
        } catch (e: NoSuchElementException) {
            throw IllegalStateException("No positions are currently marked", e)
        }
    }

    fun removeSavedPosition() {
        savedPositions.removeLast()
    }

    abstract fun peek(): Char
    abstract fun substring(size: Int): String
    abstract fun hasNext(): Boolean

    protected inline fun <R> ensureBounds(block: () -> R): R {
        try {
            return block()
        } catch (e: IndexOutOfBoundsException) {
            throw StreamTerminator
        }
    }
}

/**
 * Incrementally iterates through a [String].
 * This stream does not need to be closed.
 */
internal class StringCharStream(private val chars: String) : CharStream(0) {
    override fun peek() = ensureBounds { chars[position] }
    override fun substring(size: Int) = ensureBounds { chars.substring(position, position + size) }
    override fun hasNext() = position < chars.length
    override fun close() {}

    override fun toString(): String {
        val lowerBound = (position - 20).coerceAtLeast(0)
        val upperBound = (position + 20).coerceAtMost(chars.length)
        return buildString {
            if (lowerBound != 0) {
                append("...")
            }
            append(chars.substring(lowerBound, position))
            append("{ ")
            append(chars[position.coerceIn(lowerBound, upperBound)])
            append(" }")
            append(chars.substring(position + 1, upperBound))
            if (upperBound != chars.length) {
                append("...")
            }
        }
    }
}

internal class FileCharStream(path: String) : CharStream(0) {
    private val source: BufferedInputStream
    private val chars = StringBuilder()
    private var eofReached = false

     init {
         val sourceFile = File(path)
         val length = sourceFile.length()
         if (length == 0L) {
             throw IOException("File $path does not exist")
         }
         source = BufferedInputStream(FileInputStream(sourceFile))
     }

    override fun peek(): Char {
        if (position >= chars.length) {
            loadChars()
        }
        return ensureBounds { chars[position] }
    }

    private fun loadChars() {
        var b1: Int
        var b2: Int
        do {
            b1 = source.read()
            if (b1 == -1) {
                break
            }
            b2 = source.read()
            if (b2 == -1) {
                break
            }
            chars.append(((b1 shl Byte.SIZE_BITS) or b2).toChar())
            if (b1 == 0x0 && b2 == 0x0) {
                eofReached = true
            }
        } while (!(b1 == 0x0 && b2 == '\n'.code) && !eofReached)
    }

    override fun substring(size: Int): String = ensureBounds { chars.substring(position, position + size) }

    override fun hasNext() = eofReached

    override fun close() {
        source.close()
    }
}