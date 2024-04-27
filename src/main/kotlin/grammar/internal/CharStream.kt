package grammar.internal

/**
 * Incrementally iterates through a [String] with the ability to move the position of the iterator.
 */
internal class CharStream(private val source: String) {
    private var curPosition = 0

    fun next() = peek().also { ++curPosition }

    fun substring(size: Int): String {
        try {
            return source.substring(curPosition - size, curPosition)
        } catch (e: StringIndexOutOfBoundsException) {  // Impossible
            throw StreamTerminator
        }
    }

    fun peek(): Char {
        try {
            return source[curPosition]
        } catch (e: IndexOutOfBoundsException) {
            throw StreamTerminator
        }
    }

    fun advancePosition(places: Int) {
        curPosition += places
    }

    fun regressPosition(places: Int) {
        curPosition -= places
    }
}