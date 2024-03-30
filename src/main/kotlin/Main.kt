fun main(args: Array<String>) {
    println("Hello world!")
}

/*
 * sum(listOf(Exponent(Exponent(Variable('x'), Value(2.0)), Value(7.0)), Product(listOf(Value(2.0), Variable('x'))), Value(3.5), Value(6.0))) =
 * ('x'.v toThe 2.0.r toThe 7.0.r) + 2.0.r*'x'.v + 3.5.r + 6.0.r
 * ('x'.v toThe 2.0.r) + ('x'.v toThe 0.5.r)
 * ('x'.v toThe 2.0.r) * ('x'.v toThe 0.5.r)
 * ('x'.v toThe 2.0.r) / ('x'.v toThe 0.5.r)
 * 8.0.r*'y'.v - 6.0.r*'y'.v*'z'.v - 10.0.r - 9.0.r*'y'.v*'z'.v + 9.0.r*'z'.v
 * (8.0.r*'y'.v).substitute('y', 5.0) - 6.0.r*'y'.v*'z'.v - 10.0.r - 9.0.r*'y'.v*'z'.v + 9.0.r*'z'.v
 */