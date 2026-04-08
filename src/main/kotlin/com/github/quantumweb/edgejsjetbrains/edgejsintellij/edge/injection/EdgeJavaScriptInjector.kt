package com.github.quantumweb.edgejsjetbrains.edge.injection

import com.github.quantumweb.edgejsjetbrains.edge.EdgeLanguage
import com.github.quantumweb.edgejsjetbrains.edge.lexer.EdgeTokenTypes
import com.github.quantumweb.edgejsjetbrains.edge.lexer.findMatchingClosingParen
import com.github.quantumweb.edgejsjetbrains.edge.psi.EdgeInjectionHostLeaf
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost

class EdgeJavaScriptInjector : MultiHostInjector {
    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        if (context.language != EdgeLanguage || context !is PsiLanguageInjectionHost) return

        val injectionLanguage = findInjectionLanguage() ?: return
        when (context.node?.elementType) {
            EdgeTokenTypes.MUSTACHE -> injectRanges(registrar, injectionLanguage, context, listOfNotNull(innerRange(context.text, 2, 2)))
            EdgeTokenTypes.SAFE_MUSTACHE -> injectRanges(registrar, injectionLanguage, context, listOfNotNull(innerRange(context.text, 3, 3)))
            EdgeTokenTypes.TAG -> injectDirectiveArguments(registrar, injectionLanguage, context)
        }
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> = listOf(EdgeInjectionHostLeaf::class.java)

    private fun findInjectionLanguage(): Language? {
        return Language.findLanguageByID("JavaScript")
            ?: Language.findLanguageByID("TypeScript")
            ?: Language.findLanguageByID("ECMAScript 6")
    }

    private fun innerRange(text: String, prefixLength: Int, suffixLength: Int): TextRange? {
        val start = prefixLength.coerceAtMost(text.length)
        val end = (text.length - suffixLength).coerceAtLeast(start)
        return if (start < end) TextRange(start, end) else null
    }

    private fun directiveArgumentRanges(text: String): List<TextRange> {
        val openParen = text.indexOf('(')
        if (openParen < 0) return emptyList()

        val closeParen = findMatchingClosingParen(text, openParen) ?: return emptyList()
        if (openParen + 1 >= closeParen) return emptyList()

        return listOf(TextRange(openParen + 1, closeParen))
    }

    private fun injectRanges(
        registrar: MultiHostRegistrar,
        language: Language,
        host: PsiLanguageInjectionHost,
        ranges: List<TextRange>,
    ) {
        if (ranges.isEmpty()) return

        registrar.startInjecting(language)
        for (range in ranges) {
            registrar.addPlace(null, null, host, range)
        }
        registrar.doneInjecting()
    }

    private fun injectDirectiveArguments(
        registrar: MultiHostRegistrar,
        language: Language,
        host: PsiLanguageInjectionHost,
    ) {
        val ranges = directiveArgumentRanges(host.text)
        if (ranges.isEmpty()) return

        registrar.startInjecting(language)
        for ((index, range) in ranges.withIndex()) {
            val prefix = if (index == 0) "edgeDirective(" else null
            val suffix = if (index == ranges.lastIndex) ")" else null
            registrar.addPlace(prefix, suffix, host, range)
        }
        registrar.doneInjecting()
    }
}
