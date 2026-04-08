package com.github.quantumweb.edgejsjetbrains.edge.psi

import com.github.quantumweb.edgejsjetbrains.edge.EdgeLanguage
import com.github.quantumweb.edgejsjetbrains.edge.lexer.EdgeTokenTypes
import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutors
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.templateLanguages.ConfigurableTemplateLanguageFileViewProvider
import com.intellij.psi.templateLanguages.TemplateDataElementType
import com.intellij.psi.templateLanguages.TemplateDataModifications
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings
import com.intellij.psi.tree.IElementType
import com.intellij.openapi.util.TextRange

private val EDGE_TEMPLATE_DATA = object : TemplateDataElementType(
    "EDGE_TEMPLATE_DATA",
    EdgeLanguage,
    EdgeTokenTypes.CONTENT,
    EdgeTokenTypes.OUTER_ELEMENT_TYPE,
) {
    override fun collectTemplateModifications(
        sourceCode: CharSequence,
        baseLexer: com.intellij.lexer.Lexer,
    ): TemplateDataModifications {
        val modifications = TemplateDataModifications()
        baseLexer.start(sourceCode)

        while (baseLexer.tokenType != null) {
            val tokenRange = TextRange(baseLexer.tokenStart, baseLexer.tokenEnd)

            when (baseLexer.tokenType) {
                EdgeTokenTypes.CONTENT -> Unit
                EdgeTokenTypes.ESCAPED_MUSTACHE -> {
                    modifications.addOuterRange(TextRange.from(tokenRange.startOffset, 1))
                }
                else -> modifications.addOuterRange(tokenRange)
            }

            baseLexer.advance()
        }

        return modifications
    }
}

class EdgeFileViewProvider(
    manager: PsiManager,
    virtualFile: VirtualFile,
    eventSystemEnabled: Boolean,
    private val dataLanguage: Language = computeTemplateDataLanguage(manager, virtualFile),
) : MultiplePsiFilesPerDocumentFileViewProvider(manager, virtualFile, eventSystemEnabled),
    ConfigurableTemplateLanguageFileViewProvider {

    override fun createFile(lang: Language): PsiFile? {
        val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang) ?: return null

        return when (lang) {
            baseLanguage -> parserDefinition.createFile(this)
            templateDataLanguage -> {
                val file = parserDefinition.createFile(this)
                if (file is PsiFileImpl) {
                    file.contentElementType = EDGE_TEMPLATE_DATA
                }
                file
            }

            else -> null
        }
    }

    override fun getBaseLanguage(): Language = EdgeLanguage

    override fun getTemplateDataLanguage(): Language = dataLanguage

    override fun getLanguages(): Set<Language> = setOf(baseLanguage, templateDataLanguage)

    override fun cloneInner(fileCopy: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider {
        return EdgeFileViewProvider(manager, fileCopy, false, computeTemplateDataLanguage(manager, virtualFile))
    }

    override fun supportsIncrementalReparse(rootLanguage: Language): Boolean = false

    override fun getContentElementType(language: Language): IElementType? {
        return if (language == templateDataLanguage) EDGE_TEMPLATE_DATA else null
    }
}

internal fun computeTemplateDataLanguage(manager: PsiManager, virtualFile: VirtualFile): Language {
    val mappings = TemplateDataLanguageMappings.getInstance(manager.project)
    val configuredLanguage = mappings?.getMapping(virtualFile) ?: HtmlFileType.INSTANCE.language
    val substitutedLanguage = LanguageSubstitutors.getInstance().substituteLanguage(configuredLanguage, virtualFile, manager.project)

    return if (TemplateDataLanguageMappings.getTemplateableLanguages().contains(substitutedLanguage)) {
        substitutedLanguage
    } else {
        configuredLanguage
    }
}
