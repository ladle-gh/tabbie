import internal.isValue
import internal.strictEquals
import java.math.BigDecimal

/**
 * A rational number.
 * @see MutableValue
 */
open class Value(open val value: BigDecimal) : PureExpression(), CanBeNegative {
    override fun substitute(vars: VariableTable) = this
    override fun isolateCoeff() = value to Expression.ONE

    override fun isNegative() = value < BigDecimal.ZERO
    override fun removeNegative() = Value(-value)

    override fun evaluateAsFraction(precision: Int) = value

    override fun equals(other: Any?) = strictEquals(other) { value isValue it.value }
    override fun hashCode() = value.hashCode()
    override fun toString() = value.toString()  // Debug
}

class MutableValue(override var value: BigDecimal) : Value(value)