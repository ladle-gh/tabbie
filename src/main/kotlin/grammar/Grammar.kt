package grammar

fun <T,M> grammar(formalGrammar: String, builder: Grammar<T,M>.BuilderContext.() -> Unit): Grammar<T,M> {
    val result = Grammar<T,M>()
    if (formalGrammar == "__meta__") {
        result.defineUsing(
            "symbol" to MetaGrammar.symbol,
            "escape" to MetaGrammar.escape,
            "char" to MetaGrammar.char,
            "switch" to MetaGrammar.switch,
            "character" to MetaGrammar.character,
            "text" to MetaGrammar.text,
            "rule" to MetaGrammar.rule,
            "sequence" to MetaGrammar.sequence,
            "junction" to MetaGrammar.junction,
            "multiple" to MetaGrammar.multiple,
            "optional" to MetaGrammar.option,
            "any" to MetaGrammar.star,
            "start" to MetaGrammar.start,
            "skip" to MetaGrammar.skip
        )
    } else {
        result.defineUsing(formalGrammar)
    }
    return result
}

class Grammar<R, M> {
    private val listeners = mutableMapOf<String, Token.(M) -> Any?>()
    private var rules: Map<String,Symbol> by AssignOnce()
    private var startID: String by AssignOnce()
    private var skipID: String by AssignOnce()

    fun parse(input: String, mutableState: M): R {
        // Root token can be seen as base of parse tree
        val rootToken = rules.getValue(startID).match(CharStream(input), rules.getValue(skipID), mutableListOf())
        rootToken.walk(listeners, mutableState)
        return rootToken.payload()
    }

    internal fun defineUsing(formalGrammar: String) {
        rules = META.parse(formalGrammar, MetaGrammarState())
    }

    internal fun defineUsing(vararg rule: Pair<String, Symbol>) {
        rules = rule.toMap()
    }

    inner class BuilderContext {
        fun String.start() {
            startID = this
        }

        fun String.skip() {
            skipID = this
        }

        operator fun <T> String.invoke(listener: Token.(M) -> T): String {
            if (this !in rules) {
                throw IllegalCallerException("Rule '$this' is undefined")
            }
            if (this in listeners) {
                throw IllegalCallerException("Listener for rule '$this' is already defined")
            }
            listeners[this] = listener
            return this
        }

        // TODO add fail-fast check for proper Symbol in rules (.values)
        inline fun <T> String.sequence(crossinline listener: SequenceToken.(M) -> T) = invoke { listener(thisAs("SequenceToken"), it) }
        inline fun <T> String.junction(crossinline listener: JunctionToken.(M) -> T) = invoke { listener(thisAs("JunctionToken"), it) }
        inline fun <T> String.multiple(crossinline listener: MultipleToken.(M) -> T) = invoke { listener(thisAs("MultipleToken"), it) }
        inline fun <T> String.option(crossinline listener: OptionToken.(M) -> T) = invoke { listener(thisAs("Optionoken"), it) }
        inline fun <T> String.star(crossinline listener: StarToken.(M) -> T) = invoke { listener(thisAs("StarToken"), it) }
        inline fun <T> String.character(crossinline listener: CharacterToken.(M) -> T) = invoke { listener(thisAs("CharacterToken"), it) }
        inline fun <T> String.text(crossinline listener: TextToken.(M) -> T) = invoke { listener(thisAs("TextToken"), it) }
        inline fun <T> String.switch(crossinline listener: SwitchToken.(M) -> T) = invoke { listener(thisAs("SwitchToken"), it) }

        fun Token.raise(message: String): Nothing {
            throw IllegalArgumentException("$message (in '$substring')")
        }
    }

    internal companion object {
        class MetaGrammarState {
            val implicitNamedSymbols = mutableListOf<ImplicitSymbol>()
            val rules = mutableMapOf<String, Symbol>()
        }

        val META = grammar<Map<String,Symbol>,MetaGrammarState>("__meta__") {
            "escape".sequence {
                when (substring[1]) {
                    'u' -> substring.substring(2..5).toInt(8).toChar()
                    't' -> '\t'
                    'n' -> '\n'
                    'r' -> '\r'
                    else -> substring[1]    // ', -, \
                }
            }

            "char".junction {
                if (match.id == "escape") match.payload() else substring.single()
            }

            "switch".sequence {
                if (substring.length == 3) {    // .substring == "[-]"
                    AnyCharacter()
                } else {
                    val lowerBounds = MutableIntVector()
                    val upperBounds = MutableIntVector()

                    junctionAt(1).sequence {    // -> <switch body>
                        optionAt(1).ifPresent { // -> <up-to>
                            lowerBounds.push(Char.MIN_VALUE)
                            upperBounds.push(sequence()[1].payload<Char>())
                        }
                        starAt(2).ifPresent { // -> <single character/range>
                            for (j in junctions()) {
                                val payload: Any? = j.match.payload()
                                if (payload is Char) {
                                    lowerBounds.push(payload)
                                    upperBounds.push(payload)
                                } else {
                                    j.sequence {    // -> <range>
                                        lowerBounds.push(this[0].payload<Char>())
                                        upperBounds.push(this[2].payload<Char>())
                                    }
                                }
                            }
                        }
                        optionAt(3).ifPresent { // -> <at-least>
                            lowerBounds.push(sequence()[0].payload<Char>())
                            upperBounds.push(Char.MAX_VALUE)
                        }
                    }
                    lowerBounds to upperBounds
                }
            }

            "character".sequence {
                Character(this[1].payload())
            }

            "text".sequence {
                Text(this[1].substring)
            }

            "rule".sequence { mutableState ->
                val id = sequenceAt(0).substring
                val symbol = junctionAt(2) {    // -> symbol
                    if (ordinal() == 5) {   // -> Sequence.ID
                        raise("Delegation to another named symbol is forbidden")
                    }
                    val rhs = match.payload<Symbol>()
                    if (rhs is ImplicitSymbol) {    // Delegation to literal
                        // ...rhs can never be a named symbol
                        ImplicitSymbol(id).apply { reference = rhs }
                    } else {
                        rhs.apply { this.id = id }
                    }
                }

                mutableState.implicitNamedSymbols
                    .indexOfFirst { it.id == id }
                    .takeIf { it != -1 }
                    ?.let {
                        val reference = if (symbol is ImplicitSymbol) symbol.reference else symbol
                        mutableState.implicitNamedSymbols[it].reference = reference
                        mutableState.implicitNamedSymbols.removeAt(it)
                    }
                id to symbol
            }

            "sequence".multiple {
                val symbols = ArrayList<Symbol>(matches.size)
                matches.forEach { symbols.add(it.payload()) }
                Sequence(members = symbols)
            }

            "junction".sequence {
                val symbols = mutableListOf<Symbol>(this[0].payload())
                multipleAt(1) {
                    sequences().forEach { symbols.add(it[1].payload()) }
                }
                Junction(members = symbols)
            }

            "multiple".sequence {
                Multiple(this[0].payload())
            }

            "option".sequence {
                Option(this[0].payload())
            }

            "star".sequence {
                Star(this[0].payload())
            }

            "start".multiple { mutableState ->
                if (mutableState.implicitNamedSymbols.any()) {
                    raise("No definitions found for implicit symbols: ${mutableState.implicitNamedSymbols}")
                }
                mutableState.rules
            }.start()

            "skip".skip()

            "symbol".junction { mutableState ->
                when (ordinal()) {
                    0 -> {  // :: (<symbol>)
                        sequence {
                            this[1].payload()
                        }
                    }
                    5 -> {  // :: <id>
                        val id = match.substring
                        mutableState.rules.getOrElse(id) {
                            if (mutableState.implicitNamedSymbols.none { implicit -> implicit.id == id }) {
                                mutableState.implicitNamedSymbols += ImplicitSymbol(id)
                            }
                        }
                    }
                    else -> match.payload()
                }
            }
        }
    }
}
