package com.github.quantumweb.edgejsjetbrains.edge.lexer

import com.github.quantumweb.edgejsjetbrains.edge.EdgeLanguage
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.OuterLanguageElementType
import com.intellij.psi.tree.TokenSet

class EdgeTokenType(debugName: String) : IElementType(debugName, EdgeLanguage)

object EdgeTokenTypes {
    @JvmField
    val FILE = IFileElementType("EDGE_FILE", EdgeLanguage)

    @JvmField
    val CONTENT = EdgeTokenType("CONTENT")

    @JvmField
    val OUTER_ELEMENT_TYPE = OuterLanguageElementType("EDGE_FRAGMENT", EdgeLanguage)

    @JvmField
    val COMMENT = EdgeTokenType("COMMENT")

    @JvmField
    val ESCAPED_MUSTACHE = EdgeTokenType("ESCAPED_MUSTACHE")

    @JvmField
    val MUSTACHE = EdgeTokenType("MUSTACHE")

    @JvmField
    val SAFE_MUSTACHE = EdgeTokenType("SAFE_MUSTACHE")

    @JvmField
    val TAG = EdgeTokenType("TAG")

    @JvmField
    val MUSTACHE_DELIMITER = EdgeTokenType("MUSTACHE_DELIMITER")

    @JvmField
    val MUSTACHE_CONTENT = EdgeTokenType("MUSTACHE_CONTENT")

    @JvmField
    val TAG_PREAMBLE = EdgeTokenType("TAG_PREAMBLE")

    @JvmField
    val TAG_NAME = EdgeTokenType("TAG_NAME")

    @JvmField
    val TAG_ARGUMENTS = EdgeTokenType("TAG_ARGUMENTS")

    @JvmField
    val TAG_SUFFIX = EdgeTokenType("TAG_SUFFIX")

    @JvmField
    val COMMENTS = TokenSet.create(COMMENT)
}
