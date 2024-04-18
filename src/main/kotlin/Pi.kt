import internal.Constants
import java.math.BigDecimal

object Pi {
    private var piCache = BigDecimal.ZERO

    fun withPrecision(precision: Int): BigDecimal {
        if (piCache.precision() < precision) {
            piCache = (arctan(Fraction.ONE, precision) * Constants.FOUR).evaluate(precision)
        }
        return piCache
    }
}