import internal.strictEquals
import java.math.BigDecimal

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

    override fun substitute(vars: VariableTable): SimpleExpression {
        vars.forEach { (varl, sub) -> if (id == varl) return sub }
        return this
    }

    override fun isolateCoeff() = BigDecimal.ONE to this

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