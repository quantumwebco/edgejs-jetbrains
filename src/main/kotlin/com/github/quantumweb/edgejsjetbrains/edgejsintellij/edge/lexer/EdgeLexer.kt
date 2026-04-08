package com.github.quantumweb.edgejsjetbrains.edge.lexer

import com.github.quantumweb.edgejsjetbrains.edge.EdgeDirectiveSupport
import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

class EdgeLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var startOffset: Int = 0
    private var endOffset: Int = 0
    private var tokenStart: Int = 0
    private var tokenEnd: Int = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        tokenStart = startOffset
        tokenEnd = startOffset
        tokenType = null
        locateToken(startOffset)
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun advance() {
        locateToken(tokenEnd)
    }

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = endOffset

    private fun locateToken(offset: Int) {
        if (offset >= endOffset) {
            tokenStart = endOffset
            tokenEnd = endOffset
            tokenType = null
            return
        }

        tokenStart = offset

        readDirective(offset)?.let {
            tokenEnd = it
            tokenType = EdgeTokenTypes.TAG
            return
        }

        when {
            matches(offset, EDGE_COMMENT_OPEN) -> {
                tokenEnd = readDelimited(offset, EDGE_COMMENT_OPEN, EDGE_COMMENT_CLOSE)
                tokenType = EdgeTokenTypes.COMMENT
            }

            matches(offset, ESCAPED_MUSTACHE_OPEN) -> {
                tokenEnd = readDelimited(offset, ESCAPED_MUSTACHE_OPEN, MUSTACHE_CLOSE)
                tokenType = EdgeTokenTypes.ESCAPED_MUSTACHE
            }

            matches(offset, SAFE_MUSTACHE_OPEN) -> {
                tokenEnd = readDelimited(offset, SAFE_MUSTACHE_OPEN, SAFE_MUSTACHE_CLOSE)
                tokenType = EdgeTokenTypes.SAFE_MUSTACHE
            }

            matches(offset, MUSTACHE_OPEN) -> {
                tokenEnd = readDelimited(offset, MUSTACHE_OPEN, MUSTACHE_CLOSE)
                tokenType = EdgeTokenTypes.MUSTACHE
            }

            else -> {
                tokenEnd = readContent(offset)
                tokenType = EdgeTokenTypes.CONTENT
            }
        }
    }

    private fun readContent(offset: Int): Int {
        var current = offset
        while (current < endOffset) {
            if (startsFragment(current)) {
                break
            }

            if (readDirective(current) != null) {
                break
            }

            current++
        }

        return current.coerceAtLeast(offset + 1)
    }

    private fun startsFragment(offset: Int): Boolean {
        return matches(offset, EDGE_COMMENT_OPEN) ||
            matches(offset, ESCAPED_MUSTACHE_OPEN) ||
            matches(offset, SAFE_MUSTACHE_OPEN) ||
            matches(offset, MUSTACHE_OPEN)
    }

    private fun readDirective(offset: Int): Int? {
        if (!isTagStart(offset)) {
            return null
        }

        val lineEnd = findLineEnd(offset)
        val lineText = buffer.subSequence(offset, lineEnd).toString()

        if (EdgeDirectiveSupport.nonSeekableTagRegex.matches(lineText)) {
            return if (lineText.endsWith("~")) consumeSingleLineBreak(lineEnd) else lineEnd
        }

        val beginMatch = EdgeDirectiveSupport.functionTagBeginRegex.find(lineText)
        if (beginMatch == null || beginMatch.range.first != 0) {
            return null
        }

        val openParenOffset = offset + beginMatch.value.lastIndexOf('(')
        val closeParenOffset = findMatchingClosingParen(buffer, openParenOffset, endOffset) ?: return null
        var directiveEnd = closeParenOffset + 1

        if (directiveEnd < endOffset && buffer[directiveEnd] == '~') {
            directiveEnd++
            return consumeSingleLineBreak(directiveEnd)
        }

        return directiveEnd
    }

    private fun isTagStart(offset: Int): Boolean {
        if (buffer[offset] != '@') {
            return false
        }

        if (offset == startOffset) {
            return true
        }

        val previous = buffer[offset - 1]
        return previous == '\n' || previous == '\r' || previous == ' ' || previous == '\t'
    }

    private fun readDelimited(offset: Int, open: String, close: String): Int {
        var current = offset + open.length
        val lastStart = endOffset - close.length

        while (current <= lastStart) {
            if (matches(current, close)) {
                return current + close.length
            }
            current++
        }

        return endOffset
    }

    private fun matches(offset: Int, expected: String): Boolean {
        if (offset + expected.length > endOffset) {
            return false
        }

        for (index in expected.indices) {
            if (buffer[offset + index] != expected[index]) {
                return false
            }
        }

        return true
    }

    private fun findLineEnd(offset: Int): Int {
        var current = offset
        while (current < endOffset && buffer[current] != '\n' && buffer[current] != '\r') {
            current++
        }
        return current
    }

    private fun consumeSingleLineBreak(offset: Int): Int {
        var current = offset
        if (current < endOffset && buffer[current] == '\r') {
            current++
        }
        if (current < endOffset && buffer[current] == '\n') {
            current++
        }
        return current
    }

    companion object {
        private const val EDGE_COMMENT_OPEN = "{{--"
        private const val EDGE_COMMENT_CLOSE = "--}}"
        private const val ESCAPED_MUSTACHE_OPEN = "@{{"
        private const val MUSTACHE_OPEN = "{{"
        private const val MUSTACHE_CLOSE = "}}"
        private const val SAFE_MUSTACHE_OPEN = "{{{"
        private const val SAFE_MUSTACHE_CLOSE = "}}}"
    }
}
