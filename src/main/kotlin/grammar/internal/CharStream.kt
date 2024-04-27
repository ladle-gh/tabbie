package grammar.internal

/**
 * Incrementally iterates through a [String] with the ability to move the position of the iterator.
 */
internal class CharStream(private val source: String) {
    private var curPosition = 0
    private var savedPositions = MutableIntVector()

    fun next() = peek().also { ++curPosition }

    fun savePosition() {
        savedPositions.push(curPosition)
    }

    fun substring(size: Int): String {
        return source.substring(curPosition - size, curPosition)
    }

    fun revertPosition() {
        try {
            curPosition = savedPositions.pop()
        } catch (e: NoSuchElementException) {
            throw IllegalStateException("No positions are currently marked", e)
        }
    }

    fun peek() = try {
        source[curPosition]
    } catch (e: IndexOutOfBoundsException) {
        throw NoSuchElementException("End of stream reached", e)
    }

    fun advancePosition(places: Int) {
        curPosition += places
    }

    fun regressPosition(places: Int) {
        curPosition -= places
    }
}