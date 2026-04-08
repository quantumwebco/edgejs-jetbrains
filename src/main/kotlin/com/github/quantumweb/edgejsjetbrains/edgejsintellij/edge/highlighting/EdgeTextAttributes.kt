package com.github.quantumweb.edgejsjetbrains.edge.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

object EdgeTextAttributes {
    @JvmField
    val COMMENT: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "EDGE.COMMENT",
        DefaultLanguageHighlighterColors.BLOCK_COMMENT,
    )

    @JvmField
    val ESCAPED_MUSTACHE: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "EDGE.ESCAPED_MUSTACHE",
        DefaultLanguageHighlighterColors.LINE_COMMENT,
    )

    @JvmField
    val MUSTACHE: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "EDGE.MUSTACHE",
        DefaultLanguageHighlighterColors.BRACES,
    )

    @JvmField
    val DIRECTIVE: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "EDGE.DIRECTIVE",
        DefaultLanguageHighlighterColors.KEYWORD,
    )
}
