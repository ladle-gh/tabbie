package grammar

import internal.withFirst

class Grammar<R, M> {
    private val rules = mutableMapOf<String, Symbol>()
    private val listeners = mutableMapOf<String, Token.(M) -> Any?>()
    private val defaultSymbols = mutableListOf<String>()
    private lateinit var startID: String
    private lateinit var skipID: String

    fun parse(input: String, mutableState: M): R {
        TODO()
    }

    inner class BuilderContext {
        internal fun definition(vararg rule: Pair<String, Symbol>) {
            rules.putAll(rule)
        }

        fun String.start() {
            if (::startID.isInitialized) {
                throw RedefinitionException("Start symbol already defined")
            }
            startID = this
        }

        fun String.skip() {
            if (::skipID.isInitialized) {
                throw RedefinitionException("Skip symbol already defined")
            }
            skipID = this
        }

        operator fun <T> String.invoke(listener: Token.(M) -> T): String {
            if (this !in rules) {
                throw IllegalCallerException("Rule '$this' is undefined")
            }
            if (this in listeners) {
                throw RedefinitionException("Listener already defined")
            }
            listeners[this] = listener
            return this
        }

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
}

internal class MetaGrammarState {
    val implicitNamedSymbols = mutableListOf<ImplicitSymbol>()  // .id == <id>
    val rules = mutableMapOf<String, Symbol>()
}

internal val metaGrammar = grammar<Map<String,Symbol>,MetaGrammarState>("__meta__") {
    definition(
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

    "rule".sequence {
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

        it.implicitNamedSymbols
            .partition { implicit -> implicit.id == id }
            .withFirst {  }
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

    "start".multiple {
        it.rules.forEach { (id, symbol) -> symbol.id = id }
    }.start()

    "skip".skip()

    "symbol".junction {
        when (ordinal()) {
            0 -> {  // :: (<symbol>)
                sequence {
                    this[1].payload()
                }
            }
            5 -> {  // :: <id>
                val id = match.substring
                it.rules.getOrElse(id) {
                    ImplicitSymbol(id).also { implicit -> it.implicitNamedSymbols += implicit }
                }
            }
            else -> match.payload()
        }
    }
}

fun <T,M> grammar(definition: String, builder: Grammar<T,M>.BuilderContext.() -> Unit): Grammar<T,M> {
    val result = Grammar<T,M>()
    MetaGrammar.start.match(CharStream(definition), MetaGrammar.skip)
    return result
}
/*
Sequence
/// Convenience
    // sequenceAt (flattened)
    junctionAt
    optionalAt
    anyAt   // (handled internaly as multiple(optional)
    characterAt
    textAt
    switchAt

/// Fail-fast
    junctions
    // sequences
    optionals
    anys
    characters  // (.string)
    text
    switches

    matches
Character
    value
String
    length
Option
/// Fail-fast
    junction
    ...

    ifPresent
    isPresent
    isNotPresent

    match   // can be EMPTY
Multiple
/// Fail-fast
    junctions
    ...

    matches // (.size)
Junction
/// Fail-fast
    junction
    ...

    ordinal

    match
Switch
    ordinal

    value

Token
/// Fail-fast
    asSequence
    asCharacter
    asText
    asSwitch
    asOptional
    asMultiple
    asJunction

    invoke (with)
 */