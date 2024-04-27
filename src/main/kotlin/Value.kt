import internal.isValue
import internal.strictEquals
import java.math.BigDecimal

/**
 * A rational number.
 * @see MutableValue
 */
open class Value(open val value: BigDecimal) : PureExpression(), CanBeNegative {
    override fun substitute(vars: Map<Char, SimpleExpression>) = this
    override fun partitionCoeff() = value to Expression.ONE

    override fun isNegative() = value < BigDecimal.ZERO
    override fun removeNegative() = Value(-value)

    override fun toFraction() = Fraction(value)

    override fun equals(other: Any?) = strictEquals(other) { value isValue it.value }
    override fun hashCode() = value.hashCode()
    override fun toString() = value.toString()  // Debug
}

class MutableValue(override var value: BigDecimal) : Value(value)