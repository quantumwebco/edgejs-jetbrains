package com.github.quantumweb.edgejsjetbrains.edge

import com.intellij.ide.highlighter.XmlLikeFileType
import com.intellij.openapi.fileTypes.TemplateLanguageFileType
import javax.swing.Icon

object EdgeFileType : XmlLikeFileType(EdgeLanguage), TemplateLanguageFileType {
    override fun getName(): String = "Edge"

    override fun getDescription(): String = "AdonisJS Edge template"

    override fun getDefaultExtension(): String = "edge"

    override fun getIcon(): Icon? = null
}
