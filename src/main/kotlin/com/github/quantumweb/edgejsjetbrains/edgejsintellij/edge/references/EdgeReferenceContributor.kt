package com.github.quantumweb.edgejsjetbrains.edge.references

import com.github.quantumweb.edgejsjetbrains.edge.EdgeLanguage
import com.github.quantumweb.edgejsjetbrains.edge.lexer.EdgeTokenTypes
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext

class EdgeReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            psiElement(),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    if (element.node?.elementType == EdgeTokenTypes.TAG) {
                        return EdgeTagReferenceFactory.create(element)
                    }

                    EdgeTagReferenceFactory.createInjectedStringReferences(element).takeIf { it.isNotEmpty() }?.let {
                        return it
                    }

                    // The PSI tree walk below is only meaningful for elements from a non-Edge sub-tree
                    // (e.g. the HTML virtual tree inside an Edge template) whose text range happens to
                    // overlap an Edge TAG.  Pure Edge-language elements that are not TAGs will never
                    // have a TAG ancestor in the flat Edge PSI tree, so bail out early.
                    if (element.language == EdgeLanguage) return PsiReference.EMPTY_ARRAY

                    val edgeElement = element.containingFile.viewProvider
                        .getPsi(EdgeLanguage)
                        ?.findElementAt(element.textRange.startOffset)
                        ?.parentsWithSelf()
                        ?.firstOrNull { it.node?.elementType == EdgeTokenTypes.TAG }
                        ?: return PsiReference.EMPTY_ARRAY

                    return EdgeTagReferenceFactory.createForHostElement(element, edgeElement)
                }

                private fun PsiElement.parentsWithSelf(): Sequence<PsiElement> = generateSequence(this) { it.parent }
            },
        )
    }
}
