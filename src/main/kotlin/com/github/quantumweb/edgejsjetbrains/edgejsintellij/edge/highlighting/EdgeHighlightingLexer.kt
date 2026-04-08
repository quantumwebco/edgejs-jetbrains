package com.github.quantumweb.edgejsjetbrains.edge.highlighting

import com.github.quantumweb.edgejsjetbrains.edge.EdgeDirectiveSupport
import com.github.quantumweb.edgejsjetbrains.edge.lexer.EdgeLexer
import com.github.quantumweb.edgejsjetbrains.edge.lexer.EdgeTokenTypes
import com.github.quantumweb.edgejsjetbrains.edge.lexer.findMatchingClosingParen
import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

class EdgeHighlightingLexer : LexerBase() {
    private val delegate = EdgeLexer()
    private var tokenStart: Int = 0
    private var tokenEnd: Int = 0
    private var tokenType: IElementType? = null
    private var pendingTokens: ArrayDeque<Token> = ArrayDeque()

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        pendingTokens.clear()
        delegate.start(buffer, startOffset, endOffset, initialState)
        locateToken()
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun advance() {
        if (pendingTokens.isNotEmpty()) {
            emit(pendingTokens.removeFirst())
            return
        }

        delegate.advance()
        locateToken()
    }

    override fun getBufferSequence(): CharSequence = delegate.bufferSequence

    override fun getBufferEnd(): Int = delegate.bufferEnd

    private fun locateToken() {
        val delegateType = delegate.tokenType
        if (delegateType == null) {
            tokenStart = delegate.bufferEnd
            tokenEnd = delegate.bufferEnd
            tokenType = null
            return
        }

        val splitTokens = when (delegateType) {
            EdgeTokenTypes.MUSTACHE -> splitMustache(delegate.tokenStart, delegate.tokenEnd, 2, 2)
            EdgeTokenTypes.SAFE_MUSTACHE -> splitMustache(delegate.tokenStart, delegate.tokenEnd, 3, 3)
            EdgeTokenTypes.TAG -> splitTag(delegate.bufferSequence, delegate.tokenStart, delegate.tokenEnd)
            else -> listOf(Token(delegate.tokenStart, delegate.tokenEnd, delegateType))
        }

        pendingTokens.clear()
        pendingTokens.addAll(splitTokens)
        emit(pendingTokens.removeFirst())
    }

    private fun splitMustache(start: Int, end: Int, openLength: Int, closeLength: Int): List<Token> {
        val tokens = mutableListOf<Token>()
        val contentStart = start + openLength
        val contentEnd = (end - closeLength).coerceAtLeast(contentStart)

        tokens += Token(start, contentStart, EdgeTokenTypes.MUSTACHE_DELIMITER)
        if (contentStart < contentEnd) {
            tokens += Token(contentStart, contentEnd, EdgeTokenTypes.MUSTACHE_CONTENT)
        }
        if (contentEnd < end) {
            tokens += Token(contentEnd, end, EdgeTokenTypes.MUSTACHE_DELIMITER)
        }
        return tokens
    }

    private fun splitTag(buffer: CharSequence, start: Int, end: Int): List<Token> {
        val text = buffer.subSequence(start, end).toString()
        val tokens = mutableListOf<Token>()

        EdgeDirectiveSupport.nonSeekableTagRegex.find(text)?.takeIf { it.range.first == 0 }?.let { match ->
            val fullTagStart = start + match.rangeOfGroup(2).first
            val fullTagEnd = start + match.rangeOfGroup(2).last + 1
            val tagNameStart = start + match.rangeOfGroup(5).first
            val tagNameEnd = start + match.rangeOfGroup(5).last + 1

            addTagNameTokens(tokens, fullTagStart, tagNameStart, tagNameEnd, fullTagEnd)
            if (fullTagEnd < end) {
                tokens += Token(fullTagEnd, end, EdgeTokenTypes.TAG_SUFFIX)
            }
            return tokens
        }

        EdgeDirectiveSupport.functionTagBeginRegex.find(text)?.takeIf { it.range.first == 0 }?.let { match ->
            val fullTagStart = start + match.rangeOfGroup(2).first
            val fullTagEnd = start + match.rangeOfGroup(2).last + 1
            val tagNameStart = start + match.rangeOfGroup(5).first
            val tagNameEnd = start + match.rangeOfGroup(5).last + 1
            val openParenStart = start + match.rangeOfGroup(7).first
            val closeParenStart = findMatchingClosingParen(buffer, openParenStart, end)
                ?: (end.coerceAtLeast(openParenStart + 1) - 1)

            addTagNameTokens(tokens, fullTagStart, tagNameStart, tagNameEnd, fullTagEnd)
            tokens += Token(openParenStart, openParenStart + 1, EdgeTokenTypes.TAG_PREAMBLE)
            if (openParenStart + 1 < closeParenStart) {
                tokens += Token(openParenStart + 1, closeParenStart, EdgeTokenTypes.TAG_ARGUMENTS)
            }
            tokens += Token(closeParenStart, end, EdgeTokenTypes.TAG_SUFFIX)
            return tokens
        }

        return listOf(Token(start, end, EdgeTokenTypes.TAG))
    }

    private fun addTagNameTokens(
        tokens: MutableList<Token>,
        fullTagStart: Int,
        tagNameStart: Int,
        tagNameEnd: Int,
        fullTagEnd: Int,
    ) {
        if (fullTagStart < tagNameStart) {
            tokens += Token(fullTagStart, tagNameStart, EdgeTokenTypes.TAG_PREAMBLE)
        }
        tokens += Token(tagNameStart, tagNameEnd, EdgeTokenTypes.TAG_NAME)
        if (tagNameEnd < fullTagEnd) {
            tokens += Token(tagNameEnd, fullTagEnd, EdgeTokenTypes.TAG_PREAMBLE)
        }
    }

    private fun emit(token: Token) {
        tokenStart = token.start
        tokenEnd = token.end
        tokenType = token.type
    }

    private fun MatchResult.rangeOfGroup(index: Int): IntRange {
        return groups[index]?.range ?: error("Missing regex group $index")
    }

    private data class Token(
        val start: Int,
        val end: Int,
        val type: IElementType,
    )
}
