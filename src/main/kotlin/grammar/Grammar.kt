package grammar

import grammar.internal.AssignOnce
import grammar.internal.CharStream
import grammar.internal.MetaGrammar
import grammar.internal.Symbol


// TODO write code for when end of stream is reached

/**
 * @retun a context-free grammar with the given definition and specifications
 */
fun <T,M> grammar(formalGrammar: String, builder: Grammar<T,M>.BuilderContext.() -> Unit): Grammar<T,M> {
    return Grammar<T,M>().apply {
        rules = MetaGrammar.GRAMMAR.parse(formalGrammar, MetaGrammar.MutableState())
        builder(BuilderContext())
    }
}

/**
 * A context-free grammar used to parse complex expressions.
 */
class Grammar<R,M> internal constructor() {
    private val listeners = mutableMapOf<String, Token.(M) -> Any?>()
    internal var rules: Map<String, Symbol> by AssignOnce()
    private var startID: String by AssignOnce()
    private var skipID: String by AssignOnce()

    /**
     * Parses the input using the given mutable state.
     * @return the payload of the principle token
     */
    fun parse(input: String, mutableState: M): R {
        // Root token can be seen as base of parse tree
        val rootToken = rules.getValue(startID).match(CharStream(input), rules.getValue(skipID), mutableListOf())
        rootToken.walk(listeners, mutableState)
        return rootToken.payload()
    }

    /**
     * The scope wherein the start rule, skip rule, and listeners of a grammar are defined.
     */
    inner class BuilderContext internal constructor() {
        /**
         * Declares the rule with this ID to be the principle rule.
         */
        fun String.start() {
            try {
                startID = this
            } catch (e: ReassignmentException) {
                throw ReassignmentException("Start rule already defined")
            }
        }

        /**
         * Declares the rule with this ID to be skipped between match attempts.
         */
        fun String.skip() {
            try {
                skipID = this
            } catch (e: ReassignmentException) {
                throw ReassignmentException("Skip rule already defined")
            }
        }

        /**
         * Assigns the rule with this ID the given listener.
         * @return the rule ID
         */
        operator fun <T> String.invoke(listener: Token.(M) -> T): String {
            if (this !in rules) {
                throw NoSuchElementException("Rule '$this' is undefined")
            }
            if (this in listeners) {
                throw ReassignmentException("Listener for rule '$this' is already defined")
            }
            listeners[this] = listener
            return this
        }

        // TODO add fail-fast checks for proper Symbol in rules (.values)
        // TODO document
        fun <T> String.sequence(listener: SequenceToken.(M) -> T) = invoke { listener(thisAs("SequenceToken"), it) }


        fun <T> String.junction(listener: JunctionToken.(M) -> T) = invoke { listener(thisAs("JunctionToken"), it) }


        fun <T> String.multiple(listener: MultipleToken.(M) -> T) = invoke { listener(thisAs("MultipleToken"), it) }


        fun <T> String.option( listener: OptionToken.(M) -> T) = invoke { listener(thisAs("OptionToken"), it) }


        fun <T> String.star(listener: StarToken.(M) -> T) = invoke { listener(thisAs("StarToken"), it) }


        fun <T> String.character(listener: CharacterToken.(M) -> T) = invoke { listener(thisAs("CharacterToken"), it) }


        fun <T> String.text(listener: TextToken.(M) -> T) = invoke { listener(thisAs("TextToken"), it) }


        fun <T> String.switch(listener: SwitchToken.(M) -> T) = invoke { listener(thisAs("SwitchToken"), it) }

        /**
         * Throws an exception containing the error message and the current substring.
         * @throws ParseException
         */
        fun Token.raise(message: String): Nothing {
            throw ParseException("$message (in '$substring')")
        }
    }
}
