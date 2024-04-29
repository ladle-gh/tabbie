@file:Suppress("UNUSED", "MemberVisibilityCanBePrivate")
package grammar

import grammar.internal.*
import java.io.*

// TODO test
// TODO move to seperate repository

/**
 * Provides the primary functionality of the API.
 * @return a context-free grammar with the given definition and specifications
 */
@Suppress("UNCHECKED_CAST")
fun <R,M : Grammar.MutableState> grammar(
    formalGrammar: String,
    cachePath: String = "",
    builder: Grammar<R,M>.BuilderContext.() -> Unit
): Grammar<R,M> {
    val needsCache = if (cachePath.isNotEmpty()) {
        val file = File(cachePath)
        if (file.exists()) {
            FileInputStream(file).use { fileStream ->
                ObjectInputStream(fileStream).use {
                    try {
                        return it.readObject() as Grammar<R, M>
                    } catch (_: TypeCastException) {}
                }
            }
        }
        true
    } else {
        false
    }
    return Grammar<R,M>().apply {
        rules = MetaGrammar.grammar.parse(formalGrammar, MetaGrammar.MutableState())
        builder(BuilderContext())
        if (needsCache) {
            FileOutputStream(cachePath).use { fileStream ->
                ObjectOutputStream(fileStream).use {
                    it.writeUnshared(this)
                    it.flush()
                }
            }
        }
    }
}

/**
 * A context-free grammar used to parse complex expressions in a string.
 * @param R the type of the
 */
class Grammar<R,M : Grammar.MutableState> internal constructor() : Serializable {
    private val listeners = mutableMapOf<String, Token.(M) -> Any?>()
    internal var rules: Map<String, Symbol> by AssignOnce()
    private var startID: String by AssignOnce()
    private var skipID: String by AssignOnce()

    /**
     * Parses the input while modifying the given mutable state.
     * @return the payload of the principle token (the base of the parse tree)
     * @throws ParseException there is nothing to parse or there is an unknown symbol
     * @see parseOrNull
     */
    fun parse(input: String, mutableState: M) = parse(StringCharStream(input), mutableState)

    /**
     * Parses the input while modifying the given mutable state.
     * @return the payload of the principle token (the base of the parse tree),
     * or null if there is nothing to parse or there is an unknown symbol
     * @see parse
     */
    fun parseOrNull(input: String, mutableState: M) = parseOrNull(StringCharStream(input), mutableState)

    /**
     * Parses the input present within the given text file while modifying the given mutable state.
     * @return the payload of the principle token (the base of the parse tree)
     * @throws ParseException there is nothing to parse or there is an unknown symbol
     * @see parseFileOrNull
     */
    fun parseFile(inputPath: String, mutableState: M) = parse(FileCharStream(inputPath), mutableState)

    /**
     * Parses the input present within the given text file while modifying the given mutable state.
     * @return the payload of the principle token (the base of the parse tree),
     * or null if there is nothing to parse or there is an unknown symbol
     * @see parseFile
     */
    fun parseFileOrNull(inputPath: String, mutableState: M) = parseOrNull(FileCharStream(inputPath), mutableState)


    /**
     * Contains mutable properties which are manipulated by each listener invokation.
     * Useful for collecting information regardless of location in the parse tree.
     * Grammars that do not need such information should create an instance of the base class.
     */
    open class MutableState {
        internal var position = -1  // TODO implement functionality
    }

    /**
     * The scope wherein the start rule, skip rule, and listeners of a grammar are defined.
     */
    inner class BuilderContext internal constructor() {
        private val listenerDefinition = ListenerDefinition<Any?>()

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
        ): ListenerDefinition<P> {
            val id = invoke {
                if ((this as ContextFreeToken).origin.reference()::class != S::class) {
                    throw TokenMismatchException("Listener type does not agree with type of rule '$this'")
                }
                listener(this as T, it)
            }
            val symbol = rules.getValue(id).reference()
            if (symbol !is S) {
                throw TokenMismatchException("Type of rule '$id' described by listener (${S::class.simpleName}) " +
                        "does not agree with actual type (${symbol::class.simpleName})")
            }
            return ListenerDefinition()
        }
    }

    private fun parse(inputStream: CharStream, mutableState: M): R {
        inputStream.use {
            val skip = rules.getValue(skipID)
            val recursions = mutableListOf<String>()
            skip.consume(inputStream, recursions)
            val rootToken = rules.getValue(startID).match(inputStream, skip, recursions) // Base of parse tree
            if (rootToken === ContextFreeToken.NOTHING) {
                throw ParseException("Start symbol not found in input", 0)
            }
            skip.consume(inputStream, recursions)
            if (inputStream.hasNext()) {
                throw ParseException("Unknown symbol in input", inputStream.position)
            }
            rootToken.walk(listeners, mutableState)
            return rootToken.payload()
        }
    }

    private fun parseOrNull(input: CharStream, mutableState: M): R? {
        return try {
            parse(input, mutableState)
        } catch (e: ParseException) {
            null
        }
    }

    class ListenerDefinition<P> internal constructor()

    private companion object {
        @Serial val serialVersionUID = 1L
    }
}
