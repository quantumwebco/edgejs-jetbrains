package com.github.quantumweb.edgejsjetbrains.edge

import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.lang.InjectableLanguage
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.psi.templateLanguages.TemplateLanguage

object EdgeLanguage : Language("Edge"), TemplateLanguage, InjectableLanguage {
    val defaultTemplateDataFileType: LanguageFileType
        get() = HtmlFileType.INSTANCE
}
