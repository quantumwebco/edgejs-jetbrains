package com.github.quantumweb.edgejsjetbrains.edge.lexer

/**
 * Finds the offset of the closing parenthesis that matches the opening paren at [openParenOffset]
 * within [text], scanning up to [limit] (exclusive).
 *
 * Correctly skips over:
 *  - single-quoted strings  ('...')
 *  - double-quoted strings  ("...")
 *  - template literals      (`...`)
 *  - line comments          (// ...)
 *  - block comments         (/* ... */)
 *
 * Returns the offset of the matching `)`, or `null` if it is not found before [limit].
 */
fun findMatchingClosingParen(text: CharSequence, openParenOffset: Int, limit: Int = text.length): Int? {
    var depth = 0
    var current = openParenOffset
    var quotedBy: Char? = null
    var insideTemplateString = false
    var insideLineComment = false
    var insideBlockComment = false

    while (current < limit) {
        val char = text[current]

        when {
            insideLineComment -> {
                if (char == '\n' || char == '\r') insideLineComment = false
            }

            insideBlockComment -> {
                if (char == '*' && current + 1 < limit && text[current + 1] == '/') {
                    insideBlockComment = false
                    current++
                }
            }

            quotedBy != null -> {
                if (char == '\\') current++
                else if (char == quotedBy) quotedBy = null
            }

            insideTemplateString -> {
                if (char == '\\') current++
                else if (char == '`') insideTemplateString = false
            }

            else -> when (char) {
                '\'', '"' -> quotedBy = char
                '`' -> insideTemplateString = true
                '/' -> if (current + 1 < limit) {
                    when (text[current + 1]) {
                        '/' -> { insideLineComment = true; current++ }
                        '*' -> { insideBlockComment = true; current++ }
                    }
                }
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return current
                }
            }
        }

        current++
    }

    return null
}
