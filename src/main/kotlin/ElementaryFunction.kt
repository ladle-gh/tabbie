import java.math.BigDecimal

class ElementaryFunction(var value: Expression, isSimplified: Boolean = false) : Expression(isSimplified) {

    override fun simplify(): Expression {
        TODO("Not yet implemented")
    }

    override fun factor(): Expression {
        TODO("Not yet implemented")
    }

    override fun substitute(varl: Char, sub: BigDecimal): Expression {
        TODO("Not yet implemented")
    }

    override fun equals(other: Any?) = strictEquals(other) { value == it.value }

    override fun hashCode() = value.hashCode()
}

// gamma
// sin
//cos
//tan
//arcsin
//arccos
//arctan

// Delegate co-functions