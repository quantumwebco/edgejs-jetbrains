package com.github.quantumweb.edgejsjetbrains.edge.highlighting

import com.github.quantumweb.edgejsjetbrains.edge.EdgeLanguage
import com.github.quantumweb.edgejsjetbrains.edge.lexer.EdgeTokenTypes
import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.lang.Language
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.util.LayerDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings

class EdgeTemplateHighlighter(
    project: Project?,
    virtualFile: VirtualFile?,
    colors: EditorColorsScheme,
) : LayeredLexerEditorHighlighter(EdgeSyntaxHighlighter(), colors) {

    init {
        val dataFileType = resolveTemplateDataFileType(project, virtualFile)
        val contentHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(dataFileType, project, virtualFile)
            ?: SyntaxHighlighterFactory.getSyntaxHighlighter(PlainTextFileType.INSTANCE, project, virtualFile)
            ?: error("Plain text syntax highlighter is unavailable")
        registerLayer(EdgeTokenTypes.CONTENT, LayerDescriptor(contentHighlighter, ""))

        val expressionHighlighter = resolveExpressionHighlighter(project, virtualFile)
        if (expressionHighlighter != null) {
            registerLayer(EdgeTokenTypes.MUSTACHE_CONTENT, LayerDescriptor(expressionHighlighter, ""))
            registerLayer(EdgeTokenTypes.TAG_ARGUMENTS, LayerDescriptor(expressionHighlighter, ""))
        }
    }

    private fun resolveTemplateDataFileType(project: Project?, virtualFile: VirtualFile?): FileType {
        if (project == null || virtualFile == null) {
            return EdgeLanguage.defaultTemplateDataFileType
        }

        val mappedLanguage = TemplateDataLanguageMappings.getInstance(project).getMapping(virtualFile)
        return mappedLanguage?.associatedFileType ?: HtmlFileType.INSTANCE
    }

    private fun resolveExpressionHighlighter(project: Project?, virtualFile: VirtualFile?) =
        findExpressionLanguage()
            ?.let { SyntaxHighlighterFactory.getSyntaxHighlighter(it, project, virtualFile) }
            ?: FileTypeManager.getInstance()
                .getFileTypeByExtension("js")
                .takeUnless { it == PlainTextFileType.INSTANCE }
                ?.let { SyntaxHighlighterFactory.getSyntaxHighlighter(it, project, virtualFile) }

    private fun findExpressionLanguage(): Language? {
        return Language.findLanguageByID("JavaScript")
            ?: Language.findLanguageByID("TypeScript")
            ?: Language.findLanguageByID("ECMAScript 6")
    }
}
