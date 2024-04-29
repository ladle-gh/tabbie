package grammar.internal

import grammar.*

/*
    ID: [a-zA-Z] [a-zA-Z0-9_]*;
    DIGIT: [0-9];
    QUOTE: "${'"'}";

    symbol: "(" symbol ")" | junction | sequence | multiple | option | ID | switch | character | text;
    escape: "\" ([...] | ("u" DIGIT DIGIT DIGIT DIGIT));
    char: escape | [-];

    switch: "[" ((("-" char)? (char | (char "-" char))* (char "-")?) | "-") "]";
    character: QUOTE char QUOTE;
    text: QUOTE char+ QUOTE;

    rule: ID ":" symbol ";"

    sequence: symbol+;
    junction: symbol ("|" symbol)+;
    multiple: symbol "+";
    option: symbol "?";
    star: symbol "*";

    start: rule+;
    skip: ([\u0000-\u0009\u000B-\u0001F]+ | "/*"  [-]* "*/" | "//" [-\u0009\u000B-])+;
 */

internal object MetaGrammar {
    class MutableState : Grammar.MutableState() {
        val implicitNamedSymbols = mutableListOf<ImplicitSymbol>()
        val rules = mutableMapOf<String, Symbol>()
    }

    val grammar: Grammar<Map<String,Symbol>,MutableState>

    val characterEscapes = mapOf(
        't' to '\t',
        'n' to '\n',
        'r' to '\r',
        '"' to '"',
        '-' to '-',
        '\\' to '\\'
    )

    private val symbol = ImplicitSymbol("symbol")

    private val escape = Sequence("escape",
        Character('\\'),
        Junction(
            Switch.including('t', 'n', 'r', '-', '\'', '\\'),
            Sequence(
                Character('u'),
                Switch.DIGIT,
                Switch.DIGIT,
                Switch.DIGIT,
                Switch.DIGIT,
            )
        )
    )

    private val char = Junction("char",
        escape,
        AnyCharacter()
    )

    private val switch = Sequence("switch",
        Character('['),
        Junction(
            Sequence(
                Option(   // up-to
                    Sequence(
                        Character.DASH,
                        char
                    )
                ),
                Star(
                    Junction(
                        char,       // single character
                        Sequence(   // range
                            char,
                            Character.DASH,
                            char
                        ),
                    )
                ),
                Option(   // at-least
                    Sequence(
                        char,
                        Character.DASH
                    )
                ),
            ),
            Character.DASH  // catch-all
        ),
        Character(']')
    )

    private val character = Sequence("character",
        Character.QUOTE,
        char,
        Character.QUOTE
    )

    private val text = Sequence("text",
        Character.QUOTE,
        Multiple(char),
        Character.QUOTE
    )

    private val rule = Sequence("rule",
        Sequence.ID,
        Character(':'),
        symbol,
        Character(';')
    )

    private val sequence = Multiple("sequence", symbol)

    private val junction = Sequence("junction",
        symbol,
        Multiple(
            Sequence(
                Character('|'),
                symbol
            )
        )
    )

    private val multiple = Sequence("multiple",
        symbol,
        Character('+')
    )

    private val option = Sequence("option",
        symbol,
        Character('?')
    )

    private val star = Sequence("star",
        symbol,
        Character('*')
    )

    private val start = Multiple("start", rule)

    private val skip = Multiple("skip",
        Junction(
            Multiple(Switch(vectorOf('\u0000', '\u000B'), vectorOf('\u0009', '\u001F'))),   // whitespace
            Sequence(   // inline comment
                Text("/*"),
                Star(AnyCharacter()),
                Text("*/")
            ),
            Sequence(
                // line comment
                Text("//"),
                Star(Switch.excluding('\n')),
            )
        )
    )

    init {
        symbol.reference = Junction("symbol",
            Sequence(
                Character('('),
                symbol,
                Character(')')
            ),
            junction,
            sequence,
            multiple,
            option,
            Sequence.ID,
            switch,
            character,
            text
        )

        grammar = Grammar<Map<String, Symbol>, MutableState>().apply {
            rules = mapOf(
                "symbol" to symbol,         "escape" to escape,
                "char" to char,             "switch" to switch,
                "character" to character,   "text" to text,
                "rule" to rule,             "sequence" to sequence,
                "junction" to junction,     "multiple" to multiple,
                "option" to option,         "star" to star,
                "start" to start,           "skip" to skip
            )

            BuilderContext().apply {
                "escape".sequence {
                    when (val c = substring[1]) {
                        'u' -> substring.substring(2..5).toInt(8).toChar()
                        else -> characterEscapes[c]
                    }
                }

                "char".junction {
                    if (match.id == "escape") match.payload() else substring.single()
                }

                // TODO implicits
                "switch".sequence {
                    if (substring.length == 3) {    // .substring == "[-]"
                        AnyCharacter()
                    } else {
                        val lowerBounds = MutableIntVector()
                        val upperBounds = MutableIntVector()

                        junctionAt(1).sequence {    // -> <switch body>
                            optionAt(1).ifPresent { // -> <up-to>
                                lowerBounds += Char.MIN_VALUE
                                upperBounds += sequence()[1].payload<Char>()
                            }
                            starAt(2).ifPresent { // -> <single character/range>
                                for (j in junctions()) {
                                    val payload: Any? = j.match.payload()
                                    if (payload is Char) {
                                        lowerBounds += payload
                                        upperBounds += payload
                                    } else {
                                        j.sequence {    // -> <range>
                                            lowerBounds += this[0].payload<Char>()
                                            upperBounds += this[2].payload<Char>()
                                        }
                                    }
                                }
                            }
                            optionAt(3).ifPresent { // -> <at-least>
                                lowerBounds += sequence()[0].payload<Char>()
                                upperBounds += Char.MAX_VALUE
                            }
                        }
                        lowerBounds to upperBounds
                    }
                }

                // TODO implicits
                "character".sequence {
                    Character(this[1].payload())
                }

                // TODO implicits
                "text".sequence {
                    Text(this[1].substring)
                }

                "rule".sequence { mutableState ->
                    val id = sequenceAt(0).substring
                    val symbol = junctionAt(2) {    // -> symbol
                        if (ordinal() == 5) {   // -> Sequence.ID
                            raise("Delegation to another named symbol is forbidden", mutableState)
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
                        raise("No definitions found for implicit symbols: ${mutableState.implicitNamedSymbols}", mutableState)
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
}