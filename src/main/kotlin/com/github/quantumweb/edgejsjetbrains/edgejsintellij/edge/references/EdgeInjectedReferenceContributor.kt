package com.github.quantumweb.edgejsjetbrains.edge.references

import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext

class EdgeInjectedReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            psiElement().with(object : PatternCondition<PsiElement>("stringLiteralLike") {
                override fun accepts(element: PsiElement, context: ProcessingContext?): Boolean {
                    return element.textLength >= 2 && isQuoted(element.text)
                }
            }),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    return EdgeTagReferenceFactory.createInjectedStringReferences(element)
                }
            },
        )
    }

    private fun isQuoted(text: String): Boolean {
        val first = text.firstOrNull() ?: return false
        val last = text.lastOrNull() ?: return false
        return (first == '\'' && last == '\'') || (first == '"' && last == '"')
    }
}
