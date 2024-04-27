fun Val(n: Int) = Value(n.toBigDecimal())
fun Var(ch: Char) = Variable(ch)
fun main(args: Array<String>) {
    // TODO debug: b is 1?
    val x =simpleSum(Var('x').simplePow(Val(2)) simpleTimes Val(7), Var('x') simpleTimes Val(3), Val(1) simpleTimes Var('z')).factor()
    println(x)
}
