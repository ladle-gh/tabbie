package grammar.internal

import grammar.ContextFreeToken

/**
 * [Matches][attemptMatch] tokens in a [stream][CharStream].
 * @property id a unique identifier. Used by grammars to call listeners of the name.
 * Not needed for implicitly defined symbols or [Option]s.
 * Those starting in an underscore or digit are reserved for the compiler.
 * @see ContextFreeToken
 * @see grammar.Grammar
 */
internal sealed class Symbol(var id: String = ID.next()) {
    /**
     * Entry-point of parser.
     * @see MetaGrammar.start
     */
    fun startMatch(input: CharStream, skip: Symbol): ContextFreeToken {
        val recursions = mutableListOf<String>()
        skip.consume(input, recursions)
        return match(input, skip, recursions)
    }

    /**
     * [attemptMatch] with protection from infinite recursion.
     */
    fun match(input: CharStream, skip: Symbol, recursions: MutableList<String>): ContextFreeToken {
        recursions.add(id)
        return try {
            attemptMatch(input, skip, recursions).also { recursions.removeLast() }
        } catch (_: StreamTerminator) {
            ContextFreeToken.NOTHING
        }
    }

    /**
     * Consumes the next characters in stream which match this symbol.
     * Used for symbols marked with the "skip" directive.
     * @see MetaGrammar.skip
     */
    fun consume(input: CharStream, recursions: MutableList<String>) {
        match(input, ZeroLengthSymbol, recursions)
    }

    /**
     * If the current in [input] contains an expression that agrees with the rules defined by this object, a token
     * is created, which describes:
     * - The [id] of the matching symbol
     * - The chiildren which this symbol comprises (not applicable to all symbol types)
     * - The length of the character string which this symbol matches
     * @param input the target of the lexical analysis
     * @param skip the symbol matching any ignored expressions
     * @return a unique [ContextFreeToken], or [ContextFreeToken.NOTHING], signifying failure
     * @see consume
     */
    protected abstract fun attemptMatch(input: CharStream, skip: Symbol, recursions: MutableList<String>): ContextFreeToken

    /**
     * @return a unique [ContextFreeToken] with the [id] of this symbol
     * @see tokenOrNothing
     */
    protected fun token(
        input: CharStream,
        children: List<ContextFreeToken> = listOf(),
        length: Int = children.sumOf { it.substring.length },
        ordinal: Int = 0
    ): ContextFreeToken {
        return ContextFreeToken(this, input.substring(length), children, ordinal)
    }

    /**
     * @return If predicate is true: a unique [ContextFreeToken] with the [id] of this symbol;
     * If false: [ContextFreeToken.NOTHING]
     * @see token
     */
    protected fun tokenOrNothing(
        input: CharStream,
        predicate: Boolean,
        children: List<ContextFreeToken> = listOf(),
        length: Int = children.sumOf { it.substring.length },
        ordinal: Int = 0
    ): ContextFreeToken {
        return if (!predicate) ContextFreeToken.NOTHING else token(input, children, length, ordinal)
    }
}

/**
 * Symbol created by definition of a symbol using multiple other symbols in sequence.
 *
 * Default payload: List of payloads for each matched symbol
 */
internal class Sequence(id: String = grammar.internal.ID.next(), private val members: List<Symbol>) : Symbol(id) {
    constructor(id: String, vararg members: Symbol) : this(id, members.toList())
    constructor(vararg members: Symbol) : this(grammar.internal.ID.next(), members.toList())

    override fun attemptMatch(input: CharStream, skip: Symbol, recursions: MutableList<String>): ContextFreeToken {
        // assert(members.size > 0)
        val children = mutableListOf<ContextFreeToken>()
        var subMatch: ContextFreeToken

        for (member in members) {
            subMatch = member.match(input, skip, recursions)
            if (subMatch === ContextFreeToken.NOTHING) {
                input.regressPosition(children.sumOf { it.substring.length })
                return ContextFreeToken.NOTHING
            }
            children += subMatch
            skip.consume(input, recursions)
        }
        return token(input, children)
    }

    companion object {
        val ID = Sequence("id",
            Switch(vectorOf('a', 'A'), vectorOf('z', 'Z')),
            Star(Switch(vectorOf('a', 'A', '0', '_'), vectorOf('z', 'Z', '9', '_')))
        )
    }
}

/**
 * Symbol created by use of the '|' operator.
 */
internal class Junction(id: String = ID.next(), private val members: List<Symbol>) : Symbol(id) {
    constructor(id: String, vararg members: Symbol) : this(id, members.toList())
    constructor(vararg members: Symbol) : this(ID.next(), members.toList())

    override fun attemptMatch(input: CharStream, skip: Symbol, recursions: MutableList<String>): ContextFreeToken {
        val subMatch = members
            .filter { it.id !in recursions }
            .anyNot(ContextFreeToken.NOTHING) { it.match(input, skip, recursions) }
        return tokenOrNothing(input, subMatch != ContextFreeToken.NOTHING,
            children = listOf(subMatch),
            ordinal = members.indexOfFirst { it.id == subMatch.origin.id }
        )
    }
}

/**
 * Symbol created by use of the '+' operator.
 */
internal class Multiple(id: String = ID.next(), private val inner: Symbol) : Symbol(id) {
    constructor(inner: Symbol) : this(ID.next(), inner)

    override fun attemptMatch(input: CharStream, skip: Symbol, recursions: MutableList<String>): ContextFreeToken {
        val children = mutableListOf<ContextFreeToken>()
        var subMatch = inner.match(input, skip, recursions)
        while (subMatch !== ContextFreeToken.NOTHING) {
            children += subMatch
            if (subMatch.substring.isEmpty()) {
                break
            }
            subMatch = inner.match(input, skip, recursions)
        }
        return tokenOrNothing(input, children.isNotEmpty(), children)
    }
}

/**
 * Symbol created by use of the '?' operator.
 * Because of the possibility that nothing is captured, this symbol cannot be given an ID or listener.
 */
internal class Option(private val inner: Symbol) : Symbol() {
    override fun attemptMatch(input: CharStream, skip: Symbol, recursions: MutableList<String>): ContextFreeToken {
        val result = inner.match(input, skip, recursions)
        return if (result !== ContextFreeToken.NOTHING) {
            token(input, listOf(result))
        } else {
            ContextFreeToken.EMPTY  // .additionalInfo == 0
        }
    }
}

/**
 * Symbol created by use of the '*' operator.
 * Because of the possibility that nothing is captured, this symbol cannot be given an ID or listener.
 */
internal class Star(inner: Symbol) : Symbol() {
    private val equivalent = Option(Multiple(inner))

    override fun attemptMatch(input: CharStream, skip: Symbol, recursions: MutableList<String>): ContextFreeToken {
        val result = equivalent.match(input, skip, recursions)
        return if (result.isNotPresent()) ContextFreeToken.EMPTY else result.children[0].apply { origin = this@Star }
    }
}

/**
 * Symbol created by definition of a character switch (e.g. \[a-zA-Z]).
 * For up-to ranges (e.g. \[-z]), [lowerBounds] will store [Char.MIN_VALUE].
 * For at-least ranges (e.g. \[a-]), [upperBounds] will store [Char.MAX_VALUE].
 * For single characters (e.g. \[ab-c]), the lower and upper bounds will be the same.
 * May be implicitly defined.
 */
internal class Switch(
    id: String = ID.next(),
    private val lowerBounds: IntVector,
    private val upperBounds: IntVector
) : Symbol(id) {
    constructor(lowerBounds: IntVector, upperBounds: IntVector) : this(ID.next(), lowerBounds, upperBounds)

    override fun attemptMatch(input: CharStream, skip: Symbol, recursions: MutableList<String>): ContextFreeToken {
        // assert(lowerBounds.size == upperBounds.size)
        val test = lowerBounds.indices.indexOfFirst { input.peek().code in lowerBounds[it]..upperBounds[it] }
        val result = tokenOrNothing(input, test != -1, length = 1, ordinal = test)
        input.advancePosition(result.substring.length)
        return result
    }

    companion object {
        val DIGIT = Switch(vectorOf('0'), vectorOf('9'))

        fun excluding(c: Char) = Switch(vectorOf(Char.MIN_VALUE, c + 1), vectorOf(c - 1, Char.MAX_VALUE))

        fun including(vararg c: Char): Switch {
            val lowerBounds = MutableIntVector(c.size)
            val upperBounds = MutableIntVector(c.size)
            for (char in c) {
                lowerBounds.push(char.code)
                upperBounds.push(char.code)
            }
            return Switch(lowerBounds, upperBounds)
        }
    }
}

/**
 * Symbol created by definition of a string. May be implicitly defined.
 */
internal class Text(id: String = ID.next(), private val acceptable: String) : Symbol(id) {
    private val length = acceptable.length

    constructor(acceptable: String) : this(ID.next(), acceptable)

    override fun attemptMatch(input: CharStream, skip: Symbol, recursions: MutableList<String>): ContextFreeToken {
        val result = tokenOrNothing(input, acceptable.all { it == input.next() }, length = length)
        input.regressPosition(length - result.substring.length)
        return result
    }
}

/**
 * Symbol created by defintition of a string of length 1. May be implicitly defined.
 */
internal class Character(id: String = ID.next(), private val acceptable: Char) : Symbol(id) {
    constructor(acceptable: Char) : this(ID.next(), acceptable)

    override fun attemptMatch(input: CharStream, skip: Symbol, recursions: MutableList<String>): ContextFreeToken {
        val result = tokenOrNothing(input, input.peek() == acceptable, length = 1)
        input.advancePosition(result.substring.length)
        return result
    }

    companion object {
        val DASH = Character('-')
        val APOSTROPHE = Character('\'')
    }
}


/**
 * A catch-all switch literal (e.g. \[-]).
 */
internal class AnyCharacter(id: String = ID.next()) : Symbol(id) {
    override fun attemptMatch(input: CharStream, skip: Symbol, recursions: MutableList<String>): ContextFreeToken {
        try {
            input.peek()    // throws if end is reached
            return token(input, length = 1, ordinal = 1)
        } catch (e: IndexOutOfBoundsException) {
            return ContextFreeToken.NOTHING
        }
    }
}

internal class ImplicitSymbol(id: String = ID.next()) : Symbol(id) {
    var reference: Symbol? = null

    override fun attemptMatch(input: CharStream, skip: Symbol, recursions: MutableList<String>): ContextFreeToken {
        return reference!!.match(input, skip, recursions)
    }
}

/**
 * Special rule for internal API usage.
 */
internal data object ZeroLengthSymbol : Symbol() {
    override fun attemptMatch(input: CharStream, skip: Symbol, recursions: MutableList<String>) = ContextFreeToken.EMPTY
}

/**
 * @return the first member in this that is not the specified value, if it exists; else, the value
 */
private inline fun <E, T> Iterable<E>.anyNot(not: T, transform: (E) -> T): T {
    for (member in this) {
        val result = transform(member)
        if (result != not) {
            return result
        }
    }
    return not
}

private object ID {
    private var counter = 0

    fun next() = counter.toString().also { ++counter }  // Synchronization not necessary
}