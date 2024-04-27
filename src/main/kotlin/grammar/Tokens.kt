package grammar

@Suppress("UNCHECKED_CAST")
fun <T> Token.thisAs(tokenName: String): T {
    val expect = tokenName.dropLast("Token".length)
    val actual = (this as ContextFreeToken).origin::class.simpleName
    if (actual == expect) {
        return this as T
    }
    throw TypeCastException("Listener type ($expect) does not agree with type of symbol ($actual)")
}

@Suppress("UNCHECKED_CAST")
fun <T> Token.payload() = (this as ContextFreeToken).payload as T

operator fun <T> Token.invoke(block: Token.() -> T) = block(this)

inline fun <T : SingleChildToken, R> T.sequence(block: SequenceToken.() -> R) = block(sequence())
inline fun <T : SingleChildToken, R> T.junction(block: JunctionToken.() -> R) = block(junction())
inline fun <T : SingleChildToken, R> T.multiple(block: MultipleToken.() -> R) = block(multiple())
inline fun <T : SingleChildToken, R> T.option(block: OptionToken.() -> R) = block(option())
inline fun <T : SingleChildToken, R> T.star(block: StarToken.() -> R) = block(star())
inline fun <T : SingleChildToken, R> T.character(block: CharacterToken.() -> R) = block(character())
inline fun <T : SingleChildToken, R> T.text(block: TextToken.() -> R) = block(text())
inline fun <T : SingleChildToken, R> T.switch(block: SwitchToken.() -> R) = block(switch())

inline fun <R> SequenceToken.sequenceAt(index: Int, block: SequenceToken.() -> R) = block(sequenceAt(index))
inline fun <R> SequenceToken.junctionAt(index: Int, block: JunctionToken.() -> R) = block(junctionAt(index))
inline fun <R> SequenceToken.multipleAt(index: Int, block: MultipleToken.() -> R) = block(multipleAt(index))
inline fun <R> SequenceToken.optionAt(index: Int, block: OptionToken.() -> R) = block(optionAt(index))
inline fun <R> SequenceToken.starAt(index: Int, block: StarToken.() -> R) = block(starAt(index))
inline fun <R> SequenceToken.characterAt(index: Int, block: CharacterToken.() -> R) = block(characterAt(index))
inline fun <R> SequenceToken.textAt(index: Int, block: TextToken.() -> R) = block(textAt(index))
inline fun <R> SequenceToken.switchAt(index: Int, block: SwitchToken.() -> R) = block(switchAt(index))

sealed interface Token {
    val id: String
    val substring: String
}

sealed interface SingleChildToken : Token {
    val match: Token

    fun sequence(): SequenceToken
    fun junction(): JunctionToken
    fun multiple(): MultipleToken
    fun option(): OptionToken
    fun star(): StarToken
    fun character(): CharacterToken
    fun text(): TextToken
    fun switch(): SwitchToken
}

sealed interface MultiChildToken : Token {
    val matches: List<Token>

    fun sequences(): List<SequenceToken>
    fun junctions(): List<JunctionToken>
    fun multiples(): List<MultipleToken>
    fun options(): List<OptionToken>
    fun stars(): List<StarToken>
    fun characters(): List<CharacterToken>
    fun texts(): List<TextToken>
    fun switches(): List<SwitchToken>
}

sealed interface SequenceToken : MultiChildToken {
    fun sequenceAt(index: Int): SequenceToken
    fun junctionAt(index: Int): JunctionToken
    fun multipleAt(index: Int): MultipleToken
    fun optionAt(index: Int): OptionToken
    fun starAt(index: Int): StarToken
    fun characterAt(index: Int): CharacterToken
    fun textAt(index: Int): TextToken
    fun switchAt(index: Int): SwitchToken

    operator fun get(index: Int): Token
}

sealed interface JunctionToken : SingleChildToken, Numbered
sealed interface OptionToken : SingleChildToken, PossiblyEmpty
sealed interface MultipleToken : MultiChildToken
sealed interface StarToken : MultiChildToken, PossiblyEmpty

sealed interface CharacterToken : Token {
    val charValue: Char
}

sealed interface TextToken : Token {
    val stringValue: String
    val length: Int
}

sealed interface SwitchToken : Token, Numbered {
    val charValue: Char
}

internal class ContextFreeToken(
    var origin: Symbol,
    override val substring: String = "",
    val children: List<ContextFreeToken> = listOf(),
    private val ordinal: Int = 0
) : SequenceToken, JunctionToken, OptionToken, MultipleToken, StarToken, CharacterToken, TextToken, SwitchToken {
    override val id get() = origin.id
    var payload: Any? = null

    override val match get() = children[0]
    override val length get() = substring.length
    override val charValue get() = substring[0]
    override val stringValue get() = substring
    override val matches get() = children

    override fun ordinal() = ordinal
    override fun isPresent() = children.isNotEmpty()
    override fun isNotPresent() = children.isEmpty()

    override fun sequenceAt(index: Int) = getAs<Sequence,SequenceToken>(index)
    override fun junctionAt(index: Int) = getAs<Junction,JunctionToken>(index)
    override fun multipleAt(index: Int) = getAs<Multiple, MultipleToken>(index)
    override fun optionAt(index: Int) = getAs<Option,OptionToken>(index)
    override fun starAt(index: Int) = getAs<Star,StarToken>(index)
    override fun characterAt(index: Int) = getAs<Character,CharacterToken>(index)
    override fun textAt(index: Int) = getAs<Text,TextToken>(index)
    override fun switchAt(index: Int) = getAs<Switch,SwitchToken>(index)

    override fun get(index: Int) = children[index]

    override fun sequences() = getAllAs<Sequence,SequenceToken>()
    override fun junctions() = getAllAs<Junction,JunctionToken>()
    override fun multiples() = getAllAs<Multiple, MultipleToken>()
    override fun options() = getAllAs<Option,OptionToken>()
    override fun stars() = getAllAs<Star,StarToken>()
    override fun characters() = getAllAs<Character,CharacterToken>()
    override fun texts() = getAllAs<Text,TextToken>()
    override fun switches() = getAllAs<Switch,SwitchToken>()

    override fun sequence() = getSingleAs<Sequence,SequenceToken>()
    override fun junction() = getSingleAs<Junction,JunctionToken>()
    override fun multiple() = getSingleAs<Multiple, MultipleToken>()
    override fun option() = getSingleAs<Option,OptionToken>()
    override fun star() = getSingleAs<Star,StarToken>()
    override fun character() = getSingleAs<Character,CharacterToken>()
    override fun text() = getSingleAs<Text,TextToken>()
    override fun switch() = getSingleAs<Switch,SwitchToken>()

    fun <M> walk(listeners: Map<String, Token.(M) -> Any?>, mutableState: M) {
        children.forEach { it.walk(listeners, mutableState) } // Visit every node in tree
        payload = listeners[id]?.let {
             it(this, mutableState)
        } ?: when (origin) {
            is Sequence, is Multiple, is Star -> children.map { it.payload }
            is Junction, is Option -> children[0].payload
            else -> null    // Useful information for literals is found in .substring, not .payload
        }
    }

    private inline fun <reified S : Symbol, reified T : Token> getAs(index: Int): T {
        return try {
            children[index].origin as S
            children[index] as T
        } catch (e: IndexOutOfBoundsException) {
            throw IndexOutOfBoundsException("Match at index $index in calling SequenceToken does not exist")
        } catch (e: TypeCastException) {
            val s = S::class.simpleName
            throw TypeCastException("Match at index $index in calling SequenceToken is not derived from a $s")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified S : Symbol, reified T : Token> getAllAs(): List<T> {
        var failureLocation = 0
        return try {
            for (child in children) {
                child.origin as S
                ++failureLocation
            }
            children as List<T>
        } catch (e: TypeCastException) {
            throw TypeCastException("Match at index $failureLocation is not derived from a  ${S::class.simpleName}")
        }
    }

    private inline fun <reified S : Symbol, reified T : Token> getSingleAs(): T {
        return try {
            children[0].origin as S
            children[0] as T
        } catch (e: TypeCastException) {
            throw TypeCastException("Match at index 0 is not derived from a  ${S::class.simpleName}")
        }
    }

    companion object {
        // Since, by contract, no listener can have an ID beginning in an underscore, there is no need to cast to Token
        val NOTHING = ContextFreeToken(OriginMarker.NOTHING)
        val EMPTY = ContextFreeToken(OriginMarker.EMPTY)
    }
}

/**
all
how many?
any?
multiple
how many?
 */