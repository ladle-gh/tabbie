package grammar

import grammar.internal.*
import org.junit.jupiter.api.Test

// TODO add toString() to all grammar classes

class GrammarTest {
    @Test
    fun grammar() {
        val x = grammar<Map<String, Symbol>,MetaGrammar.MutableState>(formalGrammar = """
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
        """.trimIndent()) {
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
        }.parse("rule: \"e\"", MetaGrammar.MutableState())
        println(x)
    }
}