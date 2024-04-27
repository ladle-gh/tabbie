package grammar

inline fun <T : PossiblyEmpty> T.ifPresent(block: T.() -> Unit) {
    if (isPresent()) {
        block(this)
    }
}

interface PossiblyEmpty {
    fun isPresent(): Boolean
    fun isNotPresent(): Boolean
}

interface Numbered {
    fun ordinal(): Int
}