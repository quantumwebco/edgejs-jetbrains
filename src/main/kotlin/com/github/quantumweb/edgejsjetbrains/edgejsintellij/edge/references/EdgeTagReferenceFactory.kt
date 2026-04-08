package com.github.quantumweb.edgejsjetbrains.edge.references

import com.intellij.codeInsight.highlighting.HighlightedReference
import com.intellij.openapi.project.Project
import com.github.quantumweb.edgejsjetbrains.edge.EdgeDirectiveSupport
import com.github.quantumweb.edgejsjetbrains.edge.lexer.EdgeTokenTypes
import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.util.text.StringUtil

object EdgeTagReferenceFactory {
    fun create(element: PsiElement): Array<PsiReference> {
        return createReferences(element, element)
    }

    fun createForHostElement(hostElement: PsiElement, tagElement: PsiElement): Array<PsiReference> {
        return createReferences(hostElement, tagElement)
    }

    private fun createReferences(hostElement: PsiElement, tagElement: PsiElement): Array<PsiReference> {
        val references = mutableListOf<PsiReference>()
        val text = tagElement.text
        val hostRange = hostElement.textRange
        val tagRange = tagElement.textRange

        EdgeDirectiveSupport.findViewDirectiveTarget(text)?.let { target ->
            toHostRelativeRange(hostRange, tagRange, target.range)?.let { hostRelativeRange ->
                references += EdgeFileReference(hostElement, hostRelativeRange, target.value) {
                    EdgeTemplateResolver.resolveTemplate(tagElement.project, target.value)
                }
            }
        }

        EdgeDirectiveSupport.componentTagRegex.find(text)?.let { match ->
            val directiveName = match.groupValues[2]
            if (directiveName !in EdgeDirectiveSupport.builtinComponentTags) {
                val targetRange = match.rangeOfGroup(2)
                toHostRelativeRange(hostRange, tagRange, targetRange)?.let { hostRelativeRange ->
                    references += EdgeFileReference(hostElement, hostRelativeRange, directiveName) {
                        EdgeTemplateResolver.resolveComponent(tagElement.project, directiveName)
                    }
                }
            }
        }

        return references.toTypedArray()
    }

    fun createInjectedStringReferences(element: PsiElement): Array<PsiReference> {
        return createInjectedStringReferences(findInjectedIncludeContext(element))
    }

    private fun PsiElement.parentsWithSelf(): Sequence<PsiElement> = generateSequence(this) { it.parent }

    private fun createInjectedStringReferences(context: InjectedIncludeContext?): Array<PsiReference> {
        if (context == null) return PsiReference.EMPTY_ARRAY

        return arrayOf(
            EdgeFileReference(context.referenceElement, context.referenceRange, context.lookupText) {
                EdgeTemplateResolver.resolveTemplate(context.referenceElement.project, context.lookupText)
            },
        )
    }

    private fun findInjectedIncludeContext(element: PsiElement): InjectedIncludeContext? {
        val injectionHost = InjectedLanguageManager.getInstance(element.project).getInjectionHost(element) ?: return null
        if (injectionHost.node?.elementType != EdgeTokenTypes.TAG) return null

        val target = EdgeDirectiveSupport.findIncludeDirectiveTarget(injectionHost.text) ?: return null
        val injectedRange = hostRangeToInjectedRange(element, injectionHost, target.range) ?: return null
        val referenceElement = element.parentsWithSelf()
            .takeWhile { it.containingFile == element.containingFile }
            .firstOrNull { it.textRange.contains(injectedRange) }
            ?: return null

        return InjectedIncludeContext(
            referenceElement = referenceElement,
            referenceRange = injectedRange.shiftLeft(referenceElement.textRange.startOffset),
            lookupText = target.value,
        )
    }

    private fun hostRangeToInjectedRange(
        element: PsiElement,
        injectionHost: PsiElement,
        hostRelativeRange: TextRange,
    ): TextRange? {
        val documentWindow = PsiDocumentManager.getInstance(element.project).getDocument(element.containingFile) as? DocumentWindow
            ?: return null

        val hostRange = hostRelativeRange.shiftRight(injectionHost.textRange.startOffset)
        if (hostRange.isEmpty) return null

        val injectedStart = documentWindow.hostToInjected(hostRange.startOffset)
        val injectedEnd = documentWindow.hostToInjected(hostRange.endOffset - 1)
        if (injectedStart < 0 || injectedEnd < injectedStart) return null

        return TextRange(injectedStart, injectedEnd + 1)
    }

    private fun toHostRelativeRange(
        hostRange: TextRange,
        tagRange: TextRange,
        tagRelativeRange: TextRange,
    ): TextRange? {
        val absoluteStart = tagRange.startOffset + tagRelativeRange.startOffset
        val absoluteEnd = tagRange.startOffset + tagRelativeRange.endOffset
        val intersection = hostRange.intersection(TextRange(absoluteStart, absoluteEnd)) ?: return null
        return TextRange(intersection.startOffset - hostRange.startOffset, intersection.endOffset - hostRange.startOffset)
    }

    private fun MatchResult.rangeOfGroup(index: Int): TextRange {
        val groupRange = groups[index]?.range ?: error("Missing regex group $index")
        return TextRange(groupRange.first, groupRange.last + 1)
    }

    private class EdgeFileReference(
        element: PsiElement,
        textRange: TextRange,
        private val lookupText: String,
        private val resolver: () -> PsiFile?,
    ) : PsiReferenceBase<PsiElement>(element, textRange, false), HighlightedReference {
        override fun resolve(): PsiElement? = resolver()

        override fun getCanonicalText(): String = lookupText
    }

    private data class InjectedIncludeContext(
        val referenceElement: PsiElement,
        val referenceRange: TextRange,
        val lookupText: String,
    )
}

object EdgeTemplateResolver {
    fun resolveTemplate(project: Project, rawName: String): PsiFile? {
        val normalizedName = normalizeTemplateName(rawName)
        if (normalizedName.isEmpty()) return null

        return findEdgeFiles(project)
            .mapNotNull { file ->
                scoreTemplateCandidate(file, project, normalizedName)?.let { score -> score to file }
            }
            .sortedByDescending { it.first }
            .firstOrNull()
            ?.second
            ?.let { PsiUtilCore.getPsiFile(project, it) }
    }

    fun resolveComponent(project: Project, rawName: String): PsiFile? {
        val normalizedName = rawName.trim().removePrefix("!")
        if (normalizedName.isEmpty()) return null

        return findEdgeFiles(project)
            .mapNotNull { file ->
                scoreComponentCandidate(file, project, normalizedName)?.let { score -> score to file }
            }
            .sortedByDescending { it.first }
            .firstOrNull()
            ?.second
            ?.let { PsiUtilCore.getPsiFile(project, it) }
    }

    private fun scoreTemplateCandidate(file: VirtualFile, project: Project, normalizedName: String): Int? {
        val candidateNames = templateNames(file)

        return when {
            candidateNames.contains(normalizedName) -> 100
            candidateNames.any { it.endsWith("/$normalizedName") } -> 50
            else -> null
        }
    }

    private fun scoreComponentCandidate(file: VirtualFile, project: Project, normalizedName: String): Int? {
        val componentName = componentTagName(file) ?: return null
        return if (componentName == normalizedName) 100 else null
    }

    private fun templateNames(file: VirtualFile): Set<String> {
        val normalizedPath = file.path.replace('\\', '/')
        val withoutExtension = normalizedPath.removeSuffix(".edge")
        val names = linkedSetOf(file.nameWithoutExtension)

        pathAfterViewsRoot(withoutExtension)?.let { viewRelative ->
            addTemplateName(names, viewRelative)
            if (viewRelative.endsWith("/index")) {
                addTemplateName(names, viewRelative.removeSuffix("/index"))
            }
        }

        return names
    }

    private fun addTemplateName(names: MutableSet<String>, name: String) {
        val normalized = name.trim('/')
        if (normalized.isBlank()) return
        names += normalized
        names += normalized.replace('/', '.')
    }

    private fun componentTagName(file: VirtualFile): String? {
        val viewRelative = pathAfterViewsRoot(file.path.replace('\\', '/').removeSuffix(".edge")) ?: return null
        if (!viewRelative.startsWith("components/")) return null

        var componentPath = viewRelative.removePrefix("components/")
        if (componentPath.endsWith("/index")) {
            componentPath = componentPath.removeSuffix("/index")
        }
        if (componentPath.isBlank()) return null

        return componentPath.split('/')
            .joinToString(".") { segment -> toCamelCase(segment) }
    }

    private fun toCamelCase(value: String): String {
        val words = value.split(Regex("[-_\\s]+"))
            .filter { it.isNotBlank() }
        if (words.isEmpty()) return value

        return buildString {
            append(words.first().lowercase())
            for (word in words.drop(1)) {
                append(StringUtil.capitalize(word.lowercase()))
            }
        }
    }

    private fun normalizeTemplateName(rawName: String): String {
        val trimmed = rawName.trim().removePrefix("'").removePrefix("\"").removeSuffix("'").removeSuffix("\"")
        return trimmed.replace('.', '/').replace("\\", "/")
    }

    private fun pathAfterViewsRoot(path: String): String? {
        val marker = "/resources/views/"
        val markerIndex = path.indexOf(marker)
        if (markerIndex >= 0) {
            return path.substring(markerIndex + marker.length).takeIf { it.isNotBlank() }
        }

        val relativeMarker = "resources/views/"
        val relativeIndex = path.indexOf(relativeMarker)
        if (relativeIndex >= 0) {
            return path.substring(relativeIndex + relativeMarker.length).takeIf { it.isNotBlank() }
        }

        return null
    }

    private fun findEdgeFiles(project: Project): Collection<VirtualFile> {
        return FilenameIndex.getAllFilesByExt(project, "edge", GlobalSearchScope.projectScope(project))
    }
}
