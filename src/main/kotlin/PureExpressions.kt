import java.math.BigDecimal

val E = Variable('e')
val PI = Variable("{pi}")
val TWO_PI = Value.TWO.times(PI, true)
val HALF_PI = PI.div(Value.TWO.reciprocal(), true)

    // TODO benchmark alternate

/**
 * An expression that is always simplified and equivalent to its factored form
 */
abstract class PureExpression : Expression(true) {
    final override fun simplify() = this
    final override fun factor() = this
}

/**
 * A rational number.
 */
class Value(val value: BigDecimal) : PureExpression() {
    override fun substitute(varl: Char, sub: BigDecimal) = this
    override fun coefficient() = value
    override fun equals(other: Any?) = strictEquals(other) { value equals it.value }
    override fun hashCode() = value.hashCode()
    override fun toString() = value.toString()  // Debug

    companion object {
        val ONE = Value(BigDecimal.ONE)
        val TWO = Value(BigDecimal(2))
        val ZERO = Value(BigDecimal.ZERO)
        val NEGATIVE_ONE = Value(BigDecimal(-1))
    }
}

/**
 * A placeholder for any real number.
 */
class Variable : PureExpression {
    val id: Char

    constructor(value: Char) {
        require(value in 'a'..'z' || value in 'A'..'Z' || value in ESCAPE_SEQUENCES.values) {
            "Variable must be a letter"
        }
        this.id = value
    }

    constructor(escape: String) {
        id = ESCAPE_SEQUENCES.getOrElse(escape) {
            throw IllegalArgumentException("'$escape' is not a valid escape sequence")
        }
    }

    override fun substitute(varl: Char, sub: BigDecimal): Expression = if (varl != id) this else Value(sub)
    override fun equals(other: Any?) = strictEquals(other) { id == it.id }
    override fun hashCode() = id.hashCode()
    override fun toString() = id.toString() // Debug

    companion object {
        private val ESCAPE_SEQUENCES = mapOf(
            "{alpha}" to 'α',
            "{beta}" to 'β',
            "{gamma}" to 'γ',
            "{delta}" to 'δ',
            "{epsilon}" to 'ε',
            "{theta}" to 'θ',
            "{lambda}" to 'λ',
            "{mu}" to 'μ',
            "{pi}" to 'π',
            "{sigma}" to 'σ',
            "{omega}" to 'ω'
        )
    }
}