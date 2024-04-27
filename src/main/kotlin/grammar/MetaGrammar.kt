package grammar

internal object MetaGrammar {
    lateinit var symbol: Symbol
        private set

    val escape: Symbol = Sequence("escape",
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

    val char = Junction("char",
        escape,
        AnyCharacter()
    )

    val switch = Sequence("switch",
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

    val character = Sequence("character",
        Character.APOSTROPHE,
        char,
        Character.APOSTROPHE
    )

    val text = Sequence("text",
        Character.APOSTROPHE,
        Multiple(char),
        Character.APOSTROPHE
    )

    val rule = Sequence("rule",
        Sequence.ID,
        Character(':'),
        symbol,
        Character(';')
    )

    val sequence = Multiple("sequence", symbol)

    val junction = Sequence("junction",
        symbol,
        Multiple(
            Sequence(
                Character('|'),
                Multiple(symbol)
            )
        )
    )

    val multiple = Sequence("multiple",
        symbol,
        Character('+')
    )

    val option = Sequence("option",
        symbol,
        Character('?')
    )

    val star = Sequence("star",
        symbol,
        Character('*')
    )

    val start: Symbol = Multiple("start", rule)

    val skip: Symbol = Multiple("skip",
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
        symbol = Junction("symbol",
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
    }
}