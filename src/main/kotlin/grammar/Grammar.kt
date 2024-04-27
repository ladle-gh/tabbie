@file:Suppress("UNUSED")
package grammar

import grammar.internal.*

/**
 * Provides the primary functionality of the API.
 * @return a context-free grammar with the given definition and specifications
 */
fun <T,M> grammar(formalGrammar: String, builder: Grammar<T,M>.BuilderContext.() -> Unit): Grammar<T,M> {
    return Grammar<T,M>().apply {
        rules = MetaGrammar.GRAMMAR.parse(formalGrammar, MetaGrammar.MutableState())
        builder(BuilderContext())
    }
}

/**
 * A context-free grammar used to parse complex expressions in a string.
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
        val rootToken = rules.getValue(startID).startMatch(CharStream(input), rules.getValue(skipID))
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
        operator fun <P> String.invoke(listener: Token.(M) -> P): String {
            if (this !in rules) {
                throw NoSuchElementException("Rule '$this' is undefined")
            }
            if (this in listeners) {
                throw ReassignmentException("Listener for rule '$this' is already defined")
            }
            listeners[this] = listener
            return this
        }

        /**
         * Assigns the rule with this ID the given listener.
         * Asserts that the given rule is delegated to a sequence of tokens.
         * @throws TokenMismatchException the assertion fails
         */
        fun <P> String.sequence(listener: SequenceToken.(M) -> P) = listenerOf<Sequence,_,_>(listener)

        /**
         * Assigns the rule with this ID the given listener.
         * Asserts that the given rule is delegated to a rule defined using the '|' operator.
         * @throws TokenMismatchException the assertion fails
         */
        fun <P> String.junction(listener: JunctionToken.(M) -> P) =  listenerOf<Junction,_,_>(listener)

        /**
         * Assigns the rule with this ID the given listener.
         * Asserts that the given rule is delegated to a rule defined using the '' operator.
         * @throws TokenMismatchException the assertion fails
         */
        fun <P> String.multiple(listener: MultipleToken.(M) -> P) = listenerOf<Multiple,_,_>(listener)

        /**
         * Assigns the rule with this ID the given listener.
         * Asserts that the given rule is delegated to a rule defined using the '?' operator.
         * @throws TokenMismatchException the assertion fails
         */
        fun <P> String.option( listener: OptionToken.(M) -> P) = listenerOf<Option,_,_>(listener)

        /**
         * Assigns the rule with this ID the given listener.
         * Asserts that the given rule is delegated to a rule defined using the '*' operator.
         * @throws TokenMismatchException the assertion fails
         */
        fun <P> String.star(listener: StarToken.(M) -> P) = listenerOf<Star,_,_>(listener)

        /**
         * Assigns the rule with this ID the given listener.
         * Asserts that the given rule is delegated to a character literal.
         * @throws TokenMismatchException the assertion fails
         */
        fun <P> String.character(listener: CharacterToken.(M) -> P) = listenerOf<Character,_,_>(listener)

        /**
         * Assigns the rule with this ID the given listener.
         * Asserts that the given rule is delegated to a text literal.
         * @throws TokenMismatchException the assertion fails
         */
        fun <P> String.text(listener: TextToken.(M) -> P) = listenerOf<Text,_,_>(listener)

        /**
         * Assigns the rule with this ID the given listener.
         * Asserts that the given rule is delegated to a switch literal.
         * @throws TokenMismatchException the assertion fails
         */
        fun <P> String.switch(listener: SwitchToken.(M) -> P) = listenerOf<Switch,_,_>(listener)

        /**
         * Throws an exception containing the error message and the current substring.
         * @throws ParseException
         */
        fun Token.raise(message: String): Nothing {
            throw ParseException("$message (in '$substring')")
        }

        private inline fun <reified S : Symbol,reified T : Token,P> String.listenerOf(
            crossinline listener: T.(M) -> P
        ): String {
            val id = invoke {
                if ((this as ContextFreeToken).origin::class != S::class) {
                    throw TypeCastException("Listener type does not agree with type of rule '$this'")
                }
                listener(this as T, it)
            }
            if (rules.getValue(id) !is S) {
                throw TokenMismatchException("Type of rule '$id' described by listener does not agree with actual type")
            }
            return id
        }
    }
}
