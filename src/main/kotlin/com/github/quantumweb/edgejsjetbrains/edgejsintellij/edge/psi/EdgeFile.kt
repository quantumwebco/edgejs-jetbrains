package com.github.quantumweb.edgejsjetbrains.edge.psi

import com.github.quantumweb.edgejsjetbrains.edge.EdgeFileType
import com.github.quantumweb.edgejsjetbrains.edge.EdgeLanguage
import com.github.quantumweb.edgejsjetbrains.edge.references.EdgeTagReferenceFactory
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiReference

class EdgeFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, EdgeLanguage) {
    override fun getFileType() = EdgeFileType

    /**
     * Extends the default lookup with injected-fragment references.
     *
     * [super.findReferenceAt] already goes through [com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry],
     * which invokes [com.github.quantumweb.edgejsjetbrains.edge.references.EdgeReferenceContributor] and covers
     * TAG elements and HTML-tree elements that map back to Edge TAGs.
     *
     * The extra step here specifically handles the case where the caret is inside an injected
     * JS fragment (e.g. the path string inside `@include('...')`) and we want a file reference
     * back to the Edge template rather than whatever the JS provider resolves.
     */
    override fun findReferenceAt(offset: Int): PsiReference? {
        super.findReferenceAt(offset)?.let { return it }
        return findInjectedReferenceAt(offset)
    }

    override fun toString(): String = "Edge File"

    private fun findInjectedReferenceAt(offset: Int): PsiReference? {
        val manager = InjectedLanguageManager.getInstance(project)
        val injectedElement = manager.findInjectedElementAt(this, offset) ?: return null
        return EdgeTagReferenceFactory.createInjectedStringReferences(injectedElement).firstOrNull()
    }
}
