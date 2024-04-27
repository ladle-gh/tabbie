package grammar

/**
 * Provides a scope with the receiver as
 */
inline fun <T : PossiblyEmptyToken> T.ifPresent(block: T.() -> Unit) {
    if (isPresent()) {
        block(this)
    }
}



