package com.github.quantumweb.edgejsjetbrains.edge.highlighting

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.fileTypes.EditorHighlighterProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class EdgeTemplateHighlighterProvider : EditorHighlighterProvider {
    override fun getEditorHighlighter(
        project: Project?,
        fileType: FileType,
        virtualFile: VirtualFile?,
        editorColorsScheme: EditorColorsScheme,
    ): EditorHighlighter {
        return EdgeTemplateHighlighter(project, virtualFile, editorColorsScheme)
    }
}
