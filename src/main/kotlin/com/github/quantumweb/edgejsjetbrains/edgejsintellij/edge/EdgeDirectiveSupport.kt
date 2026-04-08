package com.github.quantumweb.edgejsjetbrains.edge

import com.intellij.openapi.util.TextRange

internal data class EdgeDirectiveTarget(
    val value: String,
    val range: TextRange,
)

internal object EdgeDirectiveSupport {
    // Mirrors edge-vscode-main/syntaxes/edge.tmLanguage.json.
    val nonSeekableTagRegex = Regex("^(\\s*)((@{1,2})(!)?([a-zA-Z._]+))(~)?$")
    val functionTagBeginRegex = Regex("^(\\s*)((@{1,2})(!)?([a-zA-Z._]+)(\\s{0,2}))(\\()")
    val componentTagRegex = Regex(
        """^\s*@(!?)([a-zA-Z._]+)\s*\(""",
    )

    private val viewDirectiveRegex = Regex(
        """^\s*@(!?component|include|includeIf|layout)\s*\(\s*(['\"])([^'\"]+)\2""",
    )
    private val includeDirectiveRegex = Regex(
        """^\s*@include\s*\(\s*(['\"])([^'\"]+)\1""",
    )

    val builtinComponentTags = setOf(
        "assign",
        "can",
        "component",
        "dd",
        "debugger",
        "dump",
        "each",
        "else",
        "elseif",
        "entryPointScripts",
        "entryPointStyles",
        "eval",
        "if",
        "include",
        "includeIf",
        "inject",
        "layout",
        "let",
        "newError",
        "pushOnceTo",
        "pushTo",
        "set",
        "slot",
        "stack",
        "svg",
        "unless",
        "vite",
    )

    fun findViewDirectiveTarget(text: String): EdgeDirectiveTarget? = findTarget(text, viewDirectiveRegex, 3)

    fun findIncludeDirectiveTarget(text: String): EdgeDirectiveTarget? = findTarget(text, includeDirectiveRegex, 2)

    private fun findTarget(text: String, regex: Regex, groupIndex: Int): EdgeDirectiveTarget? {
        val match = regex.find(text) ?: return null
        val groupRange = match.groups[groupIndex]?.range ?: return null
        return EdgeDirectiveTarget(
            value = text.substring(groupRange.first, groupRange.last + 1),
            range = TextRange(groupRange.first, groupRange.last + 1),
        )
    }
}
