package com.github.quantumweb.edgejsjetbrains.edge.highlighting

import com.github.quantumweb.edgejsjetbrains.edge.lexer.EdgeTokenTypes
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class EdgeSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = EdgeHighlightingLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return when (tokenType) {
            EdgeTokenTypes.COMMENT -> pack(EdgeTextAttributes.COMMENT)
            EdgeTokenTypes.ESCAPED_MUSTACHE -> pack(EdgeTextAttributes.ESCAPED_MUSTACHE)
            EdgeTokenTypes.MUSTACHE, EdgeTokenTypes.SAFE_MUSTACHE -> pack(EdgeTextAttributes.MUSTACHE)
            EdgeTokenTypes.MUSTACHE_DELIMITER -> pack(EdgeTextAttributes.MUSTACHE)
            EdgeTokenTypes.TAG -> pack(EdgeTextAttributes.DIRECTIVE)
            EdgeTokenTypes.TAG_PREAMBLE, EdgeTokenTypes.TAG_NAME, EdgeTokenTypes.TAG_SUFFIX -> pack(EdgeTextAttributes.DIRECTIVE)
            else -> emptyArray()
        }
    }
}
