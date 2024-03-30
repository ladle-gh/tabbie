data class Split<A,B>(val agree: A, val disagree: B) {
    inline fun <T> forBoth(withBoth: (A, B) -> T) = withBoth(agree, disagree)
    inline fun forTrue(withTrue: (A) -> Unit) = this.also { withTrue(agree) }
    fun forTrue() = agree
    fun forFalse() = disagree

    inline fun forFalse(withFalse: (B) -> Unit) {
        withFalse(disagree)
    }
}
